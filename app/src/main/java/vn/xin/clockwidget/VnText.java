package vn.xin.clockwidget;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Chữ tiếng Việt: thứ, mô tả thời tiết (mã WMO của Open-Meteo), ngày lễ. Không phụ thuộc Android. */
public final class VnText {
    private VnText() {}

    public static String weekday(DayOfWeek d) {
        switch (d) {
            case MONDAY: return "Thứ Hai";
            case TUESDAY: return "Thứ Ba";
            case WEDNESDAY: return "Thứ Tư";
            case THURSDAY: return "Thứ Năm";
            case FRIDAY: return "Thứ Sáu";
            case SATURDAY: return "Thứ Bảy";
            default: return "Chủ Nhật";
        }
    }

    /** Mô tả theo mã thời tiết WMO. */
    public static String weatherDesc(int code) {
        switch (code) {
            case 0: return "Trời quang";
            case 1: return "Ít mây";
            case 2: return "Mây rải rác";
            case 3: return "Nhiều mây";
            case 45:
            case 48: return "Sương mù";
            case 51: return "Mưa phùn nhẹ";
            case 53: return "Mưa phùn";
            case 55: return "Mưa phùn dày";
            case 56:
            case 57: return "Mưa phùn lạnh";
            case 61: return "Mưa nhẹ";
            case 63: return "Mưa vừa";
            case 65: return "Mưa to";
            case 66:
            case 67: return "Mưa lạnh";
            case 71:
            case 73:
            case 75:
            case 77: return "Tuyết";
            case 80: return "Mưa rào nhẹ";
            case 81: return "Mưa rào";
            case 82: return "Mưa rào lớn";
            case 85:
            case 86: return "Tuyết rào";
            case 95: return "Dông";
            case 96:
            case 99: return "Dông kèm mưa đá";
            default: return "Không rõ";
        }
    }

    /** Biểu tượng emoji (hệ thống tự vẽ). */
    public static String weatherIcon(int code, boolean isDay) {
        switch (code) {
            case 0: return isDay ? "☀️" : "🌙";
            case 1: return isDay ? "🌤️" : "🌙";
            case 2: return isDay ? "⛅" : "☁️";
            case 3: return "☁️";
            case 45:
            case 48: return "🌫️";
            case 51:
            case 53:
            case 55:
            case 56:
            case 57: return "🌦️";
            case 61:
            case 63:
            case 65:
            case 66:
            case 67:
            case 80:
            case 81:
            case 82: return "🌧️";
            case 71:
            case 73:
            case 75:
            case 77:
            case 85:
            case 86: return "❄️";
            case 95:
            case 96:
            case 99: return "⛈️";
            default: return "🌡️";
        }
    }

    public static String uvLabel(double uv) {
        if (uv < 3) return "thấp";
        if (uv < 6) return "trung bình";
        if (uv < 8) return "cao";
        if (uv < 11) return "rất cao";
        return "cực cao";
    }

    /** Nhãn theo thang AQI của Mỹ (Open-Meteo trả us_aqi). */
    public static String aqiLabel(int aqi) {
        if (aqi <= 50) return "tốt";
        if (aqi <= 100) return "trung bình";
        if (aqi <= 150) return "kém";
        if (aqi <= 200) return "xấu";
        if (aqi <= 300) return "rất xấu";
        return "nguy hại";
    }

    /** Tên ngày lễ và sự kiện của ngày (dương lịch và âm lịch). Danh sách rỗng nếu không có. */
    public static List<String> holidays(LocalDate date) {
        List<String> out = new ArrayList<>();
        int d = date.getDayOfMonth();
        int m = date.getMonthValue();

        if (d == 1 && m == 1) out.add("Tết Dương lịch");
        if (d == 22 && m == 2) out.add("Ngày Tư duy Hướng đạo");
        if (d == 8 && m == 3) out.add("Quốc tế Phụ nữ");
        if (d == 30 && m == 4) out.add("Giải phóng miền Nam");
        if (d == 1 && m == 5) out.add("Quốc tế Lao động");
        if (d == 1 && m == 6) out.add("Quốc tế Thiếu nhi");
        if (d == 2 && m == 9) out.add("Quốc khánh");
        if (d == 20 && m == 10) out.add("Ngày Phụ nữ Việt Nam");
        if (d == 20 && m == 11) out.add("Ngày Nhà giáo Việt Nam");
        if (d == 22 && m == 12) out.add("Ngày thành lập Quân đội nhân dân");
        if (d == 24 && m == 12) out.add("Đêm Giáng sinh");
        if (d == 25 && m == 12) out.add("Giáng sinh");

        LunarCalendar.Lunar l = LunarCalendar.fromSolar(d, m, date.getYear());
        LocalDate next = date.plusDays(1);
        LunarCalendar.Lunar ln = LunarCalendar.fromSolar(next.getDayOfMonth(), next.getMonthValue(), next.getYear());

        if (ln.day == 1 && ln.month == 1 && !ln.leap) {
            out.add("Giao thừa");
        }
        if (!l.leap) {
            boolean named = true;
            if (l.month == 1 && l.day == 1) out.add("Tết Nguyên Đán, mùng 1");
            else if (l.month == 1 && l.day == 2) out.add("Tết Nguyên Đán, mùng 2");
            else if (l.month == 1 && l.day == 3) out.add("Tết Nguyên Đán, mùng 3");
            else if (l.month == 1 && l.day == 15) out.add("Rằm tháng Giêng");
            else if (l.month == 3 && l.day == 3) out.add("Tết Hàn thực");
            else if (l.month == 3 && l.day == 10) out.add("Giỗ Tổ Hùng Vương");
            else if (l.month == 4 && l.day == 15) out.add("Lễ Phật Đản");
            else if (l.month == 5 && l.day == 5) out.add("Tết Đoan Ngọ");
            else if (l.month == 7 && l.day == 15) out.add("Lễ Vu Lan");
            else if (l.month == 8 && l.day == 15) out.add("Tết Trung thu");
            else if (l.month == 12 && l.day == 23) out.add("Ông Công, Ông Táo");
            else named = false;

            if (!named) {
                if (l.day == 1) out.add("Mùng 1 âm lịch");
                else if (l.day == 15) out.add("Rằm");
            }
        } else if (l.day == 1) {
            out.add("Mùng 1 âm lịch (tháng nhuận)");
        } else if (l.day == 15) {
            out.add("Rằm (tháng nhuận)");
        }
        return out;
    }
}
