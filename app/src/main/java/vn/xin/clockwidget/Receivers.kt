package vn.xin.clockwidget

import android.app.job.JobParameters
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Thứ tự chung cho mọi điểm vào chạy nền: vẽ lại ngay từ dữ liệu đã lưu (không phụ thuộc lập lịch),
 * rồi mới đặt lịch, rồi mới lấy thời tiết. Mỗi bước bọc riêng để một lỗi không chặn các bước sau
 * và không làm văng process.
 */
private fun runStep(c: Context, name: String, block: () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        CrashLog.record(c, t, name)
    }
}

/** Chạy định kỳ ~30 phút: lấy thời tiết rồi vẽ lại. */
class WeatherJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        Thread {
            try {
                runStep(app, "job/refresh") { Updater.refreshWeather(app, false) }
                runStep(app, "job/draw") { Updater.updateAll(app) }
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

/**
 * Nhận khởi động máy, cập nhật app, đổi giờ/múi giờ, báo thức nửa đêm và báo thức 30 phút:
 * vẽ lại ngay, đặt lại lịch, rồi lấy thời tiết nếu dữ liệu đã cũ.
 */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val action = intent.action ?: "?"
        runStep(app, "receiver/draw $action") { Updater.updateAll(app) }
        runStep(app, "receiver/schedule $action") { Updater.scheduleAll(app) }
        val pending = goAsync()
        Thread {
            try {
                runStep(app, "receiver/weather $action") {
                    if (Updater.refreshWeather(app, false)) Updater.updateAll(app)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

class ClockWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext
        runStep(app, "widget/draw") { Updater.updateAll(app) }
        runStep(app, "widget/schedule") { Updater.scheduleAll(app) }
        val pending = goAsync()
        Thread {
            try {
                runStep(app, "widget/weather") {
                    if (Updater.refreshWeather(app, false)) Updater.updateAll(app)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        val app = context.applicationContext
        runStep(app, "widget/options") { Updater.updateAll(app) }
    }

    override fun onEnabled(context: Context) {
        val app = context.applicationContext
        runStep(app, "widget/enabled") { Updater.scheduleAll(app) }
    }

    override fun onDisabled(context: Context) {
        val app = context.applicationContext
        runStep(app, "widget/disabled") { Updater.scheduleAll(app) }
    }
}
