package vn.xin.clockwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.AlarmClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/** Dựng nội dung hiển thị: dòng chữ, ảnh chữ (đúng font) và RemoteViews cho widget và thẻ màn hình khóa. */
object Renderer {
    class Line(val text: String, val scale: Float, val bold: Boolean, val dim: Boolean)

    // Phần đệm trong bố cục widget (dp, mỗi cạnh) và hệ số ước lượng chiều cao dòng đồng hồ.
    private const val PAD_DP = 14
    private const val CLOCK_LINE = 1.4f

    private fun fmtTemp(v: Double): String = "${Math.round(v)}°"

    private fun weatherLine(ctx: Context, compact: Boolean): Line {
        val w = WeatherRepo.load(ctx) ?: return Line("Chưa có dữ liệu thời tiết", 1.0f, false, true)
        val sb = StringBuilder()
        sb.append(VnText.weatherIcon(w.code, w.isDay)).append(' ')
        sb.append(Math.round(w.tempC)).append("°C · ").append(VnText.weatherDesc(w.code))
        if (!compact && !w.feelsC.isNaN()) {
            sb.append(" · cảm giác ").append(fmtTemp(w.feelsC))
        }
        if (WeatherRepo.ageMinutes(ctx) > 90) {
            val t = SimpleDateFormat("HH:mm", Locale.US).format(Date(w.fetchedAt))
            sb.append(" · cập nhật ").append(t)
        }
        return Line(sb.toString(), 1.1f, false, false)
    }

    /**
     * compact = true: 2 dòng cho thẻ khóa thu gọn (ngày + âm lịch, thời tiết).
     * compact = false: đầy đủ cho widget và thẻ khóa mở rộng.
     */
    fun buildLines(ctx: Context, compact: Boolean): List<Line> {
        val out = ArrayList<Line>()
        val today = LocalDate.now()
        val wd = VnText.weekday(today.dayOfWeek)
        val dd = today.dayOfMonth
        val mm = today.monthValue
        val yy = today.year
        val lunar = LunarCalendar.fromSolar(dd, mm, yy)
        val lm = if (lunar.leap) "${lunar.month} nhuận" else "${lunar.month}"

        if (compact) {
            val head = StringBuilder(String.format(Locale.US, "%s, %02d/%02d", wd, dd, mm))
            if (Prefs.showLunar(ctx)) head.append(" · Âm ${lunar.day}/$lm")
            out.add(Line(head.toString(), 1.2f, true, false))
            if (Prefs.showWeather(ctx)) out.add(weatherLine(ctx, true))
            return out
        }

        val dateText = if (Prefs.longDate(ctx)) {
            "$wd, $dd tháng $mm, $yy"
        } else {
            String.format(Locale.US, "%s, %02d/%02d/%d", wd, dd, mm, yy)
        }
        out.add(Line(dateText, 1.25f, true, false))

        if (Prefs.showLunar(ctx)) {
            val text = if (Prefs.lunarDetail(ctx)) {
                "Âm lịch ${lunar.day}/$lm · tháng ${lunar.monthCanChi()} · ngày ${lunar.dayCanChi()} · năm ${lunar.yearCanChi()}"
            } else {
                "Âm lịch ${lunar.day}/$lm · năm ${lunar.yearCanChi()}"
            }
            out.add(Line(text, 1.0f, false, false))
        }

        if (Prefs.showEvents(ctx)) {
            val ev = ArrayList<String>(VnText.holidays(today))
            val term = LunarCalendar.solarTermStartingOn(lunar.jd)
            if (term != null) ev.add("Tiết $term")
            if (ev.isNotEmpty()) {
                out.add(Line(ev.joinToString(" · "), 0.95f, false, true))
            }
        }

        if (Prefs.showWeather(ctx)) {
            out.add(weatherLine(ctx, false))
            val w = WeatherRepo.load(ctx)
            if (w != null && Prefs.showExtra(ctx)) {
                val l2 = StringBuilder(Prefs.locName(ctx))
                if (!w.maxC.isNaN() && !w.minC.isNaN()) {
                    l2.append(" · ").append(fmtTemp(w.maxC)).append("/").append(fmtTemp(w.minC))
                }
                l2.append(" · mưa ").append(w.rainTodayPct).append("%")
                if (w.rainNext6hPct > 0) {
                    l2.append(" (6 giờ tới ").append(w.rainNext6hPct).append("%)")
                }
                out.add(Line(l2.toString(), 0.95f, false, true))

                val parts = ArrayList<String>()
                if (w.humidity >= 0) parts.add("Ẩm ${w.humidity}%")
                if (!w.uvMax.isNaN()) {
                    parts.add("UV ${Math.round(w.uvMax)} (${VnText.uvLabel(w.uvMax)})")
                }
                if (w.aqi >= 0) parts.add("AQI ${w.aqi} (${VnText.aqiLabel(w.aqi)})")
                if (parts.isNotEmpty()) {
                    out.add(Line(parts.joinToString(" · "), 0.95f, false, true))
                }
            }
        }
        return out
    }

    private class Row(
        val text: String,
        val size: Float,
        val bold: Boolean,
        val dim: Boolean,
        val width: Float,
        val ascent: Float,
        val height: Float
    )

    private class Layout(val rows: List<Row>, val total: Float)

    private fun typeface(ctx: Context, id: Int): Typeface {
        return try {
            ctx.resources.getFont(id)
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }

    private fun spToPx(ctx: Context, sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, ctx.resources.displayMetrics)

    private fun dpToPx(ctx: Context, dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ctx.resources.displayMetrics)

    /** Đo từng dòng: co chữ cho vừa bề ngang, cắt "..." nếu vẫn quá rộng. */
    private fun layoutRows(
        lines: List<Line>,
        widthPx: Int,
        basePx: Float,
        regular: Typeface,
        bold: Typeface
    ): Layout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val rows = ArrayList<Row>()
        var total = 0f
        for (line in lines) {
            var size = basePx * line.scale
            paint.typeface = if (line.bold) bold else regular
            paint.textSize = size
            var text = line.text
            val minSize = size * 0.65f
            while (paint.measureText(text) > widthPx && size > minSize) {
                size -= 1f
                paint.textSize = size
            }
            if (paint.measureText(text) > widthPx) {
                text = TextUtils.ellipsize(text, paint, widthPx.toFloat(), TextUtils.TruncateAt.END).toString()
            }
            val fm = paint.fontMetrics
            val h = fm.descent - fm.ascent
            rows.add(Row(text, size, line.bold, line.dim, paint.measureText(text), -fm.ascent, h))
            total += h + size * 0.22f
        }
        return Layout(rows, total)
    }

    /**
     * Vẽ các dòng chữ vào ảnh với đúng font.
     * maxHeightPx > 0: thu nhỏ cỡ chữ (tối thiểu 45%) cho vừa chiều cao.
     * exactHeight: ảnh có đúng chiều cao maxHeightPx (khớp tỉ lệ ô chứa trong widget).
     */
    private fun infoBitmap(
        lines: List<Line>,
        widthPx: Int,
        basePx: Float,
        color: Int,
        align: Int,
        regular: Typeface,
        bold: Typeface,
        maxHeightPx: Int,
        exactHeight: Boolean
    ): Bitmap {
        var f = 1f
        var lay = layoutRows(lines, widthPx, basePx, regular, bold)
        while (maxHeightPx > 0 && lay.total > maxHeightPx && f > 0.45f) {
            f -= 0.05f
            lay = layoutRows(lines, widthPx, basePx * f, regular, bold)
        }

        var heightPx = Math.ceil(lay.total.toDouble()).toInt() + 4
        if (maxHeightPx > 0) {
            heightPx = if (exactHeight) maxHeightPx else Math.min(heightPx, maxHeightPx)
        }
        val bmp = Bitmap.createBitmap(widthPx, heightPx.coerceAtLeast(8), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        var top = 0f
        for (r in lay.rows) {
            paint.typeface = if (r.bold) bold else regular
            paint.textSize = r.size
            val a = if (r.dim) (Color.alpha(color) * 0.82f).toInt() else Color.alpha(color)
            paint.color = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
            val x = when (align) {
                0 -> 0f
                1 -> (widthPx - r.width) / 2f
                else -> widthPx - r.width
            }
            canvas.drawText(r.text, x, top + r.ascent, paint)
            top += r.height + r.size * 0.22f
        }
        return bmp
    }

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

    private fun applyClockStyle(ctx: Context, rv: RemoteViews, id: Int, color: Int, sizeSp: Int) {
        val fmt = if (Prefs.is24h(ctx)) "HH:mm" else "h:mm"
        rv.setTextColor(id, color)
        rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
        rv.setCharSequence(id, "setFormat24Hour", fmt)
        rv.setCharSequence(id, "setFormat12Hour", fmt)
    }

    /**
     * RemoteViews cho widget màn hình chính. widthDp/heightDp là kích thước widget do launcher báo.
     * Căn lề đồng hồ: bố cục có 3 TextClock (trái, giữa, phải), chỉ một cái hiện.
     */
    fun widgetViews(ctx: Context, widthDp: Int, heightDp: Int): RemoteViews {
        val f = FontCatalog.get(Prefs.font(ctx))
        val rv = RemoteViews(ctx.packageName, f.widgetLayout)
        val color = Prefs.textColor(ctx)
        val align = Prefs.align(ctx)

        val w = if (widthDp > 0) widthDp else 250
        val h = if (heightDp > 0) heightDp else 150
        val availH = (h - 2 * PAD_DP).coerceAtLeast(40)

        // Đồng hồ không chiếm quá ~55% chiều cao khả dụng.
        val maxClockSp = (availH * 0.55f / 1.3f).toInt().coerceAtLeast(24)
        val clockSp = Math.min(Prefs.clockSp(ctx), maxClockSp)

        val ids = intArrayOf(R.id.clock, R.id.clock_c, R.id.clock_r)
        for (i in ids.indices) {
            applyClockStyle(ctx, rv, ids[i], color, clockSp)
            rv.setViewVisibility(ids[i], if (i == align.coerceIn(0, 2)) View.VISIBLE else View.GONE)
            rv.setOnClickPendingIntent(ids[i], alarmsPi(ctx))
        }

        val widthPx = dpToPx(ctx, (w - 2 * PAD_DP).coerceAtLeast(120).toFloat()).toInt().coerceIn(240, 1100)
        val slotDp = availH - clockSp * CLOCK_LINE
        val slotPx = dpToPx(ctx, slotDp.coerceAtLeast(24f)).toInt()
        val bmp = infoBitmap(
            buildLines(ctx, false),
            widthPx,
            spToPx(ctx, Prefs.infoSp(ctx).toFloat()),
            color,
            align,
            typeface(ctx, f.regular),
            typeface(ctx, f.bold),
            slotPx,
            true
        )
        rv.setImageViewBitmap(R.id.info, bmp)
        rv.setOnClickPendingIntent(R.id.info, openAppPi(ctx))

        val bg = Prefs.bgColor(ctx)
        rv.setInt(R.id.bg, "setColorFilter", Color.rgb(Color.red(bg), Color.green(bg), Color.blue(bg)))
        rv.setInt(R.id.bg, "setImageAlpha", (Prefs.bgAlpha(ctx) * 255 / 100).coerceIn(0, 255))
        return rv
    }

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
     * RemoteViews cho thẻ thông báo trên màn hình khóa.
     * Thu gọn: giới hạn ~44dp cao (vùng nội dung của thông báo thu gọn), 2 dòng.
     * Mở rộng: đầy đủ các dòng như widget.
     */
    fun notifViews(ctx: Context, expanded: Boolean): RemoteViews {
        val f = FontCatalog.get(Prefs.font(ctx))
        val rv = RemoteViews(ctx.packageName, f.notifLayout)
        val dm = ctx.resources.displayMetrics
        val color = lockTextColor(ctx)

        val screenDp = dm.widthPixels / dm.density
        val widthPx = dpToPx(ctx, (screenDp - 150f).coerceAtLeast(120f)).toInt().coerceIn(240, 1000)
        val baseSp = Math.min(Prefs.infoSp(ctx), 13).toFloat()
        val bmp = infoBitmap(
            buildLines(ctx, !expanded),
            widthPx,
            spToPx(ctx, baseSp),
            color,
            0,
            typeface(ctx, f.regular),
            typeface(ctx, f.bold),
            if (expanded) 0 else dpToPx(ctx, 44f).toInt(),
            false
        )
        rv.setImageViewBitmap(R.id.info, bmp)
        applyClockStyle(ctx, rv, R.id.clock, color, 32)
        return rv
    }
}
