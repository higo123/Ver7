# PVC-Hub: đặc tả

## Mục tiêu
Một ứng dụng Android gom các luồng liên lạc (email, SMS, cuộc gọi nhỡ, tin nhắn Zalo/Telegram/Messenger) về một hộp thư hợp nhất theo thời gian, theo triết lý của BlackBerry Hub: tập trung thông tin, giảm việc chuyển qua lại giữa app.

## Phạm vi thiết bị
Android 9 (API 28) trở lên. Cài trực tiếp bằng APK, không phát hành trên Google Play ở giai đoạn đầu (tránh chính sách hạn chế quyền SMS và Call Log của Play).

## Nguồn dữ liệu

| Nguồn | Cách lấy | Quyền |
|---|---|---|
| Gmail | IMAP `imap.gmail.com:993` + Mật khẩu ứng dụng | INTERNET |
| SMS | ContentProvider `content://sms/inbox` | READ_SMS |
| Cuộc gọi nhỡ | `CallLog.Calls` (type = 3) | READ_CALL_LOG |
| Zalo, Telegram, Messenger | `NotificationListenerService` đọc thông báo | Quyền truy cập thông báo |

## Mô hình dữ liệu
Một bảng Room `hub_item`: `id`, `key` (duy nhất, chống trùng), `source`, `account`, `sender`, `preview`, `time`, `read`, `packageName`.
Chèn bằng `IGNORE` để không ghi đè trạng thái đã đọc. Riêng Gmail, thư đã đọc trên Gmail được đánh dấu đã đọc trong Hub.

## Màn hình
- **Hộp thư hợp nhất**: tìm kiếm, chip lọc theo nguồn, danh sách theo thời gian (đậm = chưa đọc), nút "Đã đọc hết", chạm để mở app gốc.
- **Cài đặt**: tài khoản Gmail, trạng thái và nút bật từng quyền, hướng dẫn "cài đặt bị hạn chế" và pin.

## Đồng bộ
WorkManager chạy định kỳ 15 phút (khi có mạng) và mỗi lần mở app. Thông báo chat được ghi ngay khi đến.

## Bảo mật
Địa chỉ Gmail và Mật khẩu ứng dụng lưu bằng EncryptedSharedPreferences (Android Keystore). Dữ liệu chỉ nằm trên máy, không có server.

## Lộ trình
- **Giai đoạn 1** (đã có mã): hộp thư hợp nhất, lọc, tìm kiếm, đã đọc.
- **Giai đoạn 2**: soạn/trả lời/chuyển tiếp email, gửi SMS, trả lời nhanh từ thông báo (RemoteInput), lịch, danh bạ, Snooze, IMAP IDLE bằng foreground service.
- **Bản 0.11**: email máy chủ riêng (IMAP/SMTP tùy chỉnh), kiểm tra bản mới qua tệp JSON, tìm kiếm toàn Hub có bộ lọc.
- **Bản 0.10**: dọn thư thật trên Gmail (IMAP: lưu trữ/xóa/spam), tự dọn tin cũ, chữ ký/nháp/đính kèm khi gửi, xem thư HTML (WebView chặn hình). Đăng nhập Google (OAuth) hoãn tới khi có người dùng thật.
- **Bản 0.9**: giao diện xanh #00A479, menu nhóm lại, OTP, vuốt, gộp tin cùng người gửi, sao/VIP, giờ yên tĩnh, danh sách Đã hoãn + nhắc, khóa sinh trắc, quy tắc lọc, tệp đính kèm email.
- **Giai đoạn 3** (đã làm ở 0.7 và 0.8: widget, ô Quick Settings, sao lưu/khôi phục, Peek): Peek (tay cầm overlay ở mép màn hình), widget, ô Quick Settings, sao lưu/xuất dữ liệu.

## Rủi ro
- Không có API chính thức cho Zalo/Messenger/Telegram; việc đọc thông báo phụ thuộc cách mỗi app hiển thị thông báo và có thể đổi theo bản cập nhật.
- Hãng máy (Xiaomi, Oppo, Samsung) có thể giết service nền; cần hướng dẫn tắt tối ưu pin.
- Từ Android 13, quyền đọc thông báo của app cài ngoài Play cần bước "Cho phép cài đặt bị hạn chế".
- Cử chỉ Peek trên Android bị tranh chấp với cử chỉ điều hướng của hệ thống; không thể giống hệt BB10.
