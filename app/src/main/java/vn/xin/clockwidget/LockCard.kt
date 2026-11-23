package vn.xin.clockwidget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Thẻ thông báo ghim hiển thị trên màn hình khóa.
 * Widget bên thứ ba không lên được màn hình khóa HyperOS, nên đây là cách thay thế
 * dùng API chuẩn. Kiểu 0 dùng bố cục riêng (font Google, đồng hồ chạy thật);
 * kiểu 1 là văn bản thường của hệ thống, phòng khi bố cục riêng không hiện.
 */
object LockCard {
    private const val CHANNEL = "lockcard_v1"
    private const val ID = 2001

    fun hasPermission(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "Thẻ màn hình khóa", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Ngày, âm lịch và thời tiết, hiển thị yên lặng trên màn hình khóa"
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    fun update(ctx: Context) {
        val c = ctx.applicationContext
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!Prefs.lockCard(c) || !hasPermission(c)) {
            nm.cancel(ID)
            return
        }
        ensureChannel(c)

        val open = PendingIntent.getActivity(
            c,
            13,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val b = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .setContentIntent(open)

        if (Prefs.lockStyle(c) == 0) {
            b.setStyle(Notification.DecoratedCustomViewStyle())
            b.setCustomContentView(Renderer.notifViews(c, false))
            b.setCustomBigContentView(Renderer.notifViews(c, true))
        } else {
            val lines = Renderer.buildLines(c, false)
            val title = if (lines.isNotEmpty()) lines[0].text else "Đồng hồ"
            val rest = lines.drop(1).joinToString("\n") { it.text }
            b.setContentTitle(title)
            b.setContentText(if (lines.size > 1) lines[1].text else "")
            b.setStyle(Notification.BigTextStyle().bigText(rest))
        }
        nm.notify(ID, b.build())
    }
}
