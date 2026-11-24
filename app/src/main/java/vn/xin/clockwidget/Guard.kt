package vn.xin.clockwidget

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dấu vết "đang ở bước nào" để phát hiện process chết đột ngột (lỗi native, bị hệ thống giết).
 * Bẫy lỗi Java không ghi được những trường hợp đó, nên mỗi bước nguy cơ được đánh dấu trước khi chạy
 * và xóa dấu khi chạy xong. Lần khởi động process sau, dấu còn sót lại nghĩa là bước đó đã chết giữa chừng.
 */
object Guard {
    const val STAGE_PREVIEW = "preview"
    const val STAGE_WIDGET = "draw-widget"
    const val STAGE_LOCK = "lock-card"
    const val STAGE_MAIN = "main-build"
    const val STAGE_WEATHER = "weather"
    const val STAGE_SCHEDULE = "schedule"

    const val SAFE_DRAW = "safeDraw"

    fun enter(c: Context, stage: String) {
        try {
            Prefs.sp(c).edit().putLong(Prefs.INFLIGHT_PREFIX + stage, System.currentTimeMillis()).commit()
        } catch (t: Throwable) {
            // Không làm gì thêm.
        }
    }

    fun leave(c: Context, stage: String) {
        try {
            Prefs.sp(c).edit().remove(Prefs.INFLIGHT_PREFIX + stage).commit()
        } catch (t: Throwable) {
            // Không làm gì thêm.
        }
    }

    /** Chạy block giữa enter và leave. Lỗi Java vẫn được ném tiếp (nơi gọi tự bắt), dấu vẫn được xóa. */
    inline fun <T> run(c: Context, stage: String, block: () -> T): T {
        enter(c, stage)
        try {
            return block()
        } finally {
            leave(c, stage)
        }
    }

    /**
     * Gọi một lần khi process khởi động (App.onCreate), trước mọi bước khác.
     * Nếu còn dấu từ lần chạy trước: ghi chú lại và tự tắt phần nghi ngờ để app dùng được.
     */
    fun checkPrevious(c: Context) {
        try {
            val all = Prefs.sp(c).all
            val dead = ArrayList<String>()
            for ((k, v) in all) {
                if (k.startsWith(Prefs.INFLIGHT_PREFIX)) {
                    val stage = k.removePrefix(Prefs.INFLIGHT_PREFIX)
                    val t = (v as? Long) ?: 0L
                    dead.add(stage + " (" + SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date(t)) + ")")
                }
            }
            if (dead.isEmpty()) return

            val ed = Prefs.sp(c).edit()
            for ((k, _) in all) {
                if (k.startsWith(Prefs.INFLIGHT_PREFIX)) ed.remove(k)
            }
            val actions = ArrayList<String>()
            for (d in dead) {
                val stage = d.substringBefore(' ')
                when (stage) {
                    STAGE_PREVIEW -> {
                        ed.putBoolean(Prefs.PREVIEW_OFF, true)
                        actions.add("đã tắt bản xem trước")
                    }
                    STAGE_WIDGET -> {
                        ed.putBoolean(SAFE_DRAW, true)
                        actions.add("widget chuyển sang chế độ an toàn")
                    }
                    STAGE_LOCK -> {
                        ed.putBoolean(Prefs.LOCK_CARD, false)
                        actions.add("đã tắt thẻ màn hình khóa")
                    }
                }
            }
            val note = "Process kết thúc đột ngột khi đang ở bước: " + dead.joinToString(", ") +
                (if (actions.isEmpty()) "" else ". Đã xử lý: " + actions.joinToString(", ")) + "."
            ed.putString(Prefs.DEATH_NOTE, note)
            ed.commit()
        } catch (t: Throwable) {
            // Không làm gì thêm.
        }
    }

    fun deathNote(c: Context): String = Prefs.str(c, Prefs.DEATH_NOTE, "")

    fun safeDraw(c: Context): Boolean = Prefs.bool(c, SAFE_DRAW, false)

    fun clearNotes(c: Context) {
        Prefs.sp(c).edit().remove(Prefs.DEATH_NOTE).commit()
    }
}
