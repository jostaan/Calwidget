package vn.xin.clockwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.provider.AlarmClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Dựng RemoteViews cho widget màn hình chính và thẻ màn hình khóa.
 *
 * Bố cục 2 cột x 2 hàng:
 *   trái:  [đồng hồ]            / [thời tiết: nhiệt độ, ẩm, gió, UV, không khí]
 *   phải:  [thứ, ngày tháng]    / [âm lịch, hoàng đạo]
 *
 * Toàn bộ là TextView/TextClock thật (font khai báo trong layout, cỡ chữ tự co bằng autoSize).
 * Không vẽ chữ vào Bitmap và không dùng emoji: phiên bản trước vẽ chữ bằng StaticLayout/Canvas
 * và là nghi can chính của việc process chết đột ngột.
 */
object Renderer {
    private val CORNER_DRAWABLES = intArrayOf(R.drawable.bg_r0, R.drawable.bg_r12, R.drawable.bg_r22, R.drawable.bg_r32)

    /** Nội dung từng ô. null = ẩn ô đó. */
    class Cells {
        var dt1: String = ""
        var dt2: String = ""
        var lu1: String? = null
        var lu2: String? = null
        var lu3: String? = null
        var ev: String? = null
        var wx1: String? = null
        var wx2: String? = null
        var wx3: String? = null
        var wx4: String? = null
        var weatherOn: Boolean = true
        var lunarOn: Boolean = true
    }

    private fun fmtTemp(v: Double): String = "${Math.round(v)}°"

    private fun uvShort(uv: Double): String = when {
        uv < 3 -> "thấp"
        uv < 6 -> "vừa"
        uv < 8 -> "cao"
        uv < 11 -> "rất cao"
        else -> "cực cao"
    }

    private fun aqiShort(aqi: Int): String = when {
        aqi <= 50 -> "tốt"
        aqi <= 100 -> "vừa"
        aqi <= 150 -> "kém"
        aqi <= 200 -> "xấu"
        aqi <= 300 -> "rất xấu"
        else -> "nguy hại"
    }

    private fun join(parts: List<String>): String? {
        val p = parts.filter { it.isNotEmpty() }
        return if (p.isEmpty()) null else p.joinToString(" · ")
    }

    private fun staleSuffix(ctx: Context, w: Weather): String? {
        if (WeatherRepo.ageMinutes(ctx) <= 90) return null
        return "cập nhật " + SimpleDateFormat("HH:mm", Locale.US).format(Date(w.fetchedAt))
    }

    // ---------- Nội dung các ô ----------

    private fun dateCells(ctx: Context, today: LocalDate, c: Cells) {
        c.dt1 = VnText.weekday(today.dayOfWeek)
        c.dt2 = if (Prefs.longDate(ctx)) {
            "${today.dayOfMonth} tháng ${today.monthValue}, ${today.year}"
        } else {
            String.format(Locale.US, "%02d/%02d/%d", today.dayOfMonth, today.monthValue, today.year)
        }
    }

    private fun lunarCells(ctx: Context, today: LocalDate, c: Cells) {
        val showLunar = Prefs.showLunar(ctx)
        val showAlmanac = Prefs.showAlmanac(ctx)
        c.lunarOn = showLunar || showAlmanac
        if (!c.lunarOn) return
        val lunar = LunarCalendar.fromSolar(today.dayOfMonth, today.monthValue, today.year)
        val lm = if (lunar.leap) "${lunar.month} nhuận" else "${lunar.month}"

        if (showLunar) {
            c.lu1 = "Âm lịch ${lunar.day}/$lm · năm ${lunar.yearCanChi()}"
        } else {
            c.lu1 = "Âm lịch ${lunar.day}/$lm"
        }

        val l2 = ArrayList<String>()
        if (showLunar && Prefs.lunarDetail(ctx)) l2.add("Ngày ${lunar.dayCanChi()}")
        if (showAlmanac) l2.add(Almanac.dayLabel(lunar.month, lunar.jd))
        c.lu2 = join(l2)

        if (showAlmanac) c.lu3 = "Giờ hoàng đạo: " + Almanac.goodHours(lunar.jd, false)

        if (Prefs.showEvents(ctx)) {
            val ev = ArrayList<String>(VnText.holidays(today))
            val term = LunarCalendar.solarTermStartingOn(lunar.jd)
            if (term != null) ev.add("Tiết $term")
            c.ev = join(ev)
        }
    }

    private fun weatherCells(ctx: Context, c: Cells) {
        c.weatherOn = Prefs.showWeather(ctx)
        if (!c.weatherOn) return
        val w = WeatherRepo.load(ctx)
        if (w == null) {
            c.wx1 = "Chưa có thời tiết"
            // Hiện luôn lý do trên widget để biết lỗi mạng hay lỗi khác, không cần mở app.
            val err = Prefs.str(ctx, Prefs.LAST_ERR, "")
            c.wx2 = if (err.isNotEmpty()) err else "Đang chờ cập nhật từ mạng"
            c.wx3 = "Mở app để thử lại"
            return
        }
        c.wx1 = "${Math.round(w.tempC)}°C · ${VnText.weatherDesc(w.code)}"

        val l2 = ArrayList<String>()
        if (w.humidity >= 0) l2.add("Ẩm ${w.humidity}%")
        if (!w.windKmh.isNaN()) l2.add("Gió ${Math.round(w.windKmh)} km/h")
        c.wx2 = join(l2)

        // UV lấy là chỉ số cao nhất trong ngày (Open-Meteo daily), nên ghi rõ "đỉnh".
        val l3 = ArrayList<String>()
        if (!w.uvMax.isNaN()) l3.add("UV đỉnh ${Math.round(w.uvMax)} (${uvShort(w.uvMax)})")
        if (w.aqi >= 0) l3.add("AQI ${w.aqi} (${aqiShort(w.aqi)})")
        c.wx3 = join(l3)

        val l4 = ArrayList<String>()
        if (Prefs.showExtra(ctx)) {
            l4.add(Prefs.locName(ctx))
            if (!w.maxC.isNaN() && !w.minC.isNaN()) l4.add("${fmtTemp(w.maxC)}/${fmtTemp(w.minC)}")
            l4.add("mưa ${w.rainTodayPct}%")
        }
        val stale = staleSuffix(ctx, w)
        if (stale != null) l4.add(stale)
        c.wx4 = join(l4)
    }

    fun cells(ctx: Context): Cells {
        val c = Cells()
        val today = LocalDate.now()
        dateCells(ctx, today, c)
        lunarCells(ctx, today, c)
        weatherCells(ctx, c)
        return c
    }

    /** Văn bản thuần cho kiểu thẻ khóa "văn bản đơn giản". */
    fun plainLines(ctx: Context): List<String> {
        val c = cells(ctx)
        val out = ArrayList<String>()
        out.add(c.dt1 + ", " + c.dt2)
        c.lu1?.let { out.add(it) }
        c.lu2?.let { out.add(it) }
        c.lu3?.let { out.add(it) }
        c.ev?.let { out.add(it) }
        if (c.weatherOn) {
            c.wx1?.let { out.add(it) }
            c.wx2?.let { out.add(it) }
            c.wx3?.let { out.add(it) }
            c.wx4?.let { out.add(it) }
        }
        return out
    }

    // ---------- Thao tác với RemoteViews ----------

    private fun dim(color: Int): Int =
        Color.argb((Color.alpha(color) * 0.82f).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun put(rv: RemoteViews, id: Int, text: String?, color: Int) {
        if (text.isNullOrEmpty()) {
            rv.setViewVisibility(id, View.GONE)
        } else {
            rv.setViewVisibility(id, View.VISIBLE)
            rv.setTextViewText(id, text)
            rv.setTextColor(id, color)
        }
    }

    private fun setClock(ctx: Context, rv: RemoteViews, color: Int) {
        val fmt = if (Prefs.is24h(ctx)) "HH:mm" else "h:mm"
        rv.setTextColor(R.id.clock, color)
        rv.setCharSequence(R.id.clock, "setFormat24Hour", fmt)
        rv.setCharSequence(R.id.clock, "setFormat12Hour", fmt)
    }

    /** Điền 4 ô của bố cục đầy đủ (dùng chung cho widget và thẻ khóa mở rộng). */
    private fun fill(ctx: Context, rv: RemoteViews, c: Cells, color: Int) {
        val soft = dim(color)
        setClock(ctx, rv, color)

        put(rv, R.id.dt1, c.dt1, color)
        put(rv, R.id.dt2, c.dt2, soft)

        if (c.weatherOn) {
            rv.setViewVisibility(R.id.box_wx, View.VISIBLE)
            put(rv, R.id.wx1, c.wx1, color)
            put(rv, R.id.wx2, c.wx2, soft)
            put(rv, R.id.wx3, c.wx3, soft)
            put(rv, R.id.wx4, c.wx4, soft)
        } else {
            rv.setViewVisibility(R.id.box_wx, View.GONE)
        }

        if (c.lunarOn) {
            rv.setViewVisibility(R.id.box_lu, View.VISIBLE)
            put(rv, R.id.lu1, c.lu1, color)
            put(rv, R.id.lu2, c.lu2, soft)
            put(rv, R.id.lu3, c.lu3, soft)
            put(rv, R.id.ev, c.ev, soft)
        } else {
            rv.setViewVisibility(R.id.box_lu, View.GONE)
        }
    }

    // ---------- PendingIntent ----------

    private fun openAppPi(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            11,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun alarmsPi(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            12,
            Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    // ---------- Widget màn hình chính ----------

    /**
     * safe = true: bản tối giản (đồng hồ + ngày + dòng báo chế độ an toàn), dùng khi lần chạy trước
     * của process chết ngay lúc vẽ widget. Gỡ chế độ này ở màn hình Chẩn đoán.
     */
    fun widgetViews(ctx: Context, safe: Boolean = false): RemoteViews {
        val f = FontCatalog.get(Prefs.font(ctx))
        val rv = RemoteViews(ctx.packageName, f.widgetLayout)
        val color = Prefs.textColor(ctx)

        if (safe) {
            val c = Cells()
            dateCells(ctx, LocalDate.now(), c)
            c.lunarOn = false
            c.wx1 = "Chế độ an toàn"
            c.wx2 = "Mở app > Chẩn đoán"
            fill(ctx, rv, c, color)
        } else {
            fill(ctx, rv, cells(ctx), color)
        }

        val pad = dpToPx(ctx, Prefs.padDp(ctx).toFloat()).toInt()
        rv.setViewPadding(R.id.content, pad, pad, pad, pad)

        rv.setOnClickPendingIntent(R.id.clock, alarmsPi(ctx))
        rv.setOnClickPendingIntent(R.id.content, openAppPi(ctx))

        val bg = Prefs.bgColor(ctx)
        rv.setImageViewResource(R.id.bg, CORNER_DRAWABLES[Prefs.corner(ctx)])
        rv.setInt(R.id.bg, "setColorFilter", Color.rgb(Color.red(bg), Color.green(bg), Color.blue(bg)))
        rv.setInt(R.id.bg, "setImageAlpha", (Prefs.bgAlpha(ctx) * 255 / 100).coerceIn(0, 255))
        return rv
    }

    private fun dpToPx(ctx: Context, dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ctx.resources.displayMetrics)

    // ---------- Thẻ màn hình khóa ----------

    private fun isNight(ctx: Context): Boolean {
        val m = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return m == Configuration.UI_MODE_NIGHT_YES
    }

    fun lockTextColor(ctx: Context): Int {
        return when (Prefs.lockColor(ctx)) {
            1 -> Color.WHITE
            2 -> Color.BLACK
            else -> if (isNight(ctx)) Color.WHITE else Color.parseColor("#E6000000")
        }
    }

    /**
     * Thẻ thông báo trên màn hình khóa.
     * Mở rộng: cùng bố cục 2x2 như widget. Thu gọn: một hàng (đồng hồ | ngày, âm lịch | thời tiết).
     */
    fun notifViews(ctx: Context, expanded: Boolean): RemoteViews {
        val f = FontCatalog.get(Prefs.font(ctx))
        val color = lockTextColor(ctx)
        val c = cells(ctx)
        if (expanded) {
            val rv = RemoteViews(ctx.packageName, f.notifBigLayout)
            fill(ctx, rv, c, color)
            return rv
        }

        val rv = RemoteViews(ctx.packageName, f.notifLayout)
        val soft = dim(color)
        setClock(ctx, rv, color)

        val today = LocalDate.now()
        put(rv, R.id.dt1, c.dt1 + String.format(Locale.US, ", %02d/%02d", today.dayOfMonth, today.monthValue), color)

        val lu = if (c.lunarOn) {
            val lunar = LunarCalendar.fromSolar(today.dayOfMonth, today.monthValue, today.year)
            val lm = if (lunar.leap) "${lunar.month}n" else "${lunar.month}"
            val tag = if (Prefs.showAlmanac(ctx)) {
                if (Almanac.isGoodDay(lunar.month, lunar.jd)) " · Hoàng đạo" else " · Hắc đạo"
            } else ""
            "Âm ${lunar.day}/$lm$tag"
        } else null
        put(rv, R.id.lu1, lu, soft)

        if (c.weatherOn) {
            put(rv, R.id.wx1, c.wx1, color)
            put(rv, R.id.wx2, c.wx2, soft)
        } else {
            rv.setViewVisibility(R.id.wx1, View.GONE)
            rv.setViewVisibility(R.id.wx2, View.GONE)
        }
        return rv
    }
}
