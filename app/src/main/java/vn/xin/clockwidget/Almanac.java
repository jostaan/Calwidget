package vn.xin.clockwidget;

/**
 * Ngày và giờ hoàng đạo theo lịch vạn niên Việt Nam.
 * Ngày: 12 thần bắt đầu từ Thanh Long, khởi ở chi ngày phụ thuộc số tháng âm lịch
 * (tháng 1 và 7: Tý; 2 và 8: Dần; 3 và 9: Thìn; 4 và 10: Ngọ; 5 và 11: Thân; 6 và 12: Tuất),
 * rồi lần lượt theo thứ tự 12 chi. Giờ: bảng 6 giờ hoàng đạo theo cặp chi ngày.
 */
public final class Almanac {
    private Almanac() {}

    private static final String[] STAR = {
        "Thanh Long", "Minh Đường", "Thiên Hình", "Chu Tước", "Kim Quỹ", "Kim Đường",
        "Bạch Hổ", "Ngọc Đường", "Thiên Lao", "Nguyên Vũ", "Tư Mệnh", "Câu Trần"
    };
    private static final boolean[] GOOD = {
        true, true, false, false, true, true, false, true, false, false, true, false
    };
    private static final int[] START = {0, 2, 4, 6, 8, 10};

    // Chỉ số chi của các giờ hoàng đạo, theo (chi ngày % 6): Tý/Ngọ, Sửu/Mùi, Dần/Thân, Mão/Dậu, Thìn/Tuất, Tỵ/Hợi.
    private static final int[][] HOURS = {
        {0, 1, 3, 6, 8, 9},
        {2, 3, 5, 8, 10, 11},
        {0, 1, 4, 5, 7, 10},
        {0, 2, 3, 6, 7, 9},
        {2, 4, 5, 8, 9, 11},
        {1, 4, 6, 7, 10, 11}
    };

    /** Chỉ số chi của ngày (0 = Tý ... 11 = Hợi). */
    public static int dayChi(int jd) {
        return Math.floorMod(jd + 1, 12);
    }

    private static int star(int lunarMonth, int jd) {
        int m = Math.floorMod(lunarMonth - 1, 6);
        return Math.floorMod(dayChi(jd) - START[m], 12);
    }

    public static boolean isGoodDay(int lunarMonth, int jd) {
        return GOOD[star(lunarMonth, jd)];
    }

    public static String starName(int lunarMonth, int jd) {
        return STAR[star(lunarMonth, jd)];
    }

    /** Ví dụ "Hoàng đạo (Thanh Long)" hoặc "Hắc đạo (Bạch Hổ)". */
    public static String dayLabel(int lunarMonth, int jd) {
        return (isGoodDay(lunarMonth, jd) ? "Hoàng đạo" : "Hắc đạo") + " (" + starName(lunarMonth, jd) + ")";
    }

    /** Giờ hoàng đạo, theo thứ tự trong ngày bắt đầu từ giờ Tý. withRange: kèm khung giờ. */
    public static String goodHours(int jd, boolean withRange) {
        int[] h = HOURS[dayChi(jd) % 6];
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(LunarCalendar.CHI[h[i]]);
            if (withRange) {
                int from = (23 + 2 * h[i]) % 24;
                int to = (from + 2) % 24;
                sb.append(" ").append(from).append("-").append(to).append("h");
            }
        }
        return sb.toString();
    }
}
