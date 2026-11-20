# PVC-Hub

Hộp thư hợp nhất cho Android, lấy cảm hứng từ BlackBerry Hub. Chạy trên Android 9 trở lên. Cài trực tiếp bằng file APK, không cần Google Play.

## Tính năng (bản 0.11)

- **Máy chủ riêng cho email (0.11)**: Thiết lập, bước 1, bật "Máy chủ riêng" để thêm email công ty hoặc hosting (IMAP SSL, SMTP 465 hoặc 587, tên đăng nhập riêng nếu cần).
- **Kiểm tra bản mới (0.11)**: đặt địa chỉ tệp JSON (mẫu ở docs/version.json); app kiểm tra mỗi ngày và báo trong hộp thư, bấm Tải về để lấy APK.
- **Tìm kiếm toàn Hub (0.11)**: tìm trong mọi nguồn, lọc chưa đọc, hôm nay, 7 ngày, 30 ngày.

## Tính năng (bản 0.10)

- **Dọn thư thật trên Gmail (0.10)**: menu ba chấm của email có Lưu trữ, Xóa (vào Thùng rác), Báo spam, thực hiện trên máy chủ thư. Mục Đã hoãn có nút "Ẩn và lưu trữ email trên Gmail".
- **Tự dọn tin cũ (0.10)**: giữ 7, 30 hoặc 90 ngày; tin cũ hơn tự ẩn khỏi Hub (không xóa trên máy chủ).
- **Soạn thư (0.10)**: chữ ký tự thêm, tự lưu nháp thư mới, đính kèm tệp khi gửi (tổng tối đa 20 MB).
- **Đọc thư HTML (0.10)**: nút "Bản đầy đủ" hiển thị thư có định dạng; hình từ internet bị chặn mặc định.

## Tính năng (bản 0.9)

- **Giao diện xanh #00A479 và menu mới (0.9)**: Tất cả, Ưu tiên ★, Đã hoãn; Email theo từng tài khoản; Điện thoại; Chat; Mạng xã hội. Dải tóm tắt (chưa đọc, ưu tiên, đã hoãn) ở đầu danh sách.
- **Sao chép mã OTP (0.9)**: tin có mã 4 đến 8 số hiện nút "Sao chép mã".
- **Vuốt (0.9.3)**: vuốt phải = đã đọc, vuốt trái = ẩn khỏi Hub (thư gốc không bị xóa, khôi phục trong Thiết lập, thẻ Quy tắc lọc). Hoãn nằm ở menu ba chấm; mục Đã hoãn có nút "Bỏ hoãn tất cả" và "Ẩn tất cả"; nhắc hoãn gộp thành một thông báo. Tin chat/SMS cùng người gửi trong ngày gộp một dòng.
- **Sao/VIP (0.9)**: gắn sao người gửi ở menu ba chấm; có thể chỉ báo thông báo thư của người gắn sao.
- **Giờ yên tĩnh (0.9)**: không hiện thông báo thư mới trong khung giờ chọn.
- **Đã hoãn (0.9)**: danh sách tin đang hoãn, bỏ hoãn được, tới giờ có thông báo nhắc.
- **Khóa ứng dụng (0.9)**: vân tay/khuôn mặt/khóa màn hình; ẩn nội dung ở widget, Peek, thông báo.
- **Quy tắc lọc và tệp đính kèm (0.9)**: từ khóa gắn sao hoặc ẩn; đọc thư thấy danh sách tệp đính kèm, chạm để tải và mở.

- **Trả lời nhanh tin chat (0.8)**: dấu ba chấm trên tin Zalo, Messenger, Telegram... chọn "Trả lời nhanh" (chỉ hiện khi thông báo gốc còn và có ô trả lời).
- **Gửi SMS (0.8)**: dấu ba chấm trên tin SMS chọn "Trả lời SMS". Cần cấp quyền gửi SMS.
- **Peek (0.8)**: tay cầm nổi ở mép màn hình, chạm xem nhanh tin chưa đọc. Bật trong Thiết lập, thẻ "Peek". Cần quyền hiển thị trên ứng dụng khác.

## Tính năng (bản 0.7)

- **Widget màn hình chính (0.7)**: giữ ngón tay trên màn hình chính, chọn Widget, tìm PVC-Hub. Hiện số tin chưa đọc và 5 tin mới nhất, chạm để mở app.
- **Ô Quick Settings (0.7)**: kéo bảng cài đặt nhanh xuống, bấm biểu tượng bút chì để sửa, kéo ô PVC-Hub vào. Ô hiện số tin chưa đọc.
- **Sao lưu và khôi phục (0.7)**: Thiết lập, thẻ "Sao lưu và khôi phục". Xuất toàn bộ tin ra file JSON và nhập lại. Không chứa mật khẩu email.

## Tính năng (bản 0.6)


- **Đọc thư ngay trong Hub (0.6.1)**: chạm vào một email để đọc nội dung, người gửi, người nhận, ngày giờ trong Hub (không nhảy sang Gmail); có nút Trả lời và Mở bằng Gmail. Nội dung tải về khi mở, thư được đánh dấu đã đọc trên máy chủ.

- **Chọn tần suất nhận email (mới ở 0.6)**: Tức thì, 5, 10, 15, 30 phút, 1 giờ, 2 giờ hoặc Thủ công; chọn số thư lấy mỗi lần (50/100/200); chỉ đồng bộ khi có Wi-Fi; báo thông báo khi có thư mới. Tức thì, 5 và 10 phút cần thông báo cố định (Android bắt buộc).
- **Soạn và trả lời email (mới ở 0.6)**: nút bút chì ở màn hình chính, hoặc dấu ba chấm trên một thư rồi chọn Trả lời. Gửi qua SMTP bằng chính Mật khẩu ứng dụng; chọn gửi từ tài khoản nào.
- **Snooze (mới ở 0.6)**: dấu ba chấm trên mọi tin, hoãn 1 giờ, 3 giờ, tối nay, sáng mai hoặc thứ Hai tuần sau; tới giờ tin tự hiện lại như tin chưa đọc.
- **Khóa ký cố định (mới ở 0.6)**: từ bản 0.6 mọi bản build đều cùng chữ ký, nên cài đè được mà không mất tài khoản và cài đặt. Lần chuyển sang 0.6 vẫn phải gỡ bản cũ một lần.

## Tính năng (bản 0.5)

- **Nhiều tài khoản email (mới ở 0.5)**: thêm 2, 3 hay nhiều hơn tài khoản Gmail (cả Yahoo, iCloud) trong bước 1 của Thiết lập. Mỗi thư hiện nhãn địa chỉ tài khoản. Tài khoản đang dùng từ bản cũ tự được giữ lại.

- Gom Gmail, SMS, cuộc gọi nhỡ và thông báo Zalo, Telegram, Messenger, WhatsApp, Viber, Signal, LINE về một danh sách theo thời gian, chia theo ngày.
- **Mạng xã hội (mới ở 0.4)**: nhận thông báo Facebook, Instagram, Threads, TikTok, X (Twitter), LinkedIn, trong nhóm "Mạng xã hội" của menu. Có công tắc bật/tắt riêng từng app trong Thiết lập.
- **Menu ngăn kéo**: vuốt từ mép trái hoặc bấm biểu tượng ba gạch để chọn nguồn (Tất cả, Email, SMS, Cuộc gọi nhỡ, từng app chat) kèm số tin chưa đọc. App chat cài thêm chỉ hiện trong menu khi đã có tin.
- **Nhận tin từ app chat cài về sau**: tự nhận ứng dụng lạ đã tắt mặc định vì lý do riêng tư. Người dùng có thể chủ động bật trong Thiết lập hoặc tự chọn thêm ứng dụng.
- **Nhật ký thông báo** trong Thiết lập để biết chính xác app chat có gửi thông báo cho Hub hay không (dùng khi Zalo chưa lên tin).
- Tìm kiếm, đánh dấu đã đọc, chạm vào mục để mở app gốc.
- Màn hình **Thiết lập và hướng dẫn** có 5 bước, mỗi bước tự hiện dấu tích khi xong, kèm bảng tình trạng đồng bộ và mục khắc phục sự cố.
- Lần đầu mở app sẽ vào thẳng màn hình hướng dẫn.
- Dữ liệu chỉ nằm trên máy, mật khẩu Gmail mã hóa bằng Android Keystore.

## Lấy file APK

- Workflow CI chỉ tạo bản debug để kiểm tra; bản debug không được phát hành cho người dùng.
- APK Release chỉ được tạo khi repository đã cấu hình đầy đủ GitHub Actions Secrets: `PVC_RELEASE_KEYSTORE_BASE64`, `PVC_RELEASE_STORE_PASSWORD`, `PVC_RELEASE_KEY_ALIAS` và `PVC_RELEASE_KEY_PASSWORD`.
- Artifact phát hành có tên `PVC-Hub-release-*` và chỉ được tải sau khi kiểm tra chữ ký APK thành công.
- Không đưa keystore, mật khẩu ký hoặc APK ký bằng khóa riêng vào Git repository.

**Cảnh báo chuyển tiếp:** khóa ký cũ từng nằm trong mã nguồn và đã bị xem là bị lộ. Không dùng lại khóa đó để phát hành bản mới. Tuy nhiên, thay khóa ký ngay có thể khiến Android từ chối cập nhật cài đè trên thiết bị hiện tại. Trước khi phát hành bản ký bằng khóa mới, chủ dự án phải lập kế hoạch chuyển khóa có hỗ trợ Android (signing lineage) hoặc phương án chuyển đổi/cài đặt lại phù hợp. Không xóa bản ứng dụng cũ khỏi thiết bị người dùng trước khi có phương án tương thích.

## Thiết lập trên điện thoại

Mở PVC-Hub, làm lần lượt 5 bước trong màn hình hướng dẫn (vào lại bất kỳ lúc nào bằng biểu tượng bánh răng):

1. **Kết nối Gmail**: bật xác minh 2 bước, tạo Mật khẩu ứng dụng tại https://myaccount.google.com/apppasswords, dán 16 ký tự vào app, bấm Lưu và đồng bộ.
2. **Quyền đọc SMS**.
3. **Quyền đọc cuộc gọi nhỡ**.
4. **Quyền đọc thông báo** cho PVC-Hub. Android 13 trở lên có thể làm mờ công tắc với app cài ngoài Play: vào Thông tin ứng dụng, dấu ba chấm, chọn **Cho phép cài đặt bị hạn chế**, rồi quay lại bật.
5. **Cho phép chạy nền** (không bị tiết kiệm pin chặn). Tùy hãng máy (Samsung, Xiaomi, Oppo, Vivo) có thêm bước, app đã ghi sẵn.

## Giới hạn đã biết

- Zalo, Telegram, Messenger: chỉ thấy tin đến **sau khi** bật quyền, chỉ phần chữ hiện trong thông báo, không có lịch sử cũ. Thông báo trong chính các app đó phải đang bật và hiện nội dung.
- Gmail: lấy 50 thư mới nhất của INBOX, đồng bộ mỗi 15 phút và mỗi lần mở app hoặc bấm Làm mới.
- Chưa có: lịch và danh bạ. Những phần này nằm ở Giai đoạn 2 và 3 trong `docs/DAC-TA.md`.
