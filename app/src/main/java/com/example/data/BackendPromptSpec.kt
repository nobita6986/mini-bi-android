package com.example.data

object BackendPromptSpec {

    const val PROMPT_TITLE = "Prompt chuẩn cho Backend Coder: Module Chấm Công GPS + Ảnh (W3-S1 Attendance)"

    val FULL_BACKEND_PROMPT = """
# TASK BRIEF FOR BACKEND CODER: W3-S1 — Event-Log GPS & Photo Verified Attendance (`nobita6986/mini-bi`)

Bạn là Senior Backend Engineer phụ trách dự án `nobita6986/mini-bi` (Next.js 16 App Router, TypeScript strict, Supabase PostgreSQL, Service-Role RPC allowlist, Cloudflare R2 S3-compatible object storage, `sharp` image sanitizer).

Hãy triển khai **Module Chấm Công Từ Xa (Remote Employee Attendance)** theo kiến trúc **Append-Only Event Log (Không giới hạn cứng số lần chấm công hoặc tên khung giờ)** để không bao giờ phải sửa code khi thay đổi quy định hành chính.

---

## 1. Triết lý Thiết kế (Zero-Config / Schema-Free Frequency)
- **Không hardcode số lần chấm công hay tên ca/khung giờ**: Việc một nhân viên chấm công 2 lần/ngày (8h & 17h), 4 lần/ngày (sáng & trưa), hay sau này phát sinh 6 lần/ngày là **mệnh lệnh hành chính**, không phải ràng buộc kỹ thuật trong code.
- **Client (Mobile/Web)**: Chỉ có một hành động duy nhất — khi nhân viên bấm nút **Chấm công**, thiết bị lấy tọa độ GPS (`latitude`, `longitude`, `accuracy_meters`), chụp ảnh thực tế, ký `photo_sha256` kèm `client_captured_at` (ISO-8601) và gửi sự kiện (event) về Backend. Nhân viên có thể bấm chấm công bất kỳ số lần nào trong ngày.
- **Backend**:
  - Lưu mọi sự kiện chấm công vào bảng append-only `public.attendance_checkins`.
  - Khi truy vấn báo cáo/lịch sử theo ngày (`work_date`), Backend tự động **đếm tổng số lần chấm công trong ngày (`daily_checkin_count`)**, đánh số thứ tự từng lần (`checkin_seq_in_day`: Lần 1, Lần 2, Lần 3, Lần 4...), lấy giờ chấm đầu tiên (`first_checkin_at`), giờ chấm cuối cùng (`last_checkin_at`) và danh sách chi tiết từng thời điểm + tọa độ + ảnh chụp.

---

## 2. Database Migration (`supabase/migrations/20261024090000_w3_s1_attendance_checkins.sql`)

### 2.1. Bảng `public.attendance_checkins` (Append-only Event Log)
```sql
begin;

-- 1) Thêm capability mới vào direct_entry_capability_grants_capability_check
alter table public.direct_entry_capability_grants
  drop constraint direct_entry_capability_grants_capability_check;

alter table public.direct_entry_capability_grants
  add constraint direct_entry_capability_grants_capability_check
  check (capability in (
    'entry_create', 'entry_own', 'entry_team', 'entry_admin', 'entry_restore',
    'submission_create', 'change_request_create', 'change_review', 'entry_privileged_edit',
    'employment_status.request', 'employment_status.review', 'employment_status.apply',
    'document_upload', 'document_view', 'payment_view', 'payment_edit',
    'recruiter_master_manage', 'team_master_manage', 'pii_view', 'pii_export', 'audit_view',
    'catalog_master_manage', 'team_manager_assign',
    'report_form_manage', 'report_assign', 'report_view', 'report_analytics_view',
    'attendance_checkin', 'attendance_review_team', 'attendance_review_all'
  ));

-- 2) Bảng lưu trữ sự kiện chấm công (Append-only, không giới hạn số lần/ngày, không hardcode tên khung giờ)
create table public.attendance_checkins (
  checkin_id uuid primary key default gen_random_uuid(),
  app_user_id uuid not null references public.direct_entry_app_users(app_user_id) on delete restrict,
  team_id uuid references public.teams(team_id) on delete restrict,
  work_date date not null default (now() at time zone 'Asia/Ho_Chi_Minh')::date,
  
  -- GPS Telemetry
  latitude double precision not null check (latitude between -90.0 and 90.0),
  longitude double precision not null check (longitude between -180.0 and 180.0),
  accuracy_meters real not null check (accuracy_meters >= 0 and accuracy_meters <= 5000),
  address_hint text check (address_hint is null or length(address_hint) <= 500),
  
  -- Photo & Object Storage Integrity
  photo_r2_key text not null unique check (length(btrim(photo_r2_key)) between 10 and 512),
  photo_sha256 text not null check (photo_sha256 ~ '^[a-f0-9]{64}$'),
  photo_mime_type text not null check (photo_mime_type in ('image/jpeg', 'image/png', 'image/webp')),
  photo_byte_size integer not null check (photo_byte_size between 1024 and 5242880),
  
  -- Timestamp Verification
  client_captured_at timestamptz not null,
  server_received_at timestamptz not null default now(),
  clock_skew_seconds integer generated always as (
    extract(epoch from (server_received_at - client_captured_at))::integer
  ) stored,
  is_time_verified boolean generated always as (
    abs(extract(epoch from (server_received_at - client_captured_at))) <= 300
  ) stored,
  
  note text check (note is null or length(note) <= 1000),
  idempotency_key uuid not null,
  created_at timestamptz not null default now(),
  
  unique (app_user_id, idempotency_key)
);

create index attendance_checkins_user_date_idx
  on public.attendance_checkins (app_user_id, work_date desc, client_captured_at asc);

create index attendance_checkins_team_date_idx
  on public.attendance_checkins (team_id, work_date desc, client_captured_at asc);

-- Bật RLS & thu hồi toàn bộ quyền anon/authenticated trực tiếp (Zero-Trust như các bảng W2/F1)
alter table public.attendance_checkins enable row level security;
revoke all on table public.attendance_checkins from public, anon, authenticated;
grant select, insert on table public.attendance_checkins to service_role;

commit;
```

---

## 3. RPC Functions (`SECURITY DEFINER` + Service-Role Allowlist)
Tạo 2 hàm RPC trong PostgreSQL và thêm vào `s0_t1_c_rpc_acl_allowlist`:

1. `public.rpc_attendance_record_checkin(...)`:
   - Nhận `p_actor_app_user_id`, `p_latitude`, `p_longitude`, `p_accuracy_meters`, `p_address_hint`, `p_photo_r2_key`, `p_photo_sha256`, `p_photo_mime_type`, `p_photo_byte_size`, `p_client_captured_at`, `p_note`, `p_idempotency_key`.
   - Tự động tính `work_date = (p_client_captured_at at time zone 'Asia/Ho_Chi_Minh')::date` và tra cứu `team_id` hiện hành của nhân sự từ `public.team_memberships`.
   - Đảm bảo Idempotency: nếu `(app_user_id, idempotency_key)` đã tồn tại, trả về bản ghi cũ.
   - Sau khi insert, trả về luôn `checkin_seq_in_day` (lần chấm thứ mấy trong ngày `work_date` của nhân sự đó) và `daily_checkin_count`.

2. `public.rpc_attendance_summary_and_logs_scoped(p_actor_app_user_id uuid, p_from date, p_to date, p_team_id uuid, p_user_id uuid)`:
   - Dùng Window Functions (`ROW_NUMBER() OVER (PARTITION BY app_user_id, work_date ORDER BY client_captured_at ASC)` và `COUNT(*) OVER (PARTITION BY app_user_id, work_date)`) để tự động tổng hợp cho mỗi nhân sự theo từng ngày:
     - `daily_checkin_count`: Tổng số lần nhân sự đã chấm công trong ngày (1, 2, 3, 4, 6... lần).
     - `first_checkin_at`: Thời điểm chấm công đầu tiên trong ngày.
     - `last_checkin_at`: Thời điểm chấm công cuối cùng trong ngày.
     - `checkins`: Mảng JSON chứa toàn bộ các lần chấm trong ngày theo thứ tự thời gian (`seq_no`, `checkin_id`, `client_captured_at`, `server_received_at`, `latitude`, `longitude`, `accuracy_meters`, `address_hint`, `photo_r2_key`, `note`, `is_time_verified`).
   - Phân quyền đọc dữ liệu:
     - `attendance_review_all` (BoD/Admin): Xem toàn bộ nhân sự.
     - `attendance_review_team` (Team Leader): Xem các thành viên thuộc nhóm mình quản lý + bản thân.
     - `attendance_checkin` (Nhân viên): Xem lịch sử chấm công của chính mình.

---

## 4. Next.js API Routes cần tạo

### 4.1. `POST /api/attendance/check-in` (`src/app/api/attendance/check-in/route.ts`)
- **Headers**:
  - `Content-Type: multipart/form-data`
  - `Idempotency-Key: <uuid-v4>`
- **Form Fields**:
  - `photo`: File ảnh (`image/jpeg` hoặc `image/png`, tối đa 5MB).
  - `metadata`: Chuỗi JSON chứa:
    ```json
    {
      "latitude": 21.028511,
      "longitude": 105.804817,
      "accuracy_meters": 8.4,
      "address_hint": "Văn phòng điều hành HR Partner, Cầu Giấy, Hà Nội",
      "client_captured_at": "2026-10-23T08:02:15.123+07:00",
      "photo_sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "note": "",
      "idempotency_key": "550e8400-e29b-41d4-a716-446655440000"
    }
    ```
- **Xử lý trên Server**:
  1. Kiểm tra `process.env.ATTENDANCE_API_ENABLED === "true"`.
  2. Xác thực actor qua `resolveSession()`.
  3. Kiểm tra `sha256Hex(rawPhotoBytes) === metadata.photo_sha256` để chống giả mạo ảnh trên đường truyền.
  4. Dùng `sanitizeWorkerPortrait(rawPhotoBytes)` (`sharp`) để re-encode ảnh sang JPEG chuẩn, loại bỏ EXIF/metadata độc hại, giới hạn kích thước tối đa `1600x1600`.
  5. Upload lên Cloudflare R2 theo key: `attendance/${'$'}{work_date}/${'$'}{app_user_id}/${'$'}{checkin_uuid}.jpg`.
  6. Gọi `rpc_attendance_record_checkin` để lưu vào DB và trả về:
     ```json
     {
       "ok": true,
       "data": {
         "checkin_id": "...",
         "work_date": "2026-10-23",
         "checkin_seq_in_day": 1,
         "daily_checkin_count": 1,
         "server_received_at": "2026-10-23T01:02:16.002Z",
         "is_time_verified": true,
         "clock_skew_seconds": 1
       }
     }
     ```

### 4.2. `GET /api/attendance/history` (`src/app/api/attendance/history/route.ts`)
- Hỗ trợ query params: `?from=YYYY-MM-DD&to=YYYY-MM-DD&team_id=...&user_id=...`
- Trả về bảng tổng hợp theo ngày (`daily_checkin_count`, `first_checkin_at`, `last_checkin_at`) và danh sách chi tiết từng lần chấm công kèm Presigned URL ngắn hạn (TTL 300s) từ Cloudflare R2 cho trường `photo_preview_url`. Quản lý nhân sự nhìn vào bảng này sẽ biết ngay ai chấm mấy lần trong ngày, vào những khung giờ nào và tại tọa độ nào mà không cần bất kỳ cấu hình ca/khung giờ cố định nào.
""".trimIndent()
}
