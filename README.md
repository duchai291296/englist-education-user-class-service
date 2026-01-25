# TÀI LIỆU THIẾT KẾ AUTHENTICATION (REGISTER → LOGIN)

## 1. Mục tiêu thiết kế

Hệ thống authentication được thiết kế với các mục tiêu sau:

* DB là **source of truth** (nguồn dữ liệu chuẩn duy nhất)
* Redis chỉ đóng vai trò **cache trạng thái auth**
* Tránh **race condition** giữa login và admin lock user
* Không để login tự ý rebuild Redis gây sai trạng thái
* Đảm bảo **transaction safety** (Redis chỉ được ghi sau khi DB commit)

---

## 2. Các thành phần chính

### 2.1 Database (MySQL / PostgreSQL)

Bảng `users` chứa các thông tin quan trọng cho auth:

* `id`
* `username`
* `password`
* `status` (ACTIVE / INACTIVE)
* `token_version`

👉 DB luôn là nguồn dữ liệu chính xác nhất.

---

### 2.2 Redis (Auth Cache)

Redis lưu **trạng thái auth runtime** để kiểm tra nhanh khi login và verify token.

Các key sử dụng:

* `token_ver:{userId}` → phiên bản token hiện tại
* `user_locked:{userId}` → trạng thái khóa user

👉 Redis **không được coi là nguồn dữ liệu chuẩn**, chỉ là cache.

---

### 2.3 JWT

* Access Token chứa:

    * `username`
    * `tokenVersion`
* Dùng để authenticate request
* Bị vô hiệu hóa khi:

    * user bị khóa
    * tokenVersion trong Redis thay đổi

---

## 3. Event-driven Architecture

### 3.1 UserCreatedEvent

```java
public record UserCreatedEvent(Long userId) {}
```

Ý nghĩa:

* Là **internal event** trong Spring
* Thông báo rằng: *User đã được tạo thành công trong DB*
* Không chứa logic, chỉ chứa dữ liệu cần thiết

---

### 3.2 UserAuthCacheListener

Listener lắng nghe `UserCreatedEvent` và **chỉ chạy sau khi DB commit**:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
```

Nhiệm vụ:

* Set Redis auth cache ban đầu cho user mới
* Đảm bảo Redis không bị ghi nếu DB rollback

---

## 4. Flow REGISTER

### 4.1 Các bước xử lý

1. Client gửi request register
2. Backend validate dữ liệu
3. Lưu user vào DB trong transaction
4. Publish `UserCreatedEvent`
5. Transaction commit thành công
6. Listener bắt event và set Redis

---

### 4.2 Pseudo Flow

```
Client → /register
   → validate input
   → save user (DB)
   → COMMIT
   → publish UserCreatedEvent
   → Redis set token_ver & user_locked
```

---

### 4.3 Vì sao KHÔNG set Redis trực tiếp trong register?

* Nếu DB rollback → Redis bị sai
* Dễ tạo race condition
* Vi phạm nguyên tắc source of truth

👉 Chỉ set Redis **sau commit** thông qua event.

---

## 5. Flow LOGIN

### 5.1 Các bước xử lý

1. Client gửi username + password
2. Backend verify user trong DB
3. Kiểm tra password
4. Kiểm tra auth state trong Redis
5. Sinh JWT nếu hợp lệ

---

### 5.2 Hàm `validateAuthState`

```java
String tokenVer = ops.get(tokenVerKey);
String locked   = ops.get(lockedKey);
```

#### Các case xử lý:

**Case 1: Redis miss (1 hoặc 2 key null)**

* Hệ thống auth cache chưa sẵn sàng
* Không được tự rebuild Redis
* Throw exception yêu cầu retry

**Case 2: User bị khóa (INACTIVE)**

* Reject login ngay lập tức

**Case 3: Hợp lệ**

* Trả về `tokenVersion`

---

### 5.3 Vì sao login KHÔNG rebuild Redis?

* Login không phải hành động có thẩm quyền
* Dễ ghi đè trạng thái lock của admin
* Redis có thể bị stale hoặc reset

👉 Login chỉ **đọc & validate**, không ghi.

---

## 6. Xử lý Redis reset / cache miss

Khi Redis bị reset:

* Login tạm thời fail với message retry
* Warm-up job hoặc admin action rebuild Redis
* Client retry login

👉 Đây là **trade-off an toàn** cho hệ thống auth.

---

## 7. Race condition & cách đã tránh

### 7.1 Race nguy hiểm (đã loại bỏ)

* Admin lock user
* Login rebuild Redis
* Redis bị ghi đè sai trạng thái

---

### 7.2 Thiết kế hiện tại

| Hành động  | Có ghi Redis không |
| ---------- | ------------------ |
| Register   | Có (AFTER_COMMIT)  |
| Admin lock | Có                 |
| Login      | KHÔNG              |

---

## 8. Tổng kết

Thiết kế authentication hiện tại đảm bảo:

* An toàn transaction
* Không race condition
* Redis không bị ghi sai
* Dễ mở rộng & bảo trì
* Phù hợp production

👉 Đây là kiến trúc auth chuẩn cho hệ thống Spring Boot + JWT + Redis.
