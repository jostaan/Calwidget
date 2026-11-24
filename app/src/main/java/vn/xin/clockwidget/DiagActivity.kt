package vn.xin.clockwidget

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Màn hình chẩn đoán độc lập: chỉ có TextView và nút, không bố cục riêng, không font tải, không bản xem trước,
 * không gọi mạng. Mục đích: vẫn mở được và đọc được lỗi ngay cả khi màn hình chính của app tự thoát.
 */
class DiagActivity : Activity() {
    private var report: TextView? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun reportText(): String =
        try {
            Diag.report(this)
        } catch (t: Throwable) {
            "Không dựng được báo cáo: " + t.javaClass.name + ": " + t.message
        }

    private fun refresh() {
        report?.text = reportText()
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun button(box: LinearLayout, label: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener {
            try {
                onClick()
            } catch (t: Throwable) {
                CrashLog.record(this, t, "diag/$label")
                toast("Lỗi: " + t.javaClass.simpleName)
            }
        }
        box.addView(b, lp())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(12), dp(16), dp(40))
        root.addView(box)
        root.setOnApplyWindowInsetsListener { v, insets ->
            val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            WindowInsets.CONSUMED
        }

        val head = TextView(this)
        head.text = "Chẩn đoán"
        head.textSize = 24f
        head.setTypeface(head.typeface, Typeface.BOLD)
        box.addView(head, lp())

        val hint = TextView(this)
        hint.text = "Màn hình này không dùng widget, font hay mạng nên mở được cả khi màn hình chính bị thoát. " +
            "Sao chép báo cáo bên dưới để gửi khi cần hỗ trợ."
        hint.textSize = 13f
        hint.setPadding(0, dp(6), 0, dp(10))
        box.addView(hint, lp())

        val tv = TextView(this)
        tv.textSize = 12f
        tv.setTextIsSelectable(true)
        tv.text = reportText()
        box.addView(tv, lp())
        report = tv

        button(box, "Sao chép báo cáo") {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("báo cáo", reportText()))
            toast("Đã sao chép")
        }
        button(box, "Làm mới báo cáo") { refresh() }
        button(box, "Mở màn hình cài đặt chính") {
            startActivity(android.content.Intent(this, MainActivity::class.java))
        }
        button(box, "Bật lại bản xem trước trong app") {
            Prefs.sp(this).edit().putBoolean(Prefs.PREVIEW_OFF, false).commit()
            refresh()
        }
        button(box, "Thoát chế độ an toàn của widget và vẽ lại") {
            Prefs.sp(this).edit().putBoolean(Guard.SAFE_DRAW, false).commit()
            Updater.updateAll(this)
            refresh()
        }
        button(box, "Xóa ghi nhận lỗi") {
            CrashLog.clear(this)
            Guard.clearNotes(this)
            refresh()
        }
        button(box, "Đặt lại toàn bộ cài đặt") {
            Prefs.sp(this).edit().clear().commit()
            refresh()
            toast("Đã đặt lại")
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

}
