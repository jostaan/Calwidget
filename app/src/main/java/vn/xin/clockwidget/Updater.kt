package vn.xin.clockwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/** Cập nhật widget và thẻ khóa; lập lịch lấy thời tiết định kỳ và làm mới lúc qua nửa đêm. */
object Updater {
    private const val JOB_ID = 7301
    private const val ACTION_MIDNIGHT = "vn.xin.clockwidget.MIDNIGHT"
    private const val STALE_MINUTES = 25L

    fun widgetIds(c: Context): IntArray {
        val mgr = AppWidgetManager.getInstance(c)
        return mgr.getAppWidgetIds(ComponentName(c, ClockWidgetProvider::class.java))
    }

    /** Vẽ lại tất cả widget và thẻ khóa từ dữ liệu đã lưu. Nhanh, không dùng mạng. */
    fun updateAll(ctx: Context) {
        val c = ctx.applicationContext
        try {
            val mgr = AppWidgetManager.getInstance(c)
            for (id in widgetIds(c)) {
                val opt = mgr.getAppWidgetOptions(id)
                // Chế độ dọc: rộng = MIN_WIDTH, cao = MAX_HEIGHT.
                val wDp = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val hDp = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150)
                mgr.updateAppWidget(id, Renderer.widgetViews(c, wDp, hDp))
            }
        } catch (e: Exception) {
            // Một widget lỗi không được làm hỏng các việc còn lại.
        }
        try {
            LockCard.update(c)
        } catch (e: Exception) {
            // Bỏ qua.
        }
    }

    /** Lấy thời tiết mới nếu dữ liệu cũ hơn 25 phút (hoặc force). Gọi ngoài luồng chính. */
    fun refreshWeather(ctx: Context, force: Boolean): Boolean {
        val c = ctx.applicationContext
        if (!force && WeatherRepo.ageMinutes(c) < STALE_MINUTES) return true
        return WeatherRepo.refresh(c)
    }

    /** Có cần chạy nền không: có widget trên màn hình hoặc bật thẻ khóa. */
    private fun needed(c: Context): Boolean = widgetIds(c).isNotEmpty() || Prefs.lockCard(c)

    /** Mỗi bước được bọc riêng: một lỗi lập lịch không được làm văng app hay các bước còn lại. */
    fun scheduleAll(ctx: Context) {
        val c = ctx.applicationContext
        val need = try {
            needed(c)
        } catch (e: Throwable) {
            CrashLog.record(c, e, "scheduleAll/needed")
            return
        }
        if (!need) {
            try {
                cancelAll(c)
            } catch (e: Throwable) {
                CrashLog.record(c, e, "cancelAll")
            }
            return
        }
        try {
            scheduleJob(c)
        } catch (e: Throwable) {
            CrashLog.record(c, e, "scheduleJob")
        }
        try {
            scheduleMidnight(c)
        } catch (e: Throwable) {
            CrashLog.record(c, e, "scheduleMidnight")
        }
    }

    private fun scheduleJob(c: Context) {
        val js = c.getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(JOB_ID) != null) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(c, WeatherJobService::class.java))
            .setPeriodic(30 * 60 * 1000L)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        js.schedule(info)
    }

    private fun midnightPi(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c,
            91,
            Intent(c, SystemReceiver::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun scheduleMidnight(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val pi = midnightPi(c)
        val next = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .plusSeconds(20)
            .toInstant()
            .toEpochMilli()
        var done = false
        if (am.canScheduleExactAlarms()) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
                done = true
            } catch (e: SecurityException) {
                // ROM không cấp quyền báo thức chính xác: dùng báo thức gần đúng.
            }
        }
        if (!done) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
    }

    fun cancelAll(ctx: Context) {
        val c = ctx.applicationContext
        c.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        c.getSystemService(AlarmManager::class.java).cancel(midnightPi(c))
    }
}
