# Triển khai lên VPS

Hướng dẫn chạy Family Expense Manager trên một VPS Linux (Ubuntu 22.04/24.04) bằng `docker-compose.prod.yml`,
có HTTPS qua Nginx + Let's Encrypt (Certbot). **Không dùng `docker-compose.yml` / `docker-compose-dev.yml` trên VPS** — hai file đó
mở cổng debug JDWP và cổng MySQL/Kafka/Redis ra ngoài.

```
Internet ──443──▶ Nginx ──/api/*──▶ api-gateway ──▶ auth / expense / notification-service
                     └────── /* ──▶ frontend (nginx)
MySQL, Kafka, Redis, Eureka: chỉ trong mạng nội bộ fem-network, không có cổng nào ra ngoài.
```

## 0. Chuẩn bị

| Cần | Ghi chú |
|---|---|
| VPS | Tối thiểu **4 GB RAM**, 2 vCPU, 30 GB đĩa. 2 GB không đủ cho 5 JVM + Kafka + MySQL |
| Tên miền | Ví dụ `chitieu.example.com` |
| DNS | Bản ghi **A** trỏ tên miền về IP của VPS (và AAAA nếu VPS có IPv6). Chờ DNS cập nhật trước bước 4 — Let's Encrypt cần nó để cấp chứng chỉ |
| SMTP | Gmail: bật 2FA cho tài khoản Google rồi tạo **App Password** |

### VPS chỉ có IPv6 → đưa tên miền qua Cloudflare

Nếu VPS chỉ có IPv6 (IPv4 chỉ là địa chỉ nội bộ để đi ra ngoài), người dùng mạng chỉ có IPv4 sẽ không vào
được. Cloudflare (gói Free) nhận cả IPv4 lẫn IPv6 rồi chuyển vào VPS bằng IPv6:

1. Tạo tài khoản ở cloudflare.com → **Add a site** → nhập tên miền → chọn gói **Free**.
2. Cloudflare đưa 2 nameserver (dạng `xxx.ns.cloudflare.com`). Vào trang quản lý tên miền (nơi đăng ký) →
   đổi **Nameserver** sang 2 địa chỉ đó. Chờ Cloudflare báo **Active** (vài phút đến vài giờ).
3. Cloudflare → **DNS → Records**: thêm bản ghi **AAAA**, Name `@`, IPv6 = địa chỉ IPv6 của VPS,
   Proxy status **DNS only (đám mây xám)** — để xám cho tới khi lấy xong chứng chỉ ở bước 4.
4. Lấy chứng chỉ (bước 4 bên dưới). Xong thì quay lại bật **Proxied (đám mây cam)**.
5. Cloudflare → **SSL/TLS → Overview**: chọn **Full (strict)**. (Không chọn *Flexible* — sẽ lặp redirect vô hạn.)

Cấu hình nginx hiện **không** đọc `CF-Connecting-IP` (deploy hiện tại không dùng Cloudflare — tin header đó khi
không có Cloudflare sẽ cho phép giả IP). Nếu chuyển sang Cloudflare, cần thêm `set_real_ip_from` (dải IP Cloudflare)
+ `real_ip_header CF-Connecting-IP;` vào `nginx/templates/default.conf.template`.

## 1. Cài Docker và tường lửa trên VPS

```bash
ssh root@<IP-VPS>
apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh

# Chỉ SSH + web. (Các container khác không publish cổng nào, nên không có gì để Docker "lách" qua UFW.)
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw enable
```

Nên tạo user riêng thay vì dùng root, và tắt đăng nhập SSH bằng mật khẩu (chỉ dùng SSH key).

## 2. Đưa code lên VPS

```bash
mkdir -p /opt && cd /opt
git clone <url-repo> family-expense-manager
cd family-expense-manager/infra
chmod +x backup-mysql.sh
```

## 3. Tạo `.env.prod`

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
nano .env.prod
```

Điền **mọi** giá trị `change-me`:

| Biến | Giá trị |
|---|---|
| `DOMAIN` | Tên miền, **không** có `https://` (ví dụ `chitieu.example.com`) |
| `LETSENCRYPT_EMAIL` | Email tài khoản Let's Encrypt (nhận cảnh báo chứng chỉ sắp hết hạn) |
| `MYSQL_ROOT_PASSWORD`, `FEM_*_DB_PASSWORD` | `openssl rand -base64 32` cho mỗi cái — **khác nhau, không dùng lại `root`** |
| `JWT_SECRET` | `openssl rand -base64 64` |
| `TOTP_ENCRYPTION_KEY` | Cài mới: một key ngẫu nhiên mới. Chuyển DB cũ sang: **dùng lại đúng key cũ**, nếu không 2FA đã bật sẽ không giải mã được |
| `SMTP_*`, `MAIL_FROM` | Thông tin SMTP; Gmail dùng cổng 587 + App Password |
| `GOOGLE_CLIENT_*` | Tuỳ chọn, xem bước 6 |
| `GRAFANA_ADMIN_PASSWORD` | Chỉ cần khi bật monitoring |

`APP_BASE_URL`, `FRONTEND_BASE_URL`, `CORS_ALLOWED_ORIGINS`, URL redirect OAuth2 và URL API của frontend đều được
**tự suy ra từ `DOMAIN`** trong `docker-compose.prod.yml` — không cần điền riêng.

## 4. Lấy chứng chỉ HTTPS lần đầu

Nginx không khởi động được khi chưa có chứng chỉ, nên lần đầu lấy chứng chỉ bằng script (Certbot tự mở cổng 80):

```bash
cd /opt/family-expense-manager/infra
STAGING=1 ./init-letsencrypt.sh   # (tuỳ chọn) chạy thử với môi trường staging, không bị giới hạn số lần
FORCE=1 ./init-letsencrypt.sh     # chứng chỉ thật (FORCE=1 để thay chứng chỉ staging nếu vừa chạy thử)
```

Thấy `Successfully received certificate` là xong. Let's Encrypt giới hạn 5 chứng chỉ trùng nhau mỗi tuần — nếu
còn lỗi DNS/tường lửa thì sửa bằng `STAGING=1` trước.

Từ đó về sau container `certbot` tự gia hạn (kiểm tra 2 lần/ngày, chỉ gia hạn khi còn dưới 30 ngày) và
`nginx` tự reload mỗi 6 giờ để dùng chứng chỉ mới — không cần cron.

## 5. Khởi động

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod ps
```

Lần đầu build mất 5–15 phút. Kiểm tra:

```bash
docker logs fem-nginx               # không có "emerg"/"error"
docker logs fem-auth-service | grep -E "Started|ERROR"
curl -I https://<DOMAIN>            # HTTP/2 200
curl -I https://<DOMAIN>/api/actuator/health   # phải 404 — actuator không ra ngoài
```

Mở `https://<DOMAIN>` → đăng ký tài khoản → kiểm tra email xác thực đến được.

Đặt cho gọn (thêm vào `~/.bashrc`):

```bash
alias fem='docker compose -f /opt/family-expense-manager/infra/docker-compose.prod.yml --env-file /opt/family-expense-manager/infra/.env.prod'
# fem ps · fem logs -f expense-service · fem up -d --build · fem restart notification-service
```

## 6. Backup MySQL

```bash
/opt/family-expense-manager/infra/backup-mysql.sh      # chạy thử một lần
ls -lh /var/backups/fem/

crontab -e
# thêm dòng:
0 3 * * * /opt/family-expense-manager/infra/backup-mysql.sh >> /var/log/fem-backup.log 2>&1
```

Giữ 14 ngày gần nhất (`KEEP_DAYS`). **Chép backup ra khỏi VPS** (rclone lên Google Drive/S3, hoặc rsync về máy khác).

Khôi phục:

```bash
gunzip -c /var/backups/fem/fem-YYYYMMDD-HHMMSS.sql.gz \
  | docker exec -i -e MYSQL_PWD='<MYSQL_ROOT_PASSWORD>' fem-mysql-db mysql -uroot
```

Ảnh hoá đơn nằm trong volume `family-expense-manager_receipt-uploads` — sao lưu thêm nếu cần:
`docker run --rm -v family-expense-manager_receipt-uploads:/d -v /var/backups/fem:/b alpine tar czf /b/receipts.tgz -C /d .`

## 7. Đăng nhập Google (tuỳ chọn)

Google Cloud Console → APIs & Services → Credentials → OAuth client:
- **Authorized JavaScript origins:** `https://<DOMAIN>`
- **Authorized redirect URIs:** `https://<DOMAIN>/api/auth/login/oauth2/code/google`

Điền `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` vào `.env.prod` rồi `fem up -d auth-service`.
Thử đăng nhập Google ngay sau khi deploy: nếu Google báo `redirect_uri_mismatch`, so URI trong thông báo lỗi với URI đã khai báo.

## 8. Cập nhật phiên bản mới

```bash
cd /opt/family-expense-manager
/opt/family-expense-manager/infra/backup-mysql.sh   # luôn backup trước
git pull
fem up -d --build          # Flyway tự chạy migration mới khi service khởi động
docker image prune -f
```

## 9. Monitoring (tuỳ chọn)

```bash
fem --profile monitoring up -d
# Grafana chỉ nghe trên 127.0.0.1 của VPS — mở qua SSH tunnel từ máy của bạn:
ssh -L 3001:127.0.0.1:3001 root@<IP-VPS>
# rồi mở http://localhost:3001 (admin / GRAFANA_ADMIN_PASSWORD)
```

Prometheus + Loki + Grafana tốn thêm ~1 GB RAM — chỉ bật khi VPS có từ 6 GB.

## Giới hạn RAM đã đặt

| Container | `mem_limit` |
|---|---|
| mysql-db, kafka | 768m |
| auth-service, expense-service | 640m |
| api-gateway, notification-service | 512m |
| eureka-server | 384m |
| redis | 128m |
| frontend | 64m |

Tổng ~4.2 GB ở mức tối đa (thường dùng ít hơn). Mỗi JVM lấy heap = 75% giới hạn của nó (`-XX:MaxRAMPercentage=75`).
Nếu `docker stats` cho thấy một service sát giới hạn hoặc bị restart vì OOM, tăng `mem_limit` của service đó.

## Xử lý sự cố

| Triệu chứng | Kiểm tra |
|---|---|
| `init-letsencrypt.sh` báo lỗi | DNS đã trỏ đúng IP chưa (`dig +short <DOMAIN>`), cổng 80 có mở ở UFW và ở firewall của nhà cung cấp VPS không, có tiến trình nào khác đang chiếm cổng 80 không (`ss -ltnp | grep :80`) |
| `fem-nginx` restart liên tục | `docker logs fem-nginx` — thường là chưa có chứng chỉ (chạy bước 4) hoặc sai `DOMAIN` |
| Kiểm tra gia hạn | `fem run --rm --entrypoint certbot certbot renew --dry-run --webroot -w /var/www/certbot` |
| Trang trắng / lỗi CORS | Frontend phải được build với đúng `DOMAIN`: `fem build --no-cache frontend && fem up -d frontend` |
| Không gửi được email | `docker logs fem-notification-service | grep -i mail`; nhiều VPS chặn cổng 25 — dùng 587 |
| Service restart liên tục | `docker logs <container>`; `docker stats` để xem có hết RAM không |
| Đổi `.env.prod` không có tác dụng | Phải `fem up -d` lại (restart không đọc lại biến môi trường); riêng `DOMAIN` cần build lại frontend |
