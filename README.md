# Đồng hồ · Lịch · Thời tiết (widget cho Redmi K70 / HyperOS 3)

Gói `vn.xin.clockwidget`. Hai phần hiển thị:

1. **Widget màn hình chính** (4×2, đổi kích thước được): đồng hồ chạy thật (TextClock), ngày dương, âm lịch + can chi, ngày lễ / Rằm / mùng 1 / tiết khí, thời tiết Quận 11 (nhiệt độ, cảm giác, cao/thấp, xác suất mưa, độ ẩm, UV, AQI).
2. **Thẻ màn hình khóa** (tùy chọn): thông báo ghim yên lặng, cùng nội dung, đồng hồ chạy thật.

## Giới hạn cần biết

- **Widget của app bên thứ ba không lên được màn hình khóa HyperOS.** Android gốc chỉ cho widget màn hình khóa ở chế độ "hub" khi sạc/dock, và HyperOS không công bố hỗ trợ. Vì vậy dùng thẻ thông báo thay thế. Giờ/ngày gốc của màn hình khóa đổi font và kiểu trong Cài đặt → Màn hình khóa (do hệ thống kiểm soát, app không can thiệp được).
- Thẻ khóa phụ thuộc cài đặt hiện thông báo trên màn hình khóa. Từ Android 14 bạn vuốt bỏ được thẻ; thẻ hiện lại ở lần cập nhật kế tiếp.
- Font: 11 họ Google Fonts (OFL) đóng sẵn trong app, đủ dấu tiếng Việt. Thêm font mới cần build lại. App không tải font lúc chạy.
- Thời tiết: Open-Meteo (không cần khóa, chỉ dùng phi thương mại). AQI là mô hình ~45 km, không phải số liệu trạm đo.
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
- Thời tiết: JobScheduler ~30 phút, cần mạng; dữ liệu lưu sẵn, mất mạng vẫn hiển thị (ghi chú giờ cập nhật nếu cũ hơn 90 phút).
- Ngày/âm lịch: báo thức sau 00:00 mỗi ngày, cộng thêm khi đổi giờ, múi giờ, khởi động lại.
- Không dùng foreground service.

## Cấu trúc

- `LunarCalendar.java`: âm lịch (thuật toán Hồ Ngọc Đức, UTC+7), can chi, 24 tiết khí.
- `VnText.java`: thứ, mô tả thời tiết, UV/AQI, ngày lễ.
- `WeatherRepo.kt`, `Renderer.kt`, `Updater.kt`, `LockCard.kt`, `Receivers.kt`, `MainActivity.kt`.
- `res/font`: TTF; `res/layout/w_*` (widget), `n_*` (thẻ khóa) sinh theo từng font vì RemoteViews không đổi font lúc chạy.
