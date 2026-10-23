import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/**
 * Zero-dependency CI Test Shard Manifest Guard & Runner (JDK 21 single-file source execution).
 *
 * Usage:
 *   java scripts/CiTestShards.java --verify
 *   java scripts/CiTestShards.java --run --shard 1 [extra-gradle-args...]
 *   java scripts/CiTestShards.java --run --shard 2 [extra-gradle-args...]
 */
public final class CiTestShards {

    private static final int SHARD_COUNT = 2;
    private static final Path DEFAULT_TEST_ROOT = Path.of("app", "src", "test");
    private static final Path DEFAULT_MANIFEST = Path.of("scripts", "ci-test-shards.txt");
    private static final Path DEFAULT_XML_DIR = Path.of("app", "build", "test-results", "testDebugUnitTest");

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)");
    private static final Pattern CLASS_DECL_PATTERN =
            Pattern.compile("(?m)^\\s*(?:public\\s+|internal\\s+|final\\s+|open\\s+)*class\\s+([A-Za-z_][A-Za-z0-9_]*)\\b");
    private static final Pattern TEST_ANNOTATION_PATTERN =
            Pattern.compile("@(?:org\\.junit\\.)?Test\\b");

    record DiscoveredTestClass(String fqcn, Path sourceFile, int testMethodCount) {}

    record ManifestValidationResult(
            Map<Integer, List<DiscoveredTestClass>> shardAssignments,
            int totalClasses,
            int totalTestMethods) {}

    static final class ManifestValidationException extends RuntimeException {
        ManifestValidationException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        try {
            runMain(args);
        } catch (ManifestValidationException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("CI shard runner unexpected error: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runMain(String[] args) throws Exception {
        List<String> argList = List.of(args);
        if (argList.isEmpty() || argList.contains("--help") || argList.contains("-h")) {
            printUsage();
            return;
        }

        runBuiltInGuardSelfChecks();

        Path repoRoot = Path.of("").toAbsolutePath().normalize();
        Path testRoot = repoRoot.resolve(DEFAULT_TEST_ROOT);
        Path manifestFile = repoRoot.resolve(DEFAULT_MANIFEST);

        if (argList.contains("--verify")) {
            ManifestValidationResult verified = verifyManifest(testRoot, manifestFile);
            printVerificationSummary(verified);
            return;
        }

        if (argList.contains("--run")) {
            int shardIdx = argList.indexOf("--shard");
            if (shardIdx < 0 || shardIdx + 1 >= argList.size()) {
                fail("Missing `--shard <1.." + SHARD_COUNT + ">` argument for `--run`.");
            }
            int shard = parseShardNumber(argList.get(shardIdx + 1));
            List<String> extraGradleArgs = new ArrayList<>();
            for (int i = 0; i < argList.size(); i++) {
                if (i == shardIdx || i == shardIdx + 1 || "--run".equals(argList.get(i))) {
                    continue;
                }
                extraGradleArgs.add(argList.get(i));
            }

            ManifestValidationResult verified = verifyManifest(testRoot, manifestFile);
            printVerificationSummary(verified);
            runShard(repoRoot, shard, verified.shardAssignments().get(shard), extraGradleArgs);
            return;
        }

        fail("Unknown arguments: " + String.join(" ", argList));
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java scripts/CiTestShards.java --verify");
        System.out.println("  java scripts/CiTestShards.java --run --shard <1|2> [extra-gradle-args...]");
    }

    static ManifestValidationResult verifyManifest(Path testRoot, Path manifestFile) throws IOException {
        if (!Files.isDirectory(testRoot)) {
            fail("Unit test source directory does not exist: " + testRoot);
        }
        if (!Files.isRegularFile(manifestFile)) {
            fail("CI shard manifest file does not exist: " + manifestFile);
        }

        Map<String, DiscoveredTestClass> discovered = discoverTestClasses(testRoot);
        List<String> lines = Files.readAllLines(manifestFile, StandardCharsets.UTF_8);
        return validateParsedManifest(discovered, lines, manifestFile.toString());
    }

    static ManifestValidationResult validateParsedManifest(
            Map<String, DiscoveredTestClass> discovered,
            List<String> manifestLines,
            String manifestSourceLabel) {

        Map<Integer, List<String>> rawShards = new TreeMap<>();
        for (int s = 1; s <= SHARD_COUNT; s++) {
            rawShards.put(s, new ArrayList<>());
        }

        Map<String, Integer> firstSeenLine = new LinkedHashMap<>();
        Map<String, Integer> firstSeenShard = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (int idx = 0; idx < manifestLines.size(); idx++) {
            int lineNo = idx + 1;
            String raw = manifestLines.get(idx).trim();
            if (raw.isEmpty() || raw.startsWith("#")) {
                continue;
            }
            int colon = raw.indexOf(':');
            if (colon <= 0 || colon == raw.length() - 1) {
                errors.add(manifestSourceLabel + ":" + lineNo + " malformed entry `" + raw + "` (expected `<shard>:<fqcn>`).");
                continue;
            }
            String shardPart = raw.substring(0, colon).trim();
            String fqcn = raw.substring(colon + 1).trim();

            int shard;
            try {
                shard = Integer.parseInt(shardPart);
            } catch (NumberFormatException e) {
                errors.add(manifestSourceLabel + ":" + lineNo + " invalid shard number `" + shardPart + "`.");
                continue;
            }
            if (shard < 1 || shard > SHARD_COUNT) {
                errors.add(manifestSourceLabel + ":" + lineNo + " shard `" + shard + "` out of range 1.." + SHARD_COUNT + ".");
                continue;
            }

            if (firstSeenLine.containsKey(fqcn)) {
                errors.add("Duplicate test class `" + fqcn + "` at " + manifestSourceLabel + ":" + lineNo
                        + " (already assigned to shard " + firstSeenShard.get(fqcn)
                        + " at line " + firstSeenLine.get(fqcn) + ").");
                continue;
            }
            firstSeenLine.put(fqcn, lineNo);
            firstSeenShard.put(fqcn, shard);
            rawShards.get(shard).add(fqcn);
        }

        for (String discoveredClass : discovered.keySet()) {
            if (!firstSeenShard.containsKey(discoveredClass)) {
                DiscoveredTestClass info = discovered.get(discoveredClass);
                errors.add("Missing/orphan @Test class not assigned to any CI shard: `"
                        + discoveredClass + "` (" + info.sourceFile() + ", " + info.testMethodCount() + " @Test).");
            }
        }

        for (Map.Entry<String, Integer> entry : firstSeenShard.entrySet()) {
            if (!discovered.containsKey(entry.getKey())) {
                errors.add("Stale manifest entry `" + entry.getKey() + "` in shard " + entry.getValue()
                        + " (class does not exist under app/src/test or contains zero @Test methods).");
            }
        }

        for (int s = 1; s <= SHARD_COUNT; s++) {
            if (rawShards.get(s).isEmpty()) {
                errors.add("Shard " + s + " is empty; every shard 1.." + SHARD_COUNT + " must contain at least one @Test class.");
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder("CI test shard manifest verification FAILED:\n");
            for (String err : errors) {
                sb.append("  - ").append(err).append('\n');
            }
            fail(sb.toString().trim());
        }

        Map<Integer, List<DiscoveredTestClass>> resolved = new TreeMap<>();
        int totalMethods = 0;
        for (int s = 1; s <= SHARD_COUNT; s++) {
            List<DiscoveredTestClass> list = new ArrayList<>();
            for (String fqcn : rawShards.get(s)) {
                DiscoveredTestClass info = discovered.get(fqcn);
                list.add(info);
                totalMethods += info.testMethodCount();
            }
            resolved.put(s, Collections.unmodifiableList(list));
        }

        return new ManifestValidationResult(Collections.unmodifiableMap(resolved), discovered.size(), totalMethods);
    }

    static Map<String, DiscoveredTestClass> discoverTestClasses(Path testRoot) throws IOException {
        List<Path> sourceFiles;
        try (Stream<Path> walk = Files.walk(testRoot)) {
            sourceFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".kt") || name.endsWith(".java");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        Map<String, DiscoveredTestClass> discovered = new LinkedHashMap<>();
        for (Path file : sourceFiles) {
            String rawSource = Files.readString(file, StandardCharsets.UTF_8);
            String stripped = stripCommentsAndStrings(rawSource);

            Matcher testMatcher = TEST_ANNOTATION_PATTERN.matcher(stripped);
            if (!testMatcher.find()) {
                continue;
            }

            String pkg = "";
            Matcher pkgMatcher = PACKAGE_PATTERN.matcher(stripped);
            if (pkgMatcher.find()) {
                pkg = pkgMatcher.group(1);
            }

            List<ClassSpan> classSpans = findTopLevelClasses(stripped);
            if (classSpans.isEmpty()) {
                String fallbackName = file.getFileName().toString().replaceFirst("\\.(kt|java)$", "");
                String fqcn = pkg.isEmpty() ? fallbackName : pkg + "." + fallbackName;
                int count = countMatches(TEST_ANNOTATION_PATTERN, stripped);
                discovered.put(fqcn, new DiscoveredTestClass(fqcn, file, count));
                continue;
            }

            for (ClassSpan span : classSpans) {
                String body = stripped.substring(span.startOffset, span.endOffset);
                int count = countMatches(TEST_ANNOTATION_PATTERN, body);
                if (count > 0) {
                    String fqcn = pkg.isEmpty() ? span.className : pkg + "." + span.className;
                    discovered.put(fqcn, new DiscoveredTestClass(fqcn, file, count));
                }
            }
        }
        return discovered;
    }

    private record ClassSpan(String className, int startOffset, int endOffset) {}

    private static List<ClassSpan> findTopLevelClasses(String stripped) {
        Matcher m = CLASS_DECL_PATTERN.matcher(stripped);
        List<int[]> headers = new ArrayList<>();
        List<String> names = new ArrayList<>();

        while (m.find()) {
            int braceDepthBefore = braceDepthAt(stripped, m.start());
            if (braceDepthBefore == 0) {
                headers.add(new int[] {m.start()});
                names.add(m.group(1));
            }
        }
        List<ClassSpan> spans = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int start = headers.get(i)[0];
            int end = (i + 1 < headers.size()) ? headers.get(i + 1)[0] : stripped.length();
            spans.add(new ClassSpan(names.get(i), start, end));
        }
        return spans;
    }

    private static int braceDepthAt(String text, int limit) {
        int depth = 0;
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && depth > 0) depth--;
        }
        return depth;
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    /**
     * Strips line comments, block comments, triple-quoted raw strings, and normal string literals
     * while preserving newlines so line/brace structure stays intact.
     */
    static String stripCommentsAndStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int n = src.length();
        int i = 0;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                i += 2;
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') out.append('\n');
                    i++;
                }
                i += 2;
            } else if (c == '"' && i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                out.append("\"\"");
                i += 3;
                while (i + 2 < n && !(src.charAt(i) == '"' && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"')) {
                    if (src.charAt(i) == '\n') out.append('\n');
                    i++;
                }
                i += 3;
            } else if (c == '"') {
                out.append("\"\"");
                i++;
                while (i < n && src.charAt(i) != '"') {
                    if (src.charAt(i) == '\\' && i + 1 < n) {
                        i += 2;
                        continue;
                    }
                    if (src.charAt(i) == '\n') out.append('\n');
                    i++;
                }
                i++;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void printVerificationSummary(ManifestValidationResult verified) {
        System.out.println("==================================================================");
        System.out.println("CI Test Shard Manifest Verified (Zero-Orphan / Zero-Duplicate)");
        System.out.println("Total discovered @Test classes: " + verified.totalClasses()
                + " (" + verified.totalTestMethods() + " @Test methods)");
        for (Map.Entry<Integer, List<DiscoveredTestClass>> entry : verified.shardAssignments().entrySet()) {
            int shard = entry.getKey();
            List<DiscoveredTestClass> classes = entry.getValue();
            int shardMethods = classes.stream().mapToInt(DiscoveredTestClass::testMethodCount).sum();
            System.out.println("  Shard " + shard + "/" + SHARD_COUNT + ": "
                    + classes.size() + " classes, " + shardMethods + " @Test methods");
            for (DiscoveredTestClass c : classes) {
                System.out.println("    - " + c.fqcn() + " (" + c.testMethodCount() + " @Test)");
            }
        }
        System.out.println("==================================================================");
    }

    private static void runShard(
            Path repoRoot,
            int shard,
            List<DiscoveredTestClass> shardClasses,
            List<String> extraGradleArgs) throws Exception {

        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
        String gradleExecutable = isWindows
                ? repoRoot.resolve("gradlew.bat").toString()
                : repoRoot.resolve("gradlew").toString();

        List<String> command = new ArrayList<>();
        command.add(gradleExecutable);
        for (String arg : extraGradleArgs) {
            if (!arg.startsWith("-")) {
                command.add(arg);
            }
        }
        command.add(":app:testDebugUnitTest");
        for (DiscoveredTestClass c : shardClasses) {
            command.add("--tests");
            command.add(c.fqcn());
        }
        command.add("--console=plain");
        for (String arg : extraGradleArgs) {
            if (arg.startsWith("-")) {
                command.add(arg);
            }
        }

        System.out.println("[CI Shard " + shard + "/" + SHARD_COUNT + "] Executing: " + String.join(" ", command));
        long startedNs = System.nanoTime();

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoRoot.toFile());
        pb.inheritIO();
        Process proc = pb.start();
        int exitCode = proc.waitFor();
        double wallSeconds = (System.nanoTime() - startedNs) / 1_000_000_000.0;

        if (exitCode != 0) {
            fail("Gradle test execution for shard " + shard + "/" + SHARD_COUNT + " failed with exit code " + exitCode + ".");
        }

        verifyAndSummarizeJunitXml(repoRoot.resolve(DEFAULT_XML_DIR), shard, shardClasses, wallSeconds);
    }

    private static void verifyAndSummarizeJunitXml(
            Path xmlDir,
            int shard,
            List<DiscoveredTestClass> shardClasses,
            double wallSeconds) throws Exception {

        int expectedTotalMethods = shardClasses.stream().mapToInt(DiscoveredTestClass::testMethodCount).sum();
        int actualTotalExecuted = 0;
        StringBuilder md = new StringBuilder();
        md.append("### Unit Tests — Shard ").append(shard).append("/").append(SHARD_COUNT).append("\n\n");
        md.append("| Test Class | Executed Tests | Suite Time (s) | Status |\n");
        md.append("| :--- | ---: | ---: | :--- |\n");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        for (DiscoveredTestClass expected : shardClasses) {
            File xmlFile = xmlDir.resolve("TEST-" + expected.fqcn() + ".xml").toFile();
            if (!xmlFile.isFile()) {
                fail("Expected JUnit XML report missing after shard run: " + xmlFile);
            }
            Element suite = dbf.newDocumentBuilder().parse(xmlFile).getDocumentElement();
            int tests = Integer.parseInt(suite.getAttribute("tests"));
            int failures = Integer.parseInt(suite.getAttribute("failures"));
            int errors = Integer.parseInt(suite.getAttribute("errors"));
            String timeStr = suite.getAttribute("time");

            if (failures > 0 || errors > 0) {
                fail("JUnit XML reported failures/errors for " + expected.fqcn());
            }
            if (tests != expected.testMethodCount()) {
                fail("Executed test count mismatch for " + expected.fqcn()
                        + ": expected " + expected.testMethodCount() + " @Test, XML reported " + tests + ".");
            }
            actualTotalExecuted += tests;
            md.append("| `").append(expected.fqcn()).append("` | ")
                    .append(tests).append(" | ")
                    .append(timeStr).append("s | PASS |\n");
        }

        md.append(String.format(
                Locale.US,
                "\n**Total**: %d classes, %d/%d tests executed, Gradle wall-clock: **%.2fs**\n\n",
                shardClasses.size(),
                actualTotalExecuted,
                expectedTotalMethods,
                wallSeconds));

        System.out.printf(
                Locale.US,
                "[CI Shard %d/%d] Verified JUnit XML: %d classes, %d/%d tests executed in %.2fs.%n",
                shard,
                SHARD_COUNT,
                shardClasses.size(),
                actualTotalExecuted,
                expectedTotalMethods,
                wallSeconds);

        String summaryEnv = System.getenv("GITHUB_STEP_SUMMARY");
        if (summaryEnv != null && !summaryEnv.isBlank()) {
            Files.writeString(
                    Path.of(summaryEnv),
                    md.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        }
    }

    /**
     * Fast in-memory self-checks guaranteeing that orphan, duplicate, and stale-entry guards fail closed.
     */
    private static void runBuiltInGuardSelfChecks() {
        Map<String, DiscoveredTestClass> sample = new LinkedHashMap<>();
        sample.put("com.example.AlphaTest", new DiscoveredTestClass("com.example.AlphaTest", Path.of("AlphaTest.kt"), 3));
        sample.put("com.example.BetaTest", new DiscoveredTestClass("com.example.BetaTest", Path.of("BetaTest.kt"), 2));

        // 1. Valid split succeeds
        ManifestValidationResult ok = validateParsedManifest(
                sample,
                List.of("1:com.example.AlphaTest", "2:com.example.BetaTest"),
                "self-check");
        if (ok.totalClasses() != 2 || ok.totalTestMethods() != 5) {
            throw new IllegalStateException("Self-check valid split failed.");
        }

        // 2. Orphan class must fail
        assertGuardRejects(sample, List.of("1:com.example.AlphaTest", "2:com.example.StaleTest"), "orphan");

        // 3. Duplicate class across shards must fail
        assertGuardRejects(
                sample,
                List.of("1:com.example.AlphaTest", "2:com.example.AlphaTest", "2:com.example.BetaTest"),
                "duplicate");

        // 4. Empty shard must fail
        assertGuardRejects(
                sample,
                List.of("1:com.example.AlphaTest", "1:com.example.BetaTest"),
                "empty shard");
    }

    private static void assertGuardRejects(
            Map<String, DiscoveredTestClass> discovered,
            List<String> lines,
            String caseName) {
        boolean rejected = false;
        try {
            validateParsedManifest(discovered, lines, "self-check-" + caseName);
        } catch (ManifestValidationException expected) {
            rejected = true;
        }
        if (!rejected) {
            throw new IllegalStateException("Manifest guard self-check failed to reject " + caseName + " case.");
        }
    }

    private static int parseShardNumber(String raw) {
        try {
            int s = Integer.parseInt(raw.trim());
            if (s < 1 || s > SHARD_COUNT) {
                fail("Shard must be between 1 and " + SHARD_COUNT + ", got: " + s);
            }
            return s;
        } catch (NumberFormatException e) {
            fail("Invalid shard number: " + raw);
            return -1;
        }
    }

    private static void fail(String message) {
        throw new ManifestValidationException(message);
    }
}
