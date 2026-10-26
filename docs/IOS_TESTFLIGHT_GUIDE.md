# Hướng dẫn Cấu hình Apple Developer Secrets & Phát hành Tự động lên TestFlight (iOS)

Tài liệu này hướng dẫn chi tiết từng bước để tạo chứng chỉ ký số iOS ngay trên **Windows (PowerShell + OpenSSL)** hoặc **macOS**, cấu hình **6 GitHub Secrets** và kích hoạt GitHub Actions tự động đóng gói file `.ipa` đẩy thẳng lên **TestFlight (App Store Connect)**.

---

## 1. Thông tin Định danh Ứng dụng (Bundle ID & Phiên bản)

| Biến thể | Tên hiển thị | Bundle ID (`App ID`) | Máy chủ mặc định | Xcode Scheme |
| :--- | :--- | :--- | :--- | :--- |
| **Release (Chính thức)** | `HRP BI` | `com.aistudio.minibi.hrpbia` | `https://bi.hrpartner.vn` | `HRP-BI` |
| **Debug / Staging** | `HRP BI 2` | `com.aistudio.minibi.hrpbia.bi2` | `https://bi2.hrpartner.vn` | `HRP-BI2` |

- **Quy tắc `MARKETING_VERSION` (`CFBundleShortVersionString`)**: `1.0.<N>.<DDMMYYYY>` (ví dụ: `1.0.1.27102026`).
- **Quy tắc `CURRENT_PROJECT_VERSION` (`CFBundleVersion`)**: Số nguyên tăng dần mỗi lần đẩy lên TestFlight (`1, 2, 3, ...`).

---

## 2. Tạo Chứng chỉ `.p12` (Apple Distribution Certificate) ngay trên Windows

Khi đã có tài khoản **Apple Developer Program** (99 USD/năm), bạn có thể tạo file `.p12` ngay trên máy Windows bằng `openssl` (có sẵn trong Git for Windows tại `C:\Program Files\Git\usr\bin\openssl.exe`) mà không cần máy Mac:

### Bước 2.1: Tạo Private Key và file CSR (`.certSigningRequest`) trên PowerShell
Mở PowerShell tại một thư mục tạm (ví dụ `C:\temp\apple-cert`) và chạy:

```powershell
mkdir C:\temp\apple-cert -Force
cd C:\temp\apple-cert

# 1. Tạo khóa riêng RSA 2048-bit
& "C:\Program Files\Git\usr\bin\openssl.exe" genrsa -out ios_distribution.key 2048

# 2. Tạo file yêu cầu chứng chỉ (CSR)
& "C:\Program Files\Git\usr\bin\openssl.exe" req -new -key ios_distribution.key -out ios_distribution.csr -subj "/emailAddress=admin@hrpartner.vn/CN=HRP BI Distribution/C=VN"
```

### Bước 2.2: Tải CSR lên Apple Developer Portal để lấy `distribution.cer`
1. Đăng nhập vào [Apple Developer — Certificates](https://developer.apple.com/account/resources/certificates/list).
2. Bấm nút **`+`** -> Chọn **Apple Distribution** -> **Continue**.
3. Tải file `ios_distribution.csr` vừa tạo lên -> Bấm **Continue** -> **Download** để tải file `distribution.cer` về thư mục `C:\temp\apple-cert`.

### Bước 2.3: Ghép `distribution.cer` + `ios_distribution.key` thành file `.p12`
Chạy tiếp trên PowerShell (thay `MatKhauP12CuaBan` bằng mật khẩu bạn tự đặt):

```powershell
# 1. Chuyển .cer dạng DER của Apple sang PEM
& "C:\Program Files\Git\usr\bin\openssl.exe" x509 -in distribution.cer -inform DER -out distribution.pem -outform PEM

# 2. Xuất ra file chứng chỉ .p12 (sử dụng thuật toán tương thích macOS Keychain)
& "C:\Program Files\Git\usr\bin\openssl.exe" pkcs12 -export -inkey ios_distribution.key -in distribution.pem -out ios_distribution.p12 -passout pass:MatKhauP12CuaBan -legacy
```

---

## 3. Tạo App ID & Provisioning Profile (`.mobileprovision`)

1. **Đăng ký Bundle ID (`Identifiers`)**:
   - Vào [Apple Developer — Identifiers](https://developer.apple.com/account/resources/identifiers/list) -> Bấm **`+`** -> Chọn **App IDs** -> **App**.
   - Nhập Description: `HRP BI` và Bundle ID (Explicit): `com.aistudio.minibi.hrpbia` -> **Continue** -> **Register**.
   - *(Tùy chọn)* Lặp lại cho `com.aistudio.minibi.hrpbia.bi2` nếu muốn đẩy cả bản Staging lên TestFlight.
2. **Tạo Provisioning Profile (`Profiles`)**:
   - Vào [Apple Developer — Profiles](https://developer.apple.com/account/resources/profiles/list) -> Bấm **`+`**.
   - Chọn **App Store Connect** (trong mục *Distribution*) -> **Continue**.
   - Chọn App ID `com.aistudio.minibi.hrpbia` -> **Continue**.
   - Chọn chứng chỉ **Apple Distribution** vừa tạo ở mục 2 -> **Continue**.
   - Đặt tên Profile: `HRP BI AppStore` -> **Generate** -> **Download** file `HRP_BI_AppStore.mobileprovision` về `C:\temp\apple-cert`.

---

## 4. Tạo App Store Connect API Key (`.p8`)

1. Đăng nhập vào [App Store Connect — Users and Access — Integrations](https://appstoreconnect.apple.com/access/integrations/api).
2. Trong tab **Team Keys**, bấm nút **`+`** để tạo API Key mới:
   - **Name**: `GitHub Actions TestFlight`
   - **Access**: Chọn **App Manager** (hoặc **Admin**).
3. Sau khi tạo:
   - Sao chép **Issuer ID** (nằm ở đầu trang).
   - Sao chép **Key ID** (ví dụ `ABC1234567`).
   - Bấm **Download API Key** để tải file `AuthKey_ABC1234567.p8` về `C:\temp\apple-cert` *(Lưu ý: Apple chỉ cho tải file `.p8` 1 lần duy nhất)*.
4. Đảm bảo trong [App Store Connect — Apps](https://appstoreconnect.apple.com/apps) bạn đã tạo ứng dụng mới với Bundle ID `com.aistudio.minibi.hrpbia`.

---

## 5. Mã hóa Base64 trên PowerShell & Khai báo 6 GitHub Secrets

Chạy các lệnh PowerShell dưới đây để copy chuỗi Base64 vào Clipboard:

```powershell
# 1. Mã hóa file .p12 sang Base64 (tự copy vào Clipboard)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\temp\apple-cert\ios_distribution.p12")) | Set-Clipboard

# 2. Mã hóa file .mobileprovision sang Base64
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\temp\apple-cert\HRP_BI_AppStore.mobileprovision")) | Set-Clipboard

# 3. Mã hóa file AuthKey_XXXX.p8 sang Base64
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\temp\apple-cert\AuthKey_ABC1234567.p8")) | Set-Clipboard
```

Mở GitHub Repository -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**, và thêm đủ **6 biến Secrets** sau:

| Tên GitHub Secret | Giá trị cần điền |
| :--- | :--- |
| `APPLE_CERTIFICATE_BASE64` | Chuỗi Base64 của file `ios_distribution.p12` |
| `APPLE_CERTIFICATE_PASSWORD` | Mật khẩu bạn đã đặt khi xuất file `.p12` (`MatKhauP12CuaBan`) |
| `APPLE_PROVISIONING_PROFILE_BASE64` | Chuỗi Base64 của file `HRP_BI_AppStore.mobileprovision` |
| `APP_STORE_CONNECT_API_KEY_ID` | Mã **Key ID** (ví dụ: `ABC1234567`) |
| `APP_STORE_CONNECT_ISSUER_ID` | Mã **Issuer ID** (dạng UUID `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`) |
| `APP_STORE_CONNECT_API_KEY_BASE64` | Chuỗi Base64 của file `AuthKey_ABC1234567.p8` |

---

## 6. Cách Phát hành Bản mới lên TestFlight

Sau khi khai báo đủ 6 GitHub Secrets, bạn có 2 cách để xuất file `.ipa` đã ký và đẩy thẳng lên TestFlight:

### Cách 1: Bấm nút trực tiếp trên giao diện GitHub Actions (`workflow_dispatch`)
1. Vào tab **Actions** trên GitHub -> Chọn workflow **iOS CI & TestFlight Release (HRP BI)**.
2. Bấm **Run workflow**:
   - **Tag phiên bản phát hành**: `V1.0.1.27102026`
   - **Số thứ tự build tăng dần**: `1` (lần tiếp theo tăng lên `2`, `3`, ...)
   - **Scheme**: `HRP-BI`
3. Bấm **Run workflow**. Sau ~6–8 phút, bản build sẽ xuất hiện trên **GitHub Releases** (kèm mã `SHA-256`) và tự động có mặt trong **TestFlight** trên App Store Connect.

### Cách 2: Đẩy Git Tag từ dòng lệnh
```powershell
git tag V1.0.1.27102026
git push origin V1.0.1.27102026
```
Workflow sẽ tự động kích hoạt cả Chế độ 1 (Test) và Chế độ 2 (Archive + Sign `.ipa` + GitHub Release + Upload TestFlight).
