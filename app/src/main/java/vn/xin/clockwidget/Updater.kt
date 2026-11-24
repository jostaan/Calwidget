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
    private const val ACTION_TICK = "vn.xin.clockwidget.TICK"
    private const val TICK_MS = 30 * 60 * 1000L
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
                // Mỗi widget vẽ riêng: một widget lỗi không làm hỏng widget khác hay thẻ khóa.
                try {
                    Guard.run(c, Guard.STAGE_WIDGET) {
                        mgr.updateAppWidget(id, Renderer.widgetViews(c, Guard.safeDraw(c)))
                    }
                } catch (t: Throwable) {
                    CrashLog.record(c, t, "updateAll/widget $id")
                }
            }
        } catch (t: Throwable) {
            CrashLog.record(c, t, "updateAll")
        }
        try {
            Guard.run(c, Guard.STAGE_LOCK) { LockCard.update(c) }
        } catch (t: Throwable) {
            CrashLog.record(c, t, "updateAll/lockcard")
        }
    }

    /** Lấy thời tiết mới nếu dữ liệu cũ hơn 25 phút (hoặc force). Gọi ngoài luồng chính. */
    fun refreshWeather(ctx: Context, force: Boolean): Boolean {
        val c = ctx.applicationContext
        if (!force && WeatherRepo.ageMinutes(c) < STALE_MINUTES) return true
        return Guard.run(c, Guard.STAGE_WEATHER) { WeatherRepo.refresh(c) }
    }

    /** Có cần chạy nền không: có widget trên màn hình hoặc bật thẻ khóa. */
    private fun needed(c: Context): Boolean = widgetIds(c).isNotEmpty() || Prefs.lockCard(c)

    /** Mỗi bước được bọc riêng: một lỗi lập lịch không được làm văng app hay các bước còn lại. */
    fun scheduleAll(ctx: Context) {
        val c = ctx.applicationContext
        Guard.run(c, Guard.STAGE_SCHEDULE) { scheduleAllInner(c) }
    }

    private fun scheduleAllInner(c: Context) {
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
        try {
            scheduleTick(c)
        } catch (e: Throwable) {
            CrashLog.record(c, e, "scheduleTick")
        }
    }

    /** Có job định kỳ đang chờ không (dùng cho báo cáo chẩn đoán). */
    fun jobPending(c: Context): Boolean =
        c.getSystemService(JobScheduler::class.java).getPendingJob(JOB_ID) != null

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

    private fun tickPi(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c,
            92,
            Intent(c, SystemReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * Báo thức gần đúng mỗi ~30 phút, độc lập với JobScheduler: nếu job bị ROM chặn hoặc lập lịch lỗi,
     * chuỗi này vẫn kéo thời tiết về. Mỗi lần nổ, SystemReceiver gọi scheduleAll để đặt lại báo thức kế tiếp.
     */
    private fun scheduleTick(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + TICK_MS, tickPi(c))
    }

    fun cancelAll(ctx: Context) {
        val c = ctx.applicationContext
        c.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(midnightPi(c))
        am.cancel(tickPi(c))
    }
}
