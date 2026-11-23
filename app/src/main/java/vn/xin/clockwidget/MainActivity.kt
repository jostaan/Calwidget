package vn.xin.clockwidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var col: LinearLayout
    private lateinit var previewHost: FrameLayout
    private lateinit var tvStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val applyRunnable = Runnable { applyNow() }
    private var lastLoc = ""

    private companion object {
        const val PREVIEW_H_DP = 150
    }

    private val presetNames = listOf("Màu mẫu...", "Trắng", "Đen", "Vàng nhạt", "Xanh trời nhạt", "Hồng nhạt", "Xanh lá nhạt")
    private val presetHex = listOf("", "#FFFFFF", "#000000", "#FFE9A8", "#BFE3FF", "#FFD1E0", "#CFF5D0")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun locKey(): String = Prefs.lat(this).toString() + "," + Prefs.lon(this).toString()

    // ---------- áp dụng thay đổi ----------

    private fun changed() {
        handler.removeCallbacks(applyRunnable)
        handler.postDelayed(applyRunnable, 400)
    }

    private fun applyNow() {
        val k = locKey()
        if (k != lastLoc) {
            lastLoc = k
            fetchWeather(false)
        }
        refreshPreview()
        Updater.updateAll(this)
        Updater.scheduleAll(this)
        updateStatus()
    }

    private fun fetchWeather(showToast: Boolean) {
        val app = applicationContext
        Thread {
            val ok = WeatherRepo.refresh(app)
            runOnUiThread {
                if (showToast) {
                    toast(if (ok) "Đã cập nhật thời tiết" else Prefs.str(app, Prefs.LAST_ERR, "").ifEmpty { "Lỗi" })
                }
                refreshPreview()
                Updater.updateAll(app)
                updateStatus()
            }
        }.start()
    }

    private fun refreshPreview() {
        previewHost.removeAllViews()
        val dm = resources.displayMetrics
        val wDp = ((dm.widthPixels / dm.density) - 32f).toInt().coerceAtLeast(200)
        try {
            val rv = Renderer.widgetViews(applicationContext, wDp, PREVIEW_H_DP)
            previewHost.addView(rv.apply(this, previewHost))
        } catch (e: Exception) {
            val tv = TextView(this)
            tv.text = "Không dựng được bản xem trước: " + e.javaClass.simpleName
            previewHost.addView(tv)
        }
    }

    private fun updateStatus() {
        val t = Prefs.long(this, Prefs.W_TIME, 0L)
        val err = Prefs.str(this, Prefs.LAST_ERR, "")
        val base = if (t == 0L) {
            "Chưa có dữ liệu thời tiết."
        } else {
            "Thời tiết cập nhật lúc " + SimpleDateFormat("HH:mm dd/MM", Locale.US).format(Date(t)) + "."
        }
        val errText = if (err.isNotEmpty()) " Lỗi gần nhất: $err." else ""
        tvStatus.text = base + errText + " Widget đang đặt: " + Updater.widgetIds(this).size + "."
    }

    // ---------- thành phần giao diện ----------

    private fun addTitle(s: String) {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = 17f
        tv.setTypeface(tv.typeface, Typeface.BOLD)
        tv.setPadding(0, dp(22), 0, dp(4))
        col.addView(tv, lp())
    }

    private fun addNote(s: String): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = 13f
        tv.setPadding(0, dp(4), 0, dp(4))
        col.addView(tv, lp())
        return tv
    }

    private fun addButton(s: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = s
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        col.addView(b, lp())
    }

    private fun addSwitch(label: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val sw = Switch(this)
        sw.text = label
        sw.isChecked = get()
        sw.setPadding(0, dp(8), 0, dp(8))
        sw.setOnCheckedChangeListener { _, on ->
            set(on)
            changed()
        }
        col.addView(sw, lp())
    }

    private fun addSeek(label: String, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit) {
        val tv = TextView(this)
        tv.text = "$label: ${get()}"
        tv.setPadding(0, dp(10), 0, 0)
        col.addView(tv, lp())
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = (get() - min).coerceIn(0, max - min)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    set(min + p)
                    tv.text = "$label: ${min + p}"
                    changed()
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        col.addView(sb, lp())
    }

    private fun addSpinner(label: String, items: List<String>, get: () -> Int, set: (Int) -> Unit): Spinner {
        val tv = TextView(this)
        tv.text = label
        tv.setPadding(0, dp(10), 0, 0)
        col.addView(tv, lp())
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        sp.setSelection(get().coerceIn(0, items.size - 1))
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos != get()) {
                    set(pos)
                    changed()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        col.addView(sp, lp())
        return sp
    }

    private fun addField(label: String, initial: String, inputType: Int, onText: (String) -> Unit): EditText {
        val tv = TextView(this)
        tv.text = label
        tv.setPadding(0, dp(10), 0, 0)
        col.addView(tv, lp())
        val et = EditText(this)
        et.inputType = inputType
        et.setText(initial)
        et.setSingleLine(true)
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onText(s?.toString() ?: "")
                changed()
            }
        })
        col.addView(et, lp())
        return et
    }

    private fun parseHex(s: String): Int? {
        var t = s.trim()
        if (t.isEmpty()) return null
        if (!t.startsWith("#")) t = "#$t"
        if (t.length != 7) return null
        return try {
            Color.parseColor(t)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun addColorPicker(label: String, key: String, def: Int) {
        val hexNow = String.format("#%06X", Prefs.int(this, key, def) and 0xFFFFFF)
        val et = addField("$label (mã #RRGGBB)", hexNow, InputType.TYPE_CLASS_TEXT) { s ->
            val c = parseHex(s)
            if (c != null) Prefs.putInt(this, key, c)
        }
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presetNames)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos > 0) {
                    et.setText(presetHex[pos])
                    sp.setSelection(0)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        col.addView(sp, lp())
    }

    private fun safeStart(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            toast("Không mở được màn hình này.")
        }
    }

    // ---------- vòng đời ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastLoc = locKey()

        val root = ScrollView(this)
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(12), dp(16), dp(40))
        root.addView(col)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsets.CONSUMED
        }
        setContentView(root)

        val head = TextView(this)
        head.text = "Đồng hồ · Lịch · Thời tiết"
        head.textSize = 24f
        head.setTypeface(head.typeface, Typeface.BOLD)
        col.addView(head, lp())

        // Bản xem trước (cùng RemoteViews với widget thật)
        previewHost = FrameLayout(this)
        previewHost.setBackgroundColor(Color.parseColor("#37474F"))
        val plp = lp()
        plp.height = dp(PREVIEW_H_DP)
        plp.topMargin = dp(10)
        col.addView(previewHost, plp)
        tvStatus = addNote("")

        addNote(
            "Màn hình khóa: widget của app khác thường không lên được màn hình khóa HyperOS. " +
                "Dùng \"Thẻ màn hình khóa\" bên dưới (thông báo ghim). Giờ gốc của màn hình khóa đổi kiểu và font trong Cài đặt hệ thống."
        )

        addTitle("Thêm vào màn hình")
        addButton("Thêm widget vào màn hình chính") {
            val mgr = AppWidgetManager.getInstance(this)
            if (mgr.isRequestPinAppWidgetSupported) {
                mgr.requestPinAppWidget(ComponentName(this, ClockWidgetProvider::class.java), null, null)
            } else {
                toast("Giữ lâu màn hình chính, chọn Widget, tìm \"Đồng hồ, lịch âm, thời tiết\".")
            }
        }

        addTitle("Vị trí thời tiết")
        addField("Tên hiển thị", Prefs.locName(this), InputType.TYPE_CLASS_TEXT) {
            Prefs.putStr(this, Prefs.LOC_NAME, it)
        }
        val numType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        addField("Vĩ độ (Quận 11: 10.7629)", Prefs.str(this, Prefs.LAT, Prefs.DEF_LAT.toString()), numType) {
            Prefs.putStr(this, Prefs.LAT, it)
        }
        addField("Kinh độ (Quận 11: 106.65)", Prefs.str(this, Prefs.LON, Prefs.DEF_LON.toString()), numType) {
            Prefs.putStr(this, Prefs.LON, it)
        }
        addButton("Lấy thời tiết ngay") { fetchWeather(true) }

        addTitle("Kiểu chữ")
        addSpinner("Font Google", FontCatalog.all.map { it.label },
            { Prefs.font(this) }, { Prefs.putInt(this, Prefs.FONT, it) })
        addSpinner("Căn lề", listOf("Căn trái", "Căn giữa", "Căn phải"),
            { Prefs.align(this) }, { Prefs.putInt(this, Prefs.ALIGN, it) })
        addSeek("Cỡ chữ đồng hồ (sp)", 30, 110, { Prefs.clockSp(this) }, { Prefs.putInt(this, Prefs.CLOCK_SP, it) })
        addSeek("Cỡ chữ thông tin (sp)", 10, 24, { Prefs.infoSp(this) }, { Prefs.putInt(this, Prefs.INFO_SP, it) })

        addTitle("Màu sắc")
        addColorPicker("Màu chữ", Prefs.TEXT_COLOR, -1)
        addColorPicker("Màu nền", Prefs.BG_COLOR, -16777216)
        addSeek("Độ đậm nền (%)", 0, 100, { Prefs.bgAlpha(this) }, { Prefs.putInt(this, Prefs.BG_ALPHA, it) })

        addTitle("Nội dung")
        addSwitch("Đồng hồ 24 giờ", { Prefs.is24h(this) }, { Prefs.putBool(this, Prefs.H24, it) })
        addSwitch("Ngày dạng dài (22 tháng 11, 2026)", { Prefs.longDate(this) }, { Prefs.putBool(this, Prefs.LONG_DATE, it) })
        addSwitch("Âm lịch", { Prefs.showLunar(this) }, { Prefs.putBool(this, Prefs.SHOW_LUNAR, it) })
        addSwitch("Âm lịch chi tiết (can chi tháng, ngày, năm)", { Prefs.lunarDetail(this) }, { Prefs.putBool(this, Prefs.LUNAR_DETAIL, it) })
        addSwitch("Ngày lễ, Rằm, mùng 1 và tiết khí", { Prefs.showEvents(this) }, { Prefs.putBool(this, Prefs.SHOW_EVENTS, it) })
        addSwitch("Thời tiết", { Prefs.showWeather(this) }, { Prefs.putBool(this, Prefs.SHOW_WEATHER, it) })
        addSwitch("Chi tiết thời tiết (cao/thấp, mưa, ẩm, UV, AQI)", { Prefs.showExtra(this) }, { Prefs.putBool(this, Prefs.SHOW_EXTRA, it) })

        addTitle("Thẻ màn hình khóa")
        addNote(
            "Thông báo ghim yên lặng, có đồng hồ chạy thật. Cần bật hiện thông báo trên màn hình khóa " +
                "(Cài đặt hệ thống → Thông báo → Màn hình khóa). Từ Android 14, bạn vuốt bỏ được thẻ này; " +
                "thẻ tự hiện lại ở lần cập nhật kế tiếp."
        )
        addSwitch("Hiện thẻ trên màn hình khóa", { Prefs.lockCard(this) }, {
            Prefs.putBool(this, Prefs.LOCK_CARD, it)
            if (it && !LockCard.hasPermission(this)) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5)
            }
        })
        addSpinner("Kiểu thẻ", listOf("Bố cục riêng (font Google, đồng hồ chạy thật)", "Văn bản đơn giản của hệ thống"),
            { Prefs.lockStyle(this) }, { Prefs.putInt(this, Prefs.LOCK_STYLE, it) })
        addSpinner("Màu chữ của thẻ", listOf("Tự động theo giao diện sáng/tối", "Chữ trắng", "Chữ đen"),
            { Prefs.lockColor(this) }, { Prefs.putInt(this, Prefs.LOCK_COLOR, it) })
        addButton("Cài đặt thông báo của app") {
            safeStart(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }

        addTitle("HyperOS: giữ cập nhật chạy ổn định")
        addNote("Không đặt các mục này thì HyperOS có thể dừng cập nhật nền, widget và thẻ khóa hiện ngày cũ.")
        addButton("Tắt tối ưu pin cho app") {
            safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        addButton("Tự khởi động (HyperOS) / chi tiết app") {
            try {
                startActivity(
                    Intent().setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                )
            } catch (e: Exception) {
                safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        addNote("Thủ công: khóa app trong màn hình đa nhiệm (nhấn giữ thẻ app, chọn biểu tượng khóa).")

        refreshPreview()
        updateStatus()
        if (WeatherRepo.ageMinutes(this) > 25) fetchWeather(false)
    }

    override fun onResume() {
        super.onResume()
        Updater.scheduleAll(this)
        refreshPreview()
        updateStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        applyNow()
    }
}
