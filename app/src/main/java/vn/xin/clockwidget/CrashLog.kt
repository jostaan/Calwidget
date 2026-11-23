package vn.xin.clockwidget

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ghi lại lỗi để xem ngay trong app (không cần máy tính hay adb).
 * Lỗi văng (uncaught) và lỗi đã bắt ở các điểm lập lịch/dựng giao diện đều vào đây.
 */
object CrashLog {
    fun install(app: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                record(app, e, "VĂNG, luồng " + t.name)
            } catch (x: Throwable) {
                // Không làm gì thêm.
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun record(c: Context, e: Throwable, where: String) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val head = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date()) + " [" + where + "]"
            val text = head + "\n" + sw.toString().take(3500)
            // commit() đồng bộ: process có thể chết ngay sau đó.
            Prefs.sp(c).edit().putString(Prefs.LAST_CRASH, text).commit()
        } catch (x: Throwable) {
            // Bỏ qua.
        }
    }

    fun last(c: Context): String = Prefs.str(c, Prefs.LAST_CRASH, "")

    fun clear(c: Context) {
        Prefs.sp(c).edit().remove(Prefs.LAST_CRASH).commit()
    }
}
