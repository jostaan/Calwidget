# Đồng hồ · Lịch · Thời tiết (widget cho Redmi K70 / HyperOS 3)

Gói `vn.xin.clockwidget`. Hai phần hiển thị:

1. **Widget màn hình chính** (4×2, đổi kích thước được), **2 cột**: cột trái là đồng hồ chạy thật (TextClock), ngày dương, âm lịch + can chi, ngày lễ / Rằm / mùng 1 / tiết khí; cột phải là thời tiết Quận 11 (nhiệt độ, cảm giác, cao/thấp, xác suất mưa, độ ẩm, UV, AQI). Lề trong chỉnh 0–24 dp (mặc định 6), bo góc 4 mức. Lề do launcher HyperOS đặt quanh widget thì app không đổi được.
2. **Thẻ màn hình khóa** (tùy chọn): thông báo ghim yên lặng, cùng nội dung, đồng hồ chạy thật.

## Giới hạn cần biết

- **Widget của app bên thứ ba không lên được màn hình khóa HyperOS 3.** Một bài báo ngày 12/01/2026 (touchit.sk) xác nhận Android 16 có widget màn hình khóa nhưng điện thoại Xiaomi/Redmi/POCO chưa có; HyperOS 4 mới là dự kiến, chưa được xác nhận. Vì vậy dùng thẻ thông báo thay thế. Giờ/ngày gốc của màn hình khóa đổi font và kiểu trong Cài đặt → Màn hình khóa (do hệ thống kiểm soát, app không can thiệp được).
- Thẻ khóa phụ thuộc cài đặt hiện thông báo trên màn hình khóa. Từ Android 14 bạn vuốt bỏ được thẻ; thẻ hiện lại ở lần cập nhật kế tiếp.
- Font: 11 họ Google Fonts (OFL) đóng sẵn trong app, đủ dấu tiếng Việt. Thêm font mới cần build lại. App không tải font lúc chạy.
- Thời tiết: Open-Meteo (không cần khóa, chỉ dùng phi thương mại). AQI là mô hình ~45 km, không phải số liệu trạm đo.
- Nếu app báo lỗi hoặc văng: mở lại app, mục đầu trang hiện "Lỗi gần nhất" kèm nút **Sao chép lỗi**. Cuối trang có mục **Chẩn đoán** (job nền, báo thức, pin, thời tiết, lỗi mạng) kèm nút **Sao chép báo cáo**. Gửi nội dung đó để sửa chính xác. Đừng xóa dữ liệu app trước khi sao chép, vì thao tác đó xóa cả bản ghi lỗi.
- Chưa chạy thử trên máy thật. Mã Kotlin đã được kiểm tra kiểu với Android API 33; tài nguyên và Gradle chỉ được xác nhận khi chạy workflow.

## Build bằng điện thoại (GitHub Actions)

Dùng chung repo đã có, không tạo repo mới.

1. Vào repo → **Add file → Upload files**, tải lên 2 tệp:
   - `lockscreen-widget.zip` (thư mục gốc repo)
   - `.github/workflows/build-widget.yml` (nếu giao diện không cho tải thư mục, chọn **Create new file**, gõ tên `.github/workflows/build-widget.yml` rồi dán nội dung)
2. Tab **Actions → Build Clock Widget APK → Run workflow**.
3. Xong: tab **Releases** → bản `widget-N` → tải `ClockWidget.apk` và cài.

Cập nhật sau này: chỉ tải đè `lockscreen-widget.zip`, chạy lại workflow. Cùng chữ ký nên cài đè không mất cấu hình.

## Cài đặt trên HyperOS để chạy ổn định

- Cài đặt → Ứng dụng → Đồng hồ Lịch Thời tiết → **Tự khởi động**: Bật.
- Tiết kiệm pin: **Không hạn chế**.
- Khóa app trong màn hình đa nhiệm (nhấn giữ thẻ app → biểu tượng khóa).
- Thẻ khóa: Cài đặt → Thông báo → Màn hình khóa → hiện nội dung; kênh "Thẻ màn hình khóa" để bật.
- Trong app có nút mở sẵn các màn hình trên.

## Cơ chế cập nhật

- Đồng hồ: TextClock tự chạy, không tốn pin cập nhật.
- Thời tiết: ba đường độc lập, đường nào chạy cũng đủ: (1) JobScheduler ~30 phút, (2) báo thức gần đúng ~30 phút tự đặt lại, (3) lệnh cập nhật định kỳ của widget (30 phút). Dữ liệu lưu sẵn, mất mạng vẫn hiển thị (ghi chú giờ cập nhật nếu cũ hơn 90 phút). Nếu chưa lấy được, cột phải ghi rõ lý do (ví dụ HTTP 403, UnknownHostException).
- Mọi bước lập lịch và vẽ đều bọc try/catch riêng và ghi lỗi vào bản ghi trong app; vẽ widget luôn chạy trước bước lập lịch.
- Ngày/âm lịch: báo thức sau 00:00 mỗi ngày, cộng thêm khi đổi giờ, múi giờ, khởi động lại.
- Không dùng foreground service.

## Cấu trúc

- `LunarCalendar.java`: âm lịch (thuật toán Hồ Ngọc Đức, UTC+7), can chi, 24 tiết khí.
- `VnText.java`: thứ, mô tả thời tiết, UV/AQI, ngày lễ.
- `WeatherRepo.kt`, `Renderer.kt`, `Updater.kt`, `LockCard.kt`, `Receivers.kt`, `MainActivity.kt`.
- `res/font`: TTF; `res/layout/w_*` (widget), `n_*` (thẻ khóa) sinh theo từng font vì RemoteViews không đổi font lúc chạy.
