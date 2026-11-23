package vn.xin.clockwidget

import android.app.job.JobParameters
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Chạy định kỳ ~30 phút: lấy thời tiết rồi vẽ lại. */
class WeatherJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext
        Thread {
            try {
                Updater.refreshWeather(app, false)
                Updater.updateAll(app)
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

/**
 * Nhận khởi động máy, cập nhật app, đổi giờ/múi giờ/ngày và báo thức nửa đêm:
 * đặt lại lịch, vẽ lại ngay, rồi lấy thời tiết nếu dữ liệu đã cũ.
 */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Updater.scheduleAll(app)
        Updater.updateAll(app)
        val pending = goAsync()
        Thread {
            try {
                if (Updater.refreshWeather(app, false)) {
                    Updater.updateAll(app)
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
        Updater.scheduleAll(app)
        Updater.updateAll(app)
        val pending = goAsync()
        Thread {
            try {
                if (Updater.refreshWeather(app, false)) {
                    Updater.updateAll(app)
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
        Updater.updateAll(context.applicationContext)
    }

    override fun onEnabled(context: Context) {
        Updater.scheduleAll(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        Updater.scheduleAll(context.applicationContext)
    }
}
