# Mini BI — Android App (HR Partner)

Ứng dụng Android nội bộ kết nối trực tiếp Backend thật của hệ thống **Mini BI** (xác thực phiên mã hóa Android Keystore, cấp mới & danh bạ nhân sự, báo cáo công việc ngày, báo cáo điều hành và chấm công đối chiếu mốc giờ máy chủ).

## Yêu cầu môi trường phát triển

- **JDK**: `21` (tương thích Gradle `9.3.1` và Android Gradle Plugin `9.1.1`)
- **Android SDK**: `platforms;android-36.1` và `build-tools;36.1.0`
- **Debug Keystore cục bộ**: Nếu vừa clone mới và chưa có `debug.keystore` ở thư mục gốc (file này nằm trong `.gitignore`), tạo một lần bằng lệnh chuẩn Android Debug:

```bash
keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

## Chạy kiểm tra tương đương GitHub Actions CI tại máy local

### macOS / Linux (Bash)

```bash
# 1. Kiểm tra manifest phân shard (đảm bảo không bỏ sót / trùng lớp chứa @Test)
java scripts/CiTestShards.java --verify

# 2. Chạy từng shard unit test giống hệt trên CI (hoặc `./gradlew :app:testDebugUnitTest` để chạy gộp)
java scripts/CiTestShards.java --run --shard 1
java scripts/CiTestShards.java --run --shard 2

# 3. Chạy Android Lint và build thử bản debug
./gradlew :app:lintDebug :app:assembleDebug --console=plain
```

### Windows (PowerShell)

```powershell
# 1. Kiểm tra manifest phân shard
java scripts/CiTestShards.java --verify

# 2. Chạy từng shard unit test (hoặc `.\gradlew.bat :app:testDebugUnitTest` để chạy gộp)
java scripts/CiTestShards.java --run --shard 1
java scripts/CiTestShards.java --run --shard 2

# 3. Chạy Android Lint và build thử bản debug
.\gradlew.bat :app:lintDebug :app:assembleDebug --console=plain
```

> Khi thêm một lớp kiểm thử mới chứa `@Test` dưới `app/src/test`, hãy khai báo tên lớp đầy đủ vào `scripts/ci-test-shards.txt` (shard `1` hoặc `2`). Bước `Test manifest guard` sẽ báo lỗi ngay nếu phát hiện lớp `@Test` bị bỏ sót, trùng lặp hoặc đã xóa.

## Cấu hình Required Status Checks trên GitHub (`main`)

Nếu Owner muốn bật Branch Protection để khóa merge vào `main` khi CI chưa xanh, cấu hình tại **Settings $\rightarrow$ Branches $\rightarrow$ Branch protection rules (`main`) $\rightarrow$ Require status checks to pass before merging**:

- **Check tổng hợp khuyến nghị** *(bắt buộc tất cả job con phải `success`, không cần sửa rule nếu sau này tăng/giảm số shard)*:
  - `Quality (manifest · unit-tests · lint · assemble)`
- **Hoặc chọn chi tiết từng check độc lập**:
  - `Test manifest guard`
  - `Unit tests (shard 1/2)`
  - `Unit tests (shard 2/2)`
  - `Android Lint (debug)`
  - `Assemble APK (debug)`
