package vn.xin.clockwidget

import android.app.AlarmManager
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Báo cáo chẩn đoán ngắn: người dùng sao chép gửi đi, không cần máy tính hay adb. */
object Diag {
    private fun safe(block: () -> String): String =
        try {
            block()
        } catch (t: Throwable) {
            "không đọc được (" + t.javaClass.simpleName + ")"
        }

    private fun yn(v: Boolean, yes: String, no: String): String = if (v) yes else no

    fun report(c: Context): String {
        val sb = StringBuilder()
        sb.append("Bản ").append(safe {
            c.packageManager.getPackageInfo(c.packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "?"
        })
        sb.append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        sb.append(" · Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Bản hệ thống: ").append(Build.DISPLAY).append('\n')

        sb.append("Widget đang đặt: ").append(safe { Updater.widgetIds(c).size.toString() }).append('\n')
        sb.append("Kích thước widget (dp): ").append(safe {
            val ids = Updater.widgetIds(c)
            if (ids.isEmpty()) {
                "chưa có"
            } else {
                val o = AppWidgetManager.getInstance(c).getAppWidgetOptions(ids[0])
                o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).toString() + " x " +
                    o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            }
        }).append('\n')

        sb.append("Job nền 30 phút: ").append(safe { yn(Updater.jobPending(c), "đã lập lịch", "CHƯA lập lịch") }).append('\n')
        sb.append("Báo thức chính xác: ").append(safe {
            yn(c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(), "được phép", "không được phép (dùng báo thức gần đúng)")
        }).append('\n')
        sb.append("Thông báo: ").append(safe {
            yn(c.getSystemService(NotificationManager::class.java).areNotificationsEnabled(), "bật", "TẮT")
        }).append('\n')
        sb.append("Không hạn chế pin: ").append(safe {
            yn(c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName), "đã bật", "chưa (nên bật)")
        }).append('\n')
        sb.append("Thẻ màn hình khóa: ").append(safe { yn(Prefs.lockCard(c), "bật", "tắt") }).append('\n')

        sb.append("Thời tiết: ").append(safe {
            val t = Prefs.long(c, Prefs.W_TIME, 0L)
            if (t == 0L) {
                "chưa lấy được lần nào"
            } else {
                SimpleDateFormat("HH:mm dd/MM", Locale.US).format(Date(t)) + " (" + WeatherRepo.ageMinutes(c) + " phút trước)"
            }
        }).append('\n')
        sb.append("Vị trí: ").append(safe { Prefs.lat(c).toString() + ", " + Prefs.lon(c) }).append('\n')
        val err = Prefs.str(c, Prefs.LAST_ERR, "")
        sb.append("Lỗi thời tiết gần nhất: ").append(if (err.isEmpty()) "không" else err).append('\n')

        val crash = CrashLog.last(c)
        sb.append("Lỗi ghi nhận: ").append(if (crash.isEmpty()) "không" else "\n" + crash.take(1200))
        return sb.toString()
    }
}
