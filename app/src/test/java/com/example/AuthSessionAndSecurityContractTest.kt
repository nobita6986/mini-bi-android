package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ActorContractParser
import com.example.data.BackendApiException
import com.example.data.BackendFailureKind
import com.example.data.BackendHttpClient
import com.example.data.CookieCipher
import com.example.data.EncryptedPersistentCookieJar
import com.example.data.SavedCredentialsStore
import com.example.data.MiniBiDatabase
import com.example.data.MiniBiRepository
import com.example.data.WorkReportObligationEntity
import com.example.data.WorkerProfileEntity
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real AES-256-GCM cipher using standard JCA for JVM/Robolectric tests
 * (AndroidKeyStore hardware provider is unavailable on host JVM).
 */
class JvmAes256GcmCookieCipher(
    keyBytes: ByteArray = ByteArray(32) { (it * 7 + 13).toByte() }
) : CookieCipher {
    private val secretKey = SecretKeySpec(keyBytes, "AES")
    private val secureRandom = SecureRandom()

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plaintext)
        return ByteArray(1 + iv.size + encrypted.size).also { out ->
            out[0] = iv.size.toByte()
            System.arraycopy(iv, 0, out, 1, iv.size)
            System.arraycopy(encrypted, 0, out, 1 + iv.size, encrypted.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val ivLen = ciphertext[0].toInt() and 0xFF
        val iv = ciphertext.copyOfRange(1, 1 + ivLen)
        val payload = ciphertext.copyOfRange(1 + ivLen, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(payload)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthSessionAndSecurityContractTest {

    private lateinit var context: Context
    private lateinit var database: MiniBiDatabase
    private lateinit var server: MockWebServer
    private lateinit var cookieFile: File
    private lateinit var savedCredentialsFile: File
    private val cipher = JvmAes256GcmCookieCipher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MiniBiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        cookieFile = File(context.cacheDir, "test_cookies_${System.nanoTime()}.enc")
        savedCredentialsFile = File(context.cacheDir, "test_saved_creds_${System.nanoTime()}.enc")
    }

    @After
    fun tearDown() {
        database.close()
        runCatching { server.shutdown() }
        runCatching { cookieFile.delete() }
        runCatching { savedCredentialsFile.delete() }
    }

    private fun createRepository(
        baseUrl: String = server.url("/").toString().trimEnd('/'),
        allowDebugUrlOverride: Boolean = true,
        nowMillis: () -> Long = { System.currentTimeMillis() }
    ): MiniBiRepository {
        return MiniBiRepository(
            context = context,
            database = database,
            defaultServerUrl = baseUrl,
            allowDebugUrlOverride = allowDebugUrlOverride,
            cookieStorageFile = cookieFile,
            savedCredentialsStorageFile = savedCredentialsFile,
            cookieCipher = cipher,
            nowMillis = nowMillis
        )
    }

    // -------------------------------------------------------------------------
    // Item 1: Login lỗi không fallback thành actor giả
    // -------------------------------------------------------------------------
    @Test
    fun `item 1 - login failures 401 403 500 and network never fallback to fake actor`() = runBlocking {
        val repo = createRepository()

        // 401 Invalid credentials
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"ok":false,"code":"AUTH_INVALID_CREDENTIALS"}""")
        )
        val r401 = repo.login("bod@hrpartner.vn", "wrong-pass")
        assertTrue(r401.isFailure)
        assertEquals(BackendFailureKind.UNAUTHENTICATED, (r401.exceptionOrNull() as BackendApiException).kind)
        assertNull(repo.currentActorFlow.value)

        // 403 Account unavailable
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"ok":false,"code":"ACCOUNT_NOT_AVAILABLE"}""")
        )
        val r403 = repo.login("bod", "secret")
        assertTrue(r403.isFailure)
        assertEquals(BackendFailureKind.FORBIDDEN, (r403.exceptionOrNull() as BackendApiException).kind)
        assertNull(repo.currentActorFlow.value)

        // 502 Backend unavailable
        server.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody("""{"ok":false,"code":"UPSTREAM_DOWN"}""")
        )
        val r502 = repo.login("bod", "secret")
        assertTrue(r502.isFailure)
        assertEquals(BackendFailureKind.SERVER_UNAVAILABLE, (r502.exceptionOrNull() as BackendApiException).kind)
        assertNull(repo.currentActorFlow.value)

        // Network unreachable (shutdown server so TCP connection genuinely fails)
        server.shutdown()
        val rNet = repo.login("bod", "secret")
        assertTrue(rNet.isFailure)
        assertEquals(BackendFailureKind.NETWORK, (rNet.exceptionOrNull() as BackendApiException).kind)
        assertNull(repo.currentActorFlow.value)
    }

    // -------------------------------------------------------------------------
    // Item 2: Không còn tài khoản/mật khẩu hardcode trong production source
    // -------------------------------------------------------------------------
    @Test
    fun `item 2 - production source contains zero hardcoded demo accounts passwords or seed helpers`() {
        val candidates = listOf(
            File("src/main"),
            File("app/src/main"),
            File(System.getProperty("user.dir") ?: ".", "src/main"),
            File(System.getProperty("user.dir") ?: ".", "app/src/main")
        )
        val mainDir = candidates.firstOrNull { it.exists() && it.isDirectory }
        assertNotNull("Could not locate app/src/main directory for source audit", mainDir)

        val forbiddenTokens = listOf(
            "HrPartner@2026",
            "builtInAccounts",
            "dynamicActor",
            "ensureSeedDataLoaded",
            "createInitialWorkReports",
            "createInitialWorkerProfiles",
            "getBiRecruitmentFacts",
            "getDuplicateCccdAlerts",
            "generateStampedVerificationPhoto"
        )

        val sourceFiles = mainDir!!.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java", "xml", "json", "properties") }
            .toList()
        assertTrue("Expected non-empty production source tree", sourceFiles.isNotEmpty())

        for (file in sourceFiles) {
            val text = file.readText()
            for (forbidden in forbiddenTokens) {
                assertFalse(
                    "Forbidden token '$forbidden' found in production file ${file.path}",
                    text.contains(forbidden)
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Item 3: Parse actor giữ nguyên capabilities và scopes
    // -------------------------------------------------------------------------
    @Test
    fun `item 3 - actor parser preserves exact capabilities and scopes without inventing roles`() {
        val rawEnvelope = """
            {
              "ok": true,
              "actor": {
                "app_user_id": "11111111-2222-4333-8444-555555555555",
                "display_name": "Nguyễn Thị Quản Lý",
                "capabilities": ["entry_read", "change_propose", "report_review"],
                "scopes": [
                  {
                    "kind": "own",
                    "reference": "recruiter-01",
                    "valid_from": "2026-01-01",
                    "valid_to": null
                  },
                  {
                    "kind": "team",
                    "reference": "TEAM_NORTH",
                    "valid_from": "2026-02-01",
                    "valid_to": "2026-12-31"
                  }
                ],
                "self_recruiter_suggestion": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
              }
            }
        """.trimIndent()

        val actor = ActorContractParser.parseEnvelopeActor(rawEnvelope, "ql@hrpartner.vn")
        assertEquals("11111111-2222-4333-8444-555555555555", actor.appUserId)
        assertEquals("Nguyễn Thị Quản Lý", actor.displayName)
        assertEquals(listOf("entry_read", "change_propose", "report_review"), actor.capabilities)
        assertFalse(actor.hasCapability("entry_admin"))
        assertFalse(actor.hasScopeKind("all"))
        assertTrue(actor.hasScopeKind("own"))
        assertTrue(actor.hasScopeKind("team"))
        assertEquals(2, actor.scopes.size)
        assertEquals("TEAM_NORTH", actor.scopes[1].reference)
        assertEquals("2026-12-31", actor.scopes[1].validTo)
        assertEquals("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", actor.selfRecruiterSuggestion)
    }

    // -------------------------------------------------------------------------
    // Item 4: Session 401 xóa local actor và cookie
    // -------------------------------------------------------------------------
    @Test
    fun `item 4 - session 401 clears local actor cookies and cached entities`() = runBlocking {
        val repo = createRepository()

        // First login succeeds and sets session cookie
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "sb-access-token=secret_session_jwt; Path=/; HttpOnly; Max-Age=3600")
                .setBody(
                    """
                    {
                      "ok": true,
                      "actor": {
                        "app_user_id": "11111111-2222-4333-8444-555555555555",
                        "display_name": "Trần Văn A",
                        "capabilities": ["entry_read"],
                        "scopes": [{"kind":"own","reference":"self","valid_from":"2026-01-01","valid_to":null}],
                        "self_recruiter_suggestion": null
                      }
                    }
                    """.trimIndent()
                )
        )
        val loginRes = repo.login("tva", "valid-password")
        assertTrue(loginRes.isSuccess)
        assertNotNull(repo.currentActorFlow.value)
        assertEquals(1, repo.cookieJar.loadForRequest(server.url("/api/auth/session")).size)

        // Seed Room cache to verify 401 purges cached backend rows
        database.miniBiDao().insertCachedWorkers(
            listOf(
                WorkerProfileEntity(
                    cacheKey = "managed:22222222-2222-4222-8222-222222222222",
                    scopeKey = "managed",
                    entryId = "22222222-2222-4222-8222-222222222222",
                    entryVersion = 1,
                    submissionState = "SUBMITTED",
                    employeeCode = "HRP001",
                    displayName = "NLĐ Cache",
                    projectId = "PRJ1",
                    projectDisplay = "Dự án 1",
                    firstWorkDate = "2026-10-01",
                    laborType = "OFFICIAL",
                    laborTypeDisplay = "Chính thức",
                    employmentStatus = "ON",
                    recruiterId = "33333333-3333-4333-8333-333333333333",
                    recruiterDisplay = "Recruiter",
                    paymentSummary = "",
                    pendingRequestSummary = "",
                    lastDecisionSummary = "",
                    isProjectManager = true
                )
            )
        )

        // Next bootstrapSession receives 401
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"ok":false,"code":"AUTH_UNAUTHENTICATED"}""")
        )
        val bootRes = repo.bootstrapSession()
        assertTrue(bootRes.isFailure)
        assertNull(repo.currentActorFlow.value)
        assertTrue(repo.cookieJar.loadForRequest(server.url("/api/auth/session")).isEmpty())
        assertTrue(database.miniBiDao().getCachedWorkersByScope("managed").isEmpty())
    }

    // -------------------------------------------------------------------------
    // Item 5: Logout gọi backend trước khi xóa local
    // -------------------------------------------------------------------------
    @Test
    fun `item 5 - logout calls backend POST auth logout with cookie and origin before clearing local state`() = runBlocking {
        val repo = createRepository()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "mini_bi_sid=live_cookie_value_999; Path=/; HttpOnly; Max-Age=3600")
                .setBody(
                    """
                    {
                      "ok": true,
                      "actor": {
                        "app_user_id": "11111111-2222-4333-8444-555555555555",
                        "display_name": "Lê Thị B",
                        "capabilities": ["entry_read"],
                        "scopes": [{"kind":"own","reference":"self","valid_from":"2026-01-01","valid_to":null}],
                        "self_recruiter_suggestion": null
                      }
                    }
                    """.trimIndent()
                )
        )
        assertTrue(repo.login("ltb", "secret").isSuccess)
        server.takeRequest() // consume login request

        server.enqueue(MockResponse().setResponseCode(204))
        repo.logout()

        val logoutReq = server.takeRequest()
        assertEquals("POST", logoutReq.method)
        assertEquals("/api/auth/logout", logoutReq.path)
        // Proves cookie was STILL present when POST /api/auth/logout was sent!
        val sentCookie = logoutReq.getHeader("Cookie").orEmpty()
        assertTrue("Expected session cookie on logout request, got '$sentCookie'", sentCookie.contains("mini_bi_sid=live_cookie_value_999"))
        assertEquals("http://${server.hostName}:${server.port}", logoutReq.getHeader("Origin"))

        // And local state is wiped AFTER server call
        assertNull(repo.currentActorFlow.value)
        assertTrue(repo.cookieJar.loadForRequest(server.url("/api/auth/session")).isEmpty())
    }

    // -------------------------------------------------------------------------
    // Item 6: Cookie khôi phục đúng sau khi tiến trình app được tạo lại
    // -------------------------------------------------------------------------
    @Test
    fun `item 6 - encrypted cookie jar restores valid cookies across process recreation and drops expired`() {
        var fakeClock = 1_700_000_000_000L
        val targetUrl = "https://bi.hrpartner.vn/api/auth/session".toHttpUrl()

        val activeCookie = Cookie.Builder()
            .name("sb-auth-token")
            .value("super_secret_token_value_do_not_store_plaintext")
            .hostOnlyDomain("bi.hrpartner.vn")
            .path("/")
            .secure()
            .httpOnly()
            .expiresAt(fakeClock + 3_600_000L)
            .build()

        val shortLivedCookie = Cookie.Builder()
            .name("ephemeral_nonce")
            .value("expires_soon")
            .hostOnlyDomain("bi.hrpartner.vn")
            .path("/")
            .expiresAt(fakeClock + 5_000L)
            .build()

        // Process 1 saves cookies
        val jarProcess1 = EncryptedPersistentCookieJar(
            storageFile = cookieFile,
            cipher = cipher,
            nowMillis = { fakeClock }
        )
        jarProcess1.saveFromResponse(targetUrl, listOf(activeCookie, shortLivedCookie))
        assertTrue("Encrypted cookie file should exist on disk", cookieFile.exists())

        // Verify stored file on disk does NOT contain plain-text secret
        val rawDiskString = cookieFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(rawDiskString.contains("super_secret_token_value_do_not_store_plaintext"))
        assertFalse(rawDiskString.contains("sb-auth-token"))

        // Advance clock past shortLivedCookie expiry, then recreate brand-new CookieJar (Process 2)
        fakeClock += 10_000L
        val jarProcess2 = EncryptedPersistentCookieJar(
            storageFile = cookieFile,
            cipher = cipher,
            nowMillis = { fakeClock }
        )
        val restored = jarProcess2.loadForRequest(targetUrl)
        assertEquals(1, restored.size)
        assertEquals("sb-auth-token", restored[0].name)
        assertEquals("super_secret_token_value_do_not_store_plaintext", restored[0].value)
        assertTrue(restored[0].httpOnly)
        assertTrue(restored[0].secure)
    }

    // -------------------------------------------------------------------------
    // Item 7: Mutation luôn gửi đúng Origin (và không gửi X-Client-Platform)
    // -------------------------------------------------------------------------
    @Test
    fun `item 7 - every mutation sends exact scheme host port origin and strips X-Client-Platform`() = runBlocking {
        val repo = createRepository()
        val expectedOrigin = "http://${server.hostName}:${server.port}"

        // POST JSON
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        repo.httpClient.postJson("/api/test-post", JSONObject().put("hello", "world"))
        val postReq = server.takeRequest()
        assertEquals(expectedOrigin, postReq.getHeader("Origin"))
        assertNull(postReq.getHeader("X-Client-Platform"))

        // DELETE JSON
        server.enqueue(MockResponse().setResponseCode(204))
        repo.httpClient.deleteWithJsonNoContent("/api/test-delete", JSONObject().put("stage_id", "abc"))
        val deleteReq = server.takeRequest()
        assertEquals("DELETE", deleteReq.method)
        assertEquals(expectedOrigin, deleteReq.getHeader("Origin"))
        assertNull(deleteReq.getHeader("X-Client-Platform"))

        // Release mode rejects cleartext HTTP fail-closed
        val releaseClient = BackendHttpClient(
            cookieJar = repo.cookieJar,
            baseUrlProvider = { "http://bi.hrpartner.vn" },
            allowCleartextHttp = false
        )
        val cleartextError = runCatching {
            releaseClient.getJson("/api/auth/session")
        }.exceptionOrNull() as? BackendApiException
        assertNotNull(cleartextError)
        assertEquals("HTTPS_REQUIRED", cleartextError!!.backendCode)
    }

    // -------------------------------------------------------------------------
    // Item 19: Production runtime không tự seed dữ liệu mẫu
    // -------------------------------------------------------------------------
    @Test
    fun `item 19 - fresh repository and database contain zero auto-seeded workers reports or attendance`() = runBlocking {
        val repo = createRepository()
        assertTrue(database.miniBiDao().getCachedWorkersByScope("managed").isEmpty())
        assertTrue(database.miniBiDao().getCachedWorkersByScope("all").isEmpty())
        assertTrue(database.miniBiDao().getCachedWorkReportsSnapshot().isEmpty())
        assertTrue(database.miniBiDao().getRetryableAttendanceOutbox().isEmpty())
        assertNull(repo.currentActorFlow.value)
    }

    // -------------------------------------------------------------------------
    @Test
    fun `saved login accounts - encrypts credentials on disk, preserves across logout, and supports removal`() = runBlocking {
        val repo = createRepository()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "mini_bi_sid=session_cookie_123; Path=/; HttpOnly; Max-Age=3600")
                .setBody(
                    """
                    {
                      "ok": true,
                      "actor": {
                        "app_user_id": "11111111-2222-4333-8444-555555555555",
                        "display_name": "Nguyễn Thị Quản Lý",
                        "capabilities": ["entry_read"],
                        "scopes": [{"kind":"own","reference":"self","valid_from":"2026-01-01","valid_to":null}],
                        "self_recruiter_suggestion": null
                      }
                    }
                    """.trimIndent()
                )
        )

        val loginRes = repo.login(
            rawIdentifier = "ql.bacninh@hrpartner.vn",
            password = "SuperSecretLogin#2026",
            rememberAccount = true
        )
        assertTrue(loginRes.isSuccess)
        assertTrue("Encrypted saved credentials file must exist", savedCredentialsFile.exists())

        // Verify zero plaintext password or identifier on disk
        val rawSavedDisk = savedCredentialsFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(rawSavedDisk.contains("SuperSecretLogin#2026"))
        assertFalse(rawSavedDisk.contains("ql.bacninh@hrpartner.vn"))

        assertEquals(1, repo.savedAccountsFlow.value.size)
        assertEquals("ql.bacninh@hrpartner.vn", repo.savedAccountsFlow.value[0].identifier)
        assertEquals("SuperSecretLogin#2026", repo.savedAccountsFlow.value[0].password)
        assertEquals("Nguyễn Thị Quản Lý", repo.savedAccountsFlow.value[0].displayName)

        // Logout clears active session & cookies, while keeping saved account for 1-tap re-login
        server.enqueue(MockResponse().setResponseCode(204))
        repo.logout()
        assertNull(repo.currentActorFlow.value)
        assertTrue(repo.cookieJar.loadForRequest(server.url("/api/auth/session")).isEmpty())
        assertEquals(1, repo.savedAccountsFlow.value.size)
        assertEquals("ql.bacninh@hrpartner.vn", repo.savedAccountsFlow.value[0].identifier)

        // Reloading from disk in a fresh store restores exact account
        val freshStore = SavedCredentialsStore(storageFile = savedCredentialsFile, cipher = cipher)
        val reloaded = freshStore.loadAll()
        assertEquals(1, reloaded.size)
        assertEquals("ql.bacninh@hrpartner.vn", reloaded[0].identifier)
        assertEquals("SuperSecretLogin#2026", reloaded[0].password)

        // Removing saved account deletes it from state and disk
        repo.removeSavedAccount("ql.bacninh@hrpartner.vn")
        assertTrue(repo.savedAccountsFlow.value.isEmpty())
        assertFalse(savedCredentialsFile.exists())
    }

    // Item 20: Không có random UUID / capability / scope fallback
    // -------------------------------------------------------------------------
    @Test
    fun `item 20 - malformed actor responses fail closed without random UUID or default capability or scope`() {
        val invalidPayloads = listOf(
            // Missing app_user_id
            """{"ok":true,"actor":{"display_name":"A","capabilities":[],"scopes":[]}}""",
            // Non-UUID app_user_id
            """{"ok":true,"actor":{"app_user_id":"not-a-uuid","display_name":"A","capabilities":[],"scopes":[]}}""",
            // Missing capabilities
            """{"ok":true,"actor":{"app_user_id":"11111111-2222-4333-8444-555555555555","display_name":"A","scopes":[]}}""",
            // Missing scopes
            """{"ok":true,"actor":{"app_user_id":"11111111-2222-4333-8444-555555555555","display_name":"A","capabilities":[]}}""",
            // Unknown scope kind
            """{"ok":true,"actor":{"app_user_id":"11111111-2222-4333-8444-555555555555","display_name":"A","capabilities":[],"scopes":[{"kind":"uploader","reference":"x","valid_from":"2026-01-01","valid_to":null}]}}""",
            // 200 OK with ok: false
            """{"ok":false,"actor":{"app_user_id":"11111111-2222-4333-8444-555555555555","display_name":"A","capabilities":[],"scopes":[]}}"""
        )

        for (payload in invalidPayloads) {
            val err = runCatching {
                ActorContractParser.parseEnvelopeActor(payload)
            }.exceptionOrNull() as? BackendApiException
            assertNotNull("Expected fail-closed BackendApiException for payload: $payload", err)
            assertEquals(BackendFailureKind.CONTRACT_INVALID, err!!.kind)
        }
    }
}
