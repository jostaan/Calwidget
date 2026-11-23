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
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Dựng nội dung hiển thị: dòng chữ, ảnh chữ (đúng font) và RemoteViews cho widget và thẻ màn hình khóa.
 * RemoteViews không đổi được font lúc chạy nên chữ phụ được vẽ vào Bitmap bằng đúng Typeface;
 * riêng đồng hồ dùng TextClock thật để chạy từng phút mà không cần cập nhật.
 */
object Renderer {
    /** maxLines: số dòng tối đa khi chữ phải xuống dòng (xuống ở dấu cách thường, không xuống ở NBSP). */
    class Line(
        val text: String,
        val scale: Float,
        val bold: Boolean,
        val dim: Boolean,
        val maxLines: Int = 1
    )

    private const val NBSP = ' '
    private val CORNER_DRAWABLES = intArrayOf(R.drawable.bg_r0, R.drawable.bg_r12, R.drawable.bg_r22, R.drawable.bg_r32)

    private fun nb(s: String): String = s.replace(' ', NBSP)

    /** Nối các cụm bằng " · "; trong mỗi cụm dùng NBSP để chỉ xuống dòng ở dấu chấm giữa. */
    private fun chunks(parts: List<String>): String =
        parts.filter { it.isNotEmpty() }.joinToString(" · ") { nb(it) }

    private fun fmtTemp(v: Double): String = "${Math.round(v)}°"

    private fun dateText(ctx: Context, today: LocalDate): String {
        val wd = VnText.weekday(today.dayOfWeek)
        val d = today.dayOfMonth
        val m = today.monthValue
        val y = today.year
        return if (Prefs.longDate(ctx)) {
            nb(wd) + ", " + nb("$d tháng $m, $y")
        } else {
            nb(wd) + ", " + String.format(Locale.US, "%02d/%02d/%d", d, m, y)
        }
    }

    private fun staleSuffix(ctx: Context, w: Weather): String? {
        if (WeatherRepo.ageMinutes(ctx) <= 90) return null
        return "cập nhật " + SimpleDateFormat("HH:mm", Locale.US).format(Date(w.fetchedAt))
    }

    // ---------- Nội dung cho widget 2 cột ----------

    /** Cột trái: ngày dương, âm lịch, can chi, ngày lễ và tiết khí. */
    fun leftLines(ctx: Context): List<Line> {
        val out = ArrayList<Line>()
        val today = LocalDate.now()
        val lunar = LunarCalendar.fromSolar(today.dayOfMonth, today.monthValue, today.year)
        val lm = if (lunar.leap) "${lunar.month} nhuận" else "${lunar.month}"

        out.add(Line(dateText(ctx, today), 1.2f, true, false, 2))

        if (Prefs.showLunar(ctx)) {
            out.add(Line(chunks(listOf("Âm lịch ${lunar.day}/$lm", "năm ${lunar.yearCanChi()}")), 1.0f, false, false, 2))
            if (Prefs.lunarDetail(ctx)) {
                out.add(Line(chunks(listOf("tháng ${lunar.monthCanChi()}", "ngày ${lunar.dayCanChi()}")), 0.95f, false, true, 2))
            }
        }
        if (Prefs.showEvents(ctx)) {
            val ev = ArrayList<String>(VnText.holidays(today))
            val term = LunarCalendar.solarTermStartingOn(lunar.jd)
            if (term != null) ev.add("Tiết $term")
            if (ev.isNotEmpty()) out.add(Line(chunks(ev), 0.95f, false, true, 2))
        }
        return out
    }

    /** Cột phải: thời tiết. */
    fun rightLines(ctx: Context): List<Line> {
        val out = ArrayList<Line>()
        if (!Prefs.showWeather(ctx)) return out
        val w = WeatherRepo.load(ctx)
        if (w == null) {
            out.add(Line("Chưa có dữ liệu thời tiết", 1.0f, false, true, 2))
            return out
        }
        out.add(Line("${VnText.weatherIcon(w.code, w.isDay)} ${Math.round(w.tempC)}°C", 2.0f, true, false, 1))

        val desc = ArrayList<String>()
        desc.add(VnText.weatherDesc(w.code))
        if (!w.feelsC.isNaN()) desc.add("cảm giác ${fmtTemp(w.feelsC)}")
        val stale = staleSuffix(ctx, w)
        if (stale != null) desc.add(stale)
        out.add(Line(chunks(desc), 1.0f, false, false, 2))

        if (Prefs.showExtra(ctx)) {
            val where = ArrayList<String>()
            where.add(Prefs.locName(ctx))
            if (!w.maxC.isNaN() && !w.minC.isNaN()) where.add("${fmtTemp(w.maxC)}/${fmtTemp(w.minC)}")
            out.add(Line(chunks(where), 0.95f, false, true, 2))

            val rain = ArrayList<String>()
            rain.add("Mưa ${w.rainTodayPct}%")
            if (w.rainNext6hPct > 0) rain.add("6 giờ tới ${w.rainNext6hPct}%")
            out.add(Line(chunks(rain), 0.95f, false, true, 2))

            val air = ArrayList<String>()
            if (w.humidity >= 0) air.add("Ẩm ${w.humidity}%")
            if (!w.uvMax.isNaN()) air.add("UV ${Math.round(w.uvMax)} (${VnText.uvLabel(w.uvMax)})")
            if (w.aqi >= 0) air.add("AQI ${w.aqi} (${VnText.aqiLabel(w.aqi)})")
            if (air.isNotEmpty()) out.add(Line(chunks(air), 0.95f, false, true, 2))
        }
        return out
    }

    // ---------- Nội dung cho thẻ màn hình khóa ----------

    private fun weatherLine(ctx: Context, compact: Boolean): Line {
        val w = WeatherRepo.load(ctx) ?: return Line("Chưa có dữ liệu thời tiết", 1.0f, false, true)
        val sb = StringBuilder()
        sb.append(VnText.weatherIcon(w.code, w.isDay)).append(' ')
        sb.append(Math.round(w.tempC)).append("°C · ").append(VnText.weatherDesc(w.code))
        if (!compact && !w.feelsC.isNaN()) {
            sb.append(" · cảm giác ").append(fmtTemp(w.feelsC))
        }
        val stale = staleSuffix(ctx, w)
        if (stale != null) sb.append(" · ").append(stale)
        return Line(sb.toString(), 1.1f, false, false)
    }

    /**
     * compact = true: 2 dòng cho thẻ khóa thu gọn (ngày + âm lịch, thời tiết).
     * compact = false: đầy đủ, dùng cho thẻ mở rộng và kiểu văn bản đơn giản.
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

        val dateStr = if (Prefs.longDate(ctx)) {
            "$wd, $dd tháng $mm, $yy"
        } else {
            String.format(Locale.US, "%s, %02d/%02d/%d", wd, dd, mm, yy)
        }
        out.add(Line(dateStr, 1.25f, true, false))

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
            if (ev.isNotEmpty()) out.add(Line(ev.joinToString(" · "), 0.95f, false, true))
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
                if (w.rainNext6hPct > 0) l2.append(" (6 giờ tới ").append(w.rainNext6hPct).append("%)")
                out.add(Line(l2.toString(), 0.95f, false, true))

                val parts = ArrayList<String>()
                if (w.humidity >= 0) parts.add("Ẩm ${w.humidity}%")
                if (!w.uvMax.isNaN()) parts.add("UV ${Math.round(w.uvMax)} (${VnText.uvLabel(w.uvMax)})")
                if (w.aqi >= 0) parts.add("AQI ${w.aqi} (${VnText.aqiLabel(w.aqi)})")
                if (parts.isNotEmpty()) out.add(Line(parts.joinToString(" · "), 0.95f, false, true))
            }
        }
        return out
    }

    // ---------- Vẽ chữ vào ảnh ----------

    private class Block(val layout: StaticLayout, val gap: Float)

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

    private fun alignOf(a: Int): Layout.Alignment = when (a) {
        1 -> Layout.Alignment.ALIGN_CENTER
        2 -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    private fun makeLayout(text: String, paint: TextPaint, width: Int, maxLines: Int, align: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(alignOf(align))
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()

    private fun truncated(l: StaticLayout): Boolean {
        val last = l.lineCount - 1
        return last >= 0 && l.getEllipsisCount(last) > 0
    }

    /** Mỗi dòng: xuống dòng tối đa maxLines; nếu vẫn bị cắt thì co chữ (tối thiểu 70%). */
    private fun layoutBlocks(
        lines: List<Line>,
        textW: Int,
        basePx: Float,
        color: Int,
        align: Int,
        regular: Typeface,
        bold: Typeface
    ): Pair<List<Block>, Float> {
        val blocks = ArrayList<Block>()
        var total = 0f
        for (line in lines) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            paint.typeface = if (line.bold) bold else regular
            val a = if (line.dim) (Color.alpha(color) * 0.82f).toInt() else Color.alpha(color)
            paint.color = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
            var size = basePx * line.scale
            val minSize = size * 0.7f
            paint.textSize = size
            var sl = makeLayout(line.text, paint, textW, line.maxLines, align)
            while (truncated(sl) && size > minSize) {
                size -= 1f
                paint.textSize = size
                sl = makeLayout(line.text, paint, textW, line.maxLines, align)
            }
            val gap = size * 0.18f
            blocks.add(Block(sl, gap))
            total += sl.height + gap
        }
        return Pair(blocks, total)
    }

    /**
     * Vẽ các dòng chữ vào ảnh với đúng font.
     * maxHeightPx > 0: thu nhỏ cỡ chữ (tối thiểu 45%) cho vừa chiều cao.
     * exactHeight: ảnh có đúng chiều cao maxHeightPx (khớp tỉ lệ ô chứa trong widget).
     * padL/padR: chừa lề trong ảnh (khe giữa hai cột). vCenter: căn giữa theo chiều dọc.
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
        exactHeight: Boolean,
        padL: Int = 0,
        padR: Int = 0,
        vCenter: Boolean = false
    ): Bitmap {
        val textW = (widthPx - padL - padR).coerceAtLeast(40)
        var f = 1f
        var res = layoutBlocks(lines, textW, basePx, color, align, regular, bold)
        while (maxHeightPx > 0 && res.second > maxHeightPx && f > 0.45f) {
            f -= 0.05f
            res = layoutBlocks(lines, textW, basePx * f, color, align, regular, bold)
        }

        var heightPx = Math.ceil(res.second.toDouble()).toInt() + 2
        if (maxHeightPx > 0) {
            heightPx = if (exactHeight) maxHeightPx else Math.min(heightPx, maxHeightPx)
        }
        heightPx = heightPx.coerceAtLeast(8)

        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(8), heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        var y = if (vCenter) Math.max(0f, (heightPx - res.second) / 2f) else 0f
        for (b in res.first) {
            canvas.save()
            canvas.translate(padL.toFloat(), y)
            b.layout.draw(canvas)
            canvas.restore()
            y += b.layout.height + b.gap
        }
        return bmp
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

    private fun applyClockStyle(ctx: Context, rv: RemoteViews, id: Int, color: Int, sizeSp: Int) {
        val fmt = if (Prefs.is24h(ctx)) "HH:mm" else "h:mm"
        rv.setTextColor(id, color)
        rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
        rv.setCharSequence(id, "setFormat24Hour", fmt)
        rv.setCharSequence(id, "setFormat12Hour", fmt)
    }

    // ---------- Widget màn hình chính (2 cột) ----------

    /**
     * Trái: đồng hồ (TextClock thật) + ngày, âm lịch. Phải: thời tiết.
     * Tắt thời tiết thì cột trái chiếm toàn bộ bề rộng.
     * Căn lề đồng hồ: bố cục có 3 TextClock (trái, giữa, phải), chỉ một cái hiện.
     * widthDp/heightDp là kích thước widget do launcher báo.
     */
    fun widgetViews(ctx: Context, widthDp: Int, heightDp: Int): RemoteViews {
        val f = FontCatalog.get(Prefs.font(ctx))
        val rv = RemoteViews(ctx.packageName, f.widgetLayout)
        val color = Prefs.textColor(ctx)
        val align = Prefs.align(ctx).coerceIn(0, 2)
        val reg = typeface(ctx, f.regular)
        val bold = typeface(ctx, f.bold)

        val w = if (widthDp > 0) widthDp else 300
        val h = if (heightDp > 0) heightDp else 150
        val padPx = dpToPx(ctx, Prefs.padDp(ctx).toFloat()).toInt()
        val wPx = dpToPx(ctx, w.toFloat()).toInt()
        val hPx = dpToPx(ctx, h.toFloat()).toInt()
        val innerW = (wPx - 2 * padPx).coerceAtLeast(160)
        val availH = (hPx - 2 * padPx).coerceAtLeast(dpToPx(ctx, 40f).toInt())

        val twoCol = Prefs.showWeather(ctx)
        val colW = if (twoCol) innerW / 2 else innerW
        val gapPx = if (twoCol) dpToPx(ctx, 6f).toInt() else 0

        // Cỡ đồng hồ: không vượt cỡ người dùng chọn, không quá ~50% chiều cao, và phải vừa bề rộng cột.
        val cp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        cp.typeface = bold
        cp.textSize = 100f
        val fm = cp.fontMetrics
        val lineEm = (fm.descent - fm.ascent) / 100f
        val widthEm = Math.max(cp.measureText("00:00"), cp.measureText("88:88")) / 100f
        val unit = spToPx(ctx, 1f)
        val byH = availH * 0.5f / lineEm / unit
        val byW = (colW - gapPx) * 0.97f / widthEm / unit
        val clockSp = Math.floor(minOf(Prefs.clockSp(ctx).toFloat(), byH, byW).toDouble()).toInt().coerceAtLeast(18)

        val ids = intArrayOf(R.id.clock, R.id.clock_c, R.id.clock_r)
        for (i in ids.indices) {
            applyClockStyle(ctx, rv, ids[i], color, clockSp)
            rv.setViewVisibility(ids[i], if (i == align) View.VISIBLE else View.GONE)
            rv.setOnClickPendingIntent(ids[i], alarmsPi(ctx))
        }

        val clockPx = spToPx(ctx, clockSp.toFloat()) * lineEm
        val slotPx = (availH - clockPx).toInt().coerceAtLeast(dpToPx(ctx, 24f).toInt())
        val baseInfo = spToPx(ctx, Prefs.infoSp(ctx).toFloat())

        val left = infoBitmap(
            leftLines(ctx), colW.coerceIn(120, 1100), baseInfo, color, align, reg, bold,
            slotPx, true, 0, gapPx, false
        )
        rv.setImageViewBitmap(R.id.info, left)
        rv.setOnClickPendingIntent(R.id.info, openAppPi(ctx))

        if (twoCol) {
            val right = infoBitmap(
                rightLines(ctx), colW.coerceIn(120, 1100), baseInfo, color, align, reg, bold,
                availH, true, gapPx, 0, true
            )
            rv.setImageViewBitmap(R.id.info2, right)
            rv.setOnClickPendingIntent(R.id.info2, openAppPi(ctx))
            rv.setViewVisibility(R.id.info2, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.info2, View.GONE)
        }

        rv.setViewPadding(R.id.content, padPx, padPx, padPx, padPx)

        val bg = Prefs.bgColor(ctx)
        rv.setImageViewResource(R.id.bg, CORNER_DRAWABLES[Prefs.corner(ctx)])
        rv.setInt(R.id.bg, "setColorFilter", Color.rgb(Color.red(bg), Color.green(bg), Color.blue(bg)))
        rv.setInt(R.id.bg, "setImageAlpha", (Prefs.bgAlpha(ctx) * 255 / 100).coerceIn(0, 255))
        return rv
    }

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
     * RemoteViews cho thẻ thông báo trên màn hình khóa.
     * Thu gọn: giới hạn ~44dp cao (vùng nội dung của thông báo thu gọn), 2 dòng.
     * Mở rộng: đầy đủ các dòng.
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
