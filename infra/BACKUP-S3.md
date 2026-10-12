# Backup ra ngoài VPS: AWS S3 + rclone

Hướng dẫn cài đặt nơi lưu backup bên ngoài VPS, từ lúc tạo tài khoản AWS đến lúc VPS tải được file đã mã hóa lên S3.

**Vì sao cần:** backup MySQL hiện chỉ nằm trên chính VPS (`/var/backups/fem`, do `infra/backup-mysql.sh` tạo lúc 03:00), còn **ảnh hóa đơn** (Docker volume `receipt-uploads`) chưa có bản backup nào. VPS hỏng là mất cả dữ liệu lẫn backup.

**Kiến trúc:**

```
VPS (user deploy)                                  AWS (ap-southeast-1, Singapore)
/var/backups/fem/*.sql.gz ──┐                      ┌──────────────────────────────────┐
volume receipt-uploads ─────┤  rclone copy         │ bucket fem-backup-bucket          │
                            ├─► remote s3crypt ──► │   fem/  (tên file + nội dung      │
                            │   (mã hóa phía VPS)  │          đã mã hóa)              │
                            │   └─► remote s3      │ Versioning · SSE-S3 · Lifecycle  │
                            │      (IAM user không │   30 ngày → Glacier IR           │
                            │       có quyền xóa)  │   365 ngày → xóa                 │
                            └──────────────────────└──────────────────────────────────┘
```

| Thành phần | Giá trị |
|---|---|
| Region | `ap-southeast-1` (Asia Pacific – Singapore) |
| Bucket | `fem-backup-bucket` |
| Thư mục trong bucket | `fem/` |
| IAM user | `fem-backup-uploader` (chỉ ghi/đọc/liệt kê, **không xóa**) |
| IAM policy | `fem-backup-uploader-policy` |
| rclone remote trên VPS | `s3` (kết nối S3), `s3crypt` (mã hóa, bọc ngoài `s3`) |
| File cấu hình rclone | `/home/deploy/.config/rclone/rclone.conf` (quyền `600`) |

> ⚠️ Không ghi access key, secret key hay mật khẩu crypt vào file này hay bất kỳ file nào trong Git. Lưu chúng trong trình quản lý mật khẩu (Bitwarden, 1Password, …).

---

## 1. Tạo và bảo mật tài khoản AWS

1. Vào https://aws.amazon.com → **Create an AWS Account**. Cần email, số điện thoại và một thẻ **Visa/Mastercard** (thẻ ghi nợ hay thẻ ảo trong app ngân hàng đều được; nhớ bật *thanh toán trực tuyến/quốc tế* trong app ngân hàng). AWS trừ thử khoảng 1 USD rồi hoàn lại.
   - Ô **Security code** là mã CVV/CVC: 3 số ở mặt sau thẻ, hoặc xem trong app ngân hàng với thẻ ảo.
2. Chọn gói **Basic support (Free)**. Tài khoản được tạo ở gói **Free plan**: có credit dùng trong 6 tháng.
3. Khi đăng nhập, chọn **Sign in with root user** (không phải *Builder ID*: đó chỉ là hồ sơ cho công cụ lập trình, không có Console).
4. **Bật MFA cho root:** tên tài khoản (góc trên phải) → **Security credentials** → **Multi-factor authentication** → **Assign MFA device** → **Authenticator app** → quét mã QR → nhập 2 mã liên tiếp.
5. **Cảnh báo chi phí:** **Billing and Cost Management** → **Budgets** → **Create budget** → **Monthly cost budget** → `5` USD → email nhận cảnh báo.
6. **Chọn region:** góc trên phải → **Asia Pacific (Singapore) ap-southeast-1**. Bỏ qua trang *Local Zones* (ví dụ `ap-southeast-1-han-1a`): chỉ dành cho máy chủ EC2, S3 không dùng, không cần *Opt in*.

> ⚠️ **Free plan hết hạn ngày 12/04/2027** (hoặc khi dùng hết credit). Phải **nâng cấp lên Paid plan trước ngày đó** (**Billing and Cost Management → Upgrade plan**), nếu không tài khoản bị đóng và các bản backup có thể bị xóa. Paid plan không có phí cố định: chỉ trả theo dung lượng dùng (ước tính 0.2–0.6 USD/tháng cho backup của dự án), credit còn lại vẫn được dùng tiếp.

## 2. Tạo bucket S3

S3 không cần đăng ký riêng. **S3** → **Create bucket**:

| Mục | Giá trị |
|---|---|
| AWS Region | **Asia Pacific (Singapore) ap-southeast-1** (lấy theo region đang chọn ở góc trên phải; region của bucket **không đổi được** sau khi tạo) |
| Bucket type | General purpose |
| Bucket name | `fem-backup-bucket` |
| Object Ownership | **ACLs disabled** |
| Block all public access | ✅ **Bật** (không được tắt) |
| Bucket Versioning | **Enable** |
| Tags | `Project` = `family-expense-manager` (tùy chọn, để lọc chi phí) |
| Default encryption | **SSE-S3**, Bucket Key **Enable** |
| Object Lock | **Disable** (chỉ bật được lúc tạo bucket; có thể làm bucket mới có Object Lock sau) |

## 3. Lifecycle rules: tự chuyển lớp lưu trữ và tự xóa

Bucket → tab **Management** → **Create lifecycle rule**. Tạo **2 rule**.

### Rule 1: `fem-backup-retention`
- **Scope:** *Limit the scope…* → **Prefix** `fem/` → **Minimum object size** `128 KB` (file rất nhỏ như `.sha256` không bị chuyển sang Glacier, vì Glacier tính tối thiểu 128 KB mỗi file).
- **Actions:**
  - ☑ Transition current versions → **Glacier Instant Retrieval** sau `30` ngày
  - ☑ Expire current versions → `365` ngày
  - ☑ Permanently delete noncurrent versions → `30` ngày
  - ☑ *I acknowledge that this lifecycle rule will incur a transition cost per request* (phí chuyển lớp rất nhỏ)
- Ô *Delete expired object delete markers or incomplete multipart uploads* bị mờ vì rule có lọc dung lượng → làm ở rule 2.

Kết quả: mỗi bản backup được giữ khoảng **395 ngày** (365 ngày + 30 ngày ở dạng phiên bản cũ) rồi tự xóa. Ghi đè hoặc xóa nhầm thì vẫn còn 30 ngày để khôi phục bản cũ.

### Rule 2: `fem-abort-incomplete-uploads`
- **Scope:** Prefix `fem/`, **không** đặt Object size.
- **Actions:** chỉ tick ☑ *Delete expired object delete markers or incomplete multipart uploads*:
  - ☑ Delete expired object delete markers
  - ☑ Delete incomplete multipart uploads → `7` ngày
- Phần *Review* hiện "No actions defined" là bình thường.

Rule này dọn các "mảnh" của lần tải file lớn bị gián đoạn: chúng không hiện trong danh sách file nhưng vẫn bị tính tiền.

## 4. IAM user cho VPS (chỉ ghi, không xóa)

### 4.1. Policy
**IAM** → **Policies** → **Create policy** → **JSON**:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ListOnlyTheBackupFolder",
      "Effect": "Allow",
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::fem-backup-bucket",
      "Condition": {
        "StringLike": { "s3:prefix": ["fem/", "fem/*"] }
      }
    },
    {
      "Sid": "UploadAndReadBackups",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:AbortMultipartUpload"
      ],
      "Resource": "arn:aws:s3:::fem-backup-bucket/fem/*"
    }
  ]
}
```

- **Name:** `fem-backup-uploader-policy`
- **Description:** chỉ dùng chữ, số và `+=,.@-_` (không dùng `/` hay `—`), ví dụ `Upload-and-read-FEM-backups-in-fem-prefix.No-delete`, hoặc để trống.

Policy **không có** `s3:DeleteObject` và quyền cấu hình bucket: key trên VPS có bị lộ thì cũng không xóa được backup. Việc xóa bản cũ do lifecycle rule làm.

### 4.2. User
**IAM** → **Users** → **Create user** → `fem-backup-uploader` → **không** tick *Provide user access to the AWS Management Console* → **Attach policies directly** → `fem-backup-uploader-policy` → **Create user**.

### 4.3. Access key
User → tab **Security credentials** → **Create access key** → *Application running outside AWS* → description `vps-tc-1019 rclone backup` → **Create**.

- **Secret access key chỉ hiện một lần:** tải file `.csv` hoặc chép vào trình quản lý mật khẩu ngay.
- Xoay vòng key khoảng 90 ngày/lần: tạo key mới → cập nhật trên VPS (`rclone config update s3 access_key_id … secret_access_key …`) → Deactivate rồi xóa key cũ.

## 5. Cài và cấu hình rclone trên VPS

```bash
ssh fem
sudo -v && curl https://rclone.org/install.sh | sudo bash
rclone version            # nên ≥ 1.66
```

### 5.1. Remote `s3`
Chạy `rclone config` → `n`. Không chọn `s` (đặt mật khẩu cho file cấu hình), vì cron chạy tự động sẽ không có người nhập mật khẩu.

| rclone hỏi | Trả lời |
|---|---|
| `name>` | `s3` |
| `Storage>` | `s3` (Amazon S3 Compliant Storage Providers) |
| `provider>` | `AWS` |
| `env_auth>` | `false` |
| `access_key_id>` / `secret_access_key>` | key của `fem-backup-uploader` |
| `region>` | `ap-southeast-1` |
| `endpoint>`, `location_constraint>` | Enter |
| `acl>` | `bucket-owner-full-control` (bucket tắt ACL nên `private` có thể bị từ chối) |
| `server_side_encryption>` | `AES256` |
| `sse_kms_key_id>`, `storage_class>` | Enter |
| `Edit advanced config?` | **`n`** (nếu lỡ chọn `y`: nhấn Enter cho mọi câu, kể cả `bucket_object_lock_enabled`, `bucket_acl`, `requester_pays`, `sse_customer_*`) |
| `Keep this "s3" remote?` | `y` |

Cách khác, tạo bằng một lệnh. **Gõ một dấu cách ở đầu** để bash không lưu lệnh chứa key vào lịch sử:

```bash
 rclone config create s3 s3 \
  provider=AWS env_auth=false \
  access_key_id=<ACCESS_KEY_ID> secret_access_key=<SECRET_ACCESS_KEY> \
  region=ap-southeast-1 acl=bucket-owner-full-control \
  server_side_encryption=AES256 no_check_bucket=true use_dual_stack=true
```

### 5.2. Remote `s3crypt` (mã hóa phía VPS)

```bash
 rclone config create s3crypt crypt \
  remote=s3:fem-backup-bucket/fem \
  filename_encryption=standard \
  directory_name_encryption=true \
  password='<MẬT-KHẨU-1>' \
  password2='<MẬT-KHẨU-2-KHÁC-MẬT-KHẨU-1>'
```

- `rclone config create` tự obscure mật khẩu khi ghi vào file cấu hình.
- Nếu tạo bằng `rclone config` (hỏi đáp): ở câu `password` và `password2`, chọn `y` (tự nhập), **không** chọn `g` (sinh ngẫu nhiên) hay `n` (bỏ trống).
- ⚠️ **Lưu cả 2 mật khẩu crypt ra ngoài VPS ngay.** Mất mật khẩu là **mất toàn bộ backup**, kể cả AWS cũng không giải mã được. **Không đổi mật khẩu** sau khi đã có backup thật; nếu bắt buộc phải đổi, tạo remote mới (ví dụ `s3crypt2`) và giữ remote cũ đến khi các bản cũ hết hạn.
- Prompt `remote>` có dạng *"Select remote. Choose a number from below"* xuất hiện khi gõ `e` hoặc `d` ở menu chính: đó là chọn **remote đã có** để sửa/xóa, **không** phải chỗ nhập `s3:fem-backup-bucket/fem`.
- Tạo sai thì xóa rồi tạo lại: `rclone config delete s3crypt`.

### 5.3. Hoàn thiện cấu hình

```bash
rclone config update s3 no_check_bucket true      # user không có quyền tạo/kiểm tra bucket
rclone config update s3 use_dual_stack true       # kết nối S3 qua IPv6
# rclone < 1.66 thì thay dòng trên bằng:
# rclone config update s3 endpoint s3.dualstack.ap-southeast-1.amazonaws.com
chmod 600 ~/.config/rclone/rclone.conf
```

Kiểm tra (không gửi kết quả cho người khác):

```bash
rclone listremotes                                # s3:  s3crypt:
rclone config show s3 | grep -v -i key
rclone config show s3crypt | grep -v password
```

## 6. Thử nghiệm

```bash
echo "hello $(date)" > /tmp/test.txt
rclone copy /tmp/test.txt s3crypt:test/ -v        # INFO : test.txt: Copied (new)
rclone ls s3crypt:test/                           # test.txt  (tên thật)
rclone cat s3crypt:test/test.txt                  # hello …
rclone ls s3:fem-backup-bucket/fem/               # tên file đã MÃ HÓA → đúng
```

Trên Console: **S3** → `fem-backup-bucket` → `fem/` → thư mục và file có **tên lạ** (đã mã hóa). Mở file → *Storage class* **Standard**, *Server-side encryption* **SSE-S3**. Tải về trực tiếp chỉ thấy ký tự vô nghĩa. Bật **Show versions** để xem các phiên bản.

Xóa file test: chỉ xóa được trên Console bằng root (user `fem-backup-uploader` cố ý không có quyền xóa, `rclone delete` sẽ báo `AccessDenied`), hoặc để lifecycle tự xóa.

| Lỗi | Hướng xử lý |
|---|---|
| `AccessDenied` khi copy | Kiểm tra tên bucket và prefix `fem/` trong policy |
| `AccessControlListNotSupported` | `rclone config update s3 acl ""` |
| `InvalidAccessKeyId` / `SignatureDoesNotMatch` | Chép lại key (thừa dấu cách, thiếu ký tự) |
| `PermanentRedirect` / `AuthorizationHeaderMalformed` | Sai region: phải là `ap-southeast-1` |
| Timeout / `dial tcp` | Thử `rclone config update s3 use_dual_stack false` (đi qua IPv4 NAT) |

## 7. Đọc và tải backup về (VPS hoặc máy khác)

Luôn đọc qua **`s3crypt:`**: rclone tự giải mã cả tên lẫn nội dung. Đọc qua `s3:` chỉ nhận được dữ liệu mã hóa.

```bash
rclone ls s3crypt:                                        # liệt kê (tên thật)
rclone cat s3crypt:test/test.txt                          # xem file văn bản nhỏ
rclone copy s3crypt:mysql/2026/10/ /tmp/restore/ -P        # tải cả thư mục
```

### Trên Windows
1. Cài rclone: `winget install Rclone.Rclone`, rồi **mở lại** PowerShell. rclone là công cụ dòng lệnh: bấm đúp `rclone.exe` sẽ chỉ hiện thông báo *"This is a command line tool"*. Mỗi lần dùng, mở PowerShell/cmd và gõ lệnh; nếu chỉ giải nén mà chưa cài thì mở cmd ngay trong thư mục chứa `rclone.exe` (trong PowerShell gõ `.\rclone`).
2. `rclone config` → tạo **đúng** hai remote như mục 5: `s3` (access key, `ap-southeast-1`, `bucket-owner-full-control`, advanced config `n`) và `s3crypt` (`s3:fem-backup-bucket/fem`, `standard`, `true`, **đúng 2 mật khẩu đã dùng trên VPS**, chọn `y` chứ không chọn `g`).
3. Cấu hình lưu ở `C:\Users\<user>\AppData\Roaming\rclone\rclone.conf`, vẫn còn sau khi đóng cửa sổ.
4. Tải về:

```powershell
rclone ls s3crypt:
rclone copy s3crypt:test/test.txt C:\fem-restore\ -P
rclone copy s3crypt:mysql/2026/10/ C:\fem-restore\mysql\2026-10\ -P
Get-FileHash C:\fem-restore\<file>.sql.gz -Algorithm SHA256   # so với file .sha256 đi kèm
```

Tùy chọn hữu ích: `--include "*.sql.gz"`, `--max-age 7d`, `--dry-run`, `--transfers 8`. Đường dẫn có dấu cách thì đặt trong ngoặc kép.

- PowerShell lưu lịch sử lệnh tại `%APPDATA%\Microsoft\Windows\PowerShell\PSReadLine\ConsoleHost_history.txt`. Đã gõ lệnh có mật khẩu (`rclone config create … password=…`) thì mở file này và xóa dòng đó.
- File `rclone.conf` chứa key và mật khẩu crypt (dạng obscure): không chia sẻ, không đồng bộ lên cloud công khai; xóa đi nếu chỉ dùng thử.

> Nên giải mã thử từ một máy khác ngoài VPS ít nhất một lần: đó là bằng chứng các mật khẩu đã lưu là đúng và dùng được khi VPS mất.

## 8. Việc còn lại

- [ ] Viết `infra/batch/bin/offsite-backup.sh` (xem README → *Batch*):
  ```
  kiểm tra bản dump mới nhất (độ mới < ~2 giờ, không rỗng, gzip -t)
  → tar volume receipt-uploads (container tạm, mount :ro)
  → sha256 cho từng file
  → rclone copy (KHÔNG dùng sync) lên s3crypt:mysql/YYYY/MM/ và s3crypt:receipts/YYYY/MM/
  → rclone check
  → notify khi lỗi / heartbeat (healthchecks.io) khi thành công
  ```
  Không dùng `rclone sync`: `backup-mysql.sh` tự xóa bản local cũ hơn 14 ngày, `sync` sẽ xóa luôn bản trên S3.
- [ ] Cron **03:30** (sau backup local 03:00), chạy dưới user `deploy` (cấu hình rclone nằm ở `/home/deploy`). `/var/backups/fem` thuộc root: cấp quyền đọc cho `deploy`, hoặc chạy dưới root với `rclone --config /home/deploy/.config/rclone/rclone.conf`.
- [ ] **Khôi phục thử một lần**: tải bản mới nhất → kiểm tra sha256 → gunzip → import vào container MySQL **tạm** → kiểm tra số liệu → ghi các bước vào `DEPLOY.md`.
- [ ] Lưu ra ngoài VPS: access key, 2 mật khẩu crypt, các secret trong `.env.prod` (đặc biệt `TOTP_ENCRYPTION_KEY`, `JWT_SECRET`).
- [ ] Nhắc lịch nâng cấp AWS lên Paid plan **trước 12/04/2027**.

## Chi phí tham khảo (Singapore)

| Hạng mục | Đơn giá | Ước tính |
|---|---|---|
| S3 Standard (30 ngày gần nhất) | $0.025/GB/tháng | vài GB → < $0.2/tháng |
| Glacier Instant Retrieval (31–365 ngày) | ~$0.005/GB/tháng (lưu tối thiểu 90 ngày, file tối thiểu 128 KB) | < $0.4/tháng |
| Request PUT/LIST | ~$0.005/1.000 | ~0 |
| Tải về khi khôi phục | 100 GB/tháng đầu miễn phí (chính sách hiện tại) | ~0 |

Không cần dùng *S3 Intelligent-Tiering* (tính thêm phí giám sát theo số object): với backup, lifecycle rule rẻ và dễ đoán chi phí hơn. Giá có thể thay đổi, xem https://aws.amazon.com/s3/pricing/.
