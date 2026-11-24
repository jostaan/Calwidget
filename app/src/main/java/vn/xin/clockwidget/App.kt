package vn.xin.clockwidget

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Việc đầu tiên: xem lần chạy trước có chết giữa chừng không (xem Guard).
        Guard.checkPrevious(this)
        CrashLog.install(this)
    }
}
