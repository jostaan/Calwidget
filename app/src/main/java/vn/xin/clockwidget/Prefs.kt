package vn.xin.clockwidget

import android.content.Context

object Prefs {
    const val FONT = "font"
    const val CLOCK_SP = "clockSp"
    const val INFO_SP = "infoSp"
    const val TEXT_COLOR = "textColor"
    const val BG_COLOR = "bgColor"
    const val BG_ALPHA = "bgAlpha"
    const val ALIGN = "align"
    const val H24 = "h24"
    const val LONG_DATE = "longDate"
    const val SHOW_LUNAR = "showLunar"
    const val LUNAR_DETAIL = "lunarDetail"
    const val SHOW_EVENTS = "showEvents"
    const val SHOW_WEATHER = "showWeather"
    const val SHOW_EXTRA = "showExtra"
    const val LOCK_CARD = "lockCard"
    const val LOCK_STYLE = "lockStyle"
    const val LOCK_COLOR = "lockColor"
    const val LOC_NAME = "locName"
    const val LAT = "lat"
    const val LON = "lon"
    const val W_JSON = "wJson"
    const val W_TIME = "wTime"
    const val W_AQI = "wAqi"
    const val LAST_ERR = "lastErr"
    const val PAD_DP = "padDp"
    const val CORNER = "corner"
    const val LAST_CRASH = "lastCrash"

    const val DEF_LAT = 10.7629
    const val DEF_LON = 106.6500

    fun sp(c: Context) = c.getSharedPreferences("clockwidget", Context.MODE_PRIVATE)

    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)
    fun str(c: Context, k: String, d: String): String = sp(c).getString(k, d) ?: d
    fun long(c: Context, k: String, d: Long): Long = sp(c).getLong(k, d)

    fun putInt(c: Context, k: String, v: Int) {
        sp(c).edit().putInt(k, v).apply()
    }

    fun putBool(c: Context, k: String, v: Boolean) {
        sp(c).edit().putBoolean(k, v).apply()
    }

    fun putStr(c: Context, k: String, v: String) {
        sp(c).edit().putString(k, v).apply()
    }

    fun putLong(c: Context, k: String, v: Long) {
        sp(c).edit().putLong(k, v).apply()
    }

    fun font(c: Context): Int = int(c, FONT, 0)
    fun clockSp(c: Context): Int = int(c, CLOCK_SP, 56)
    fun infoSp(c: Context): Int = int(c, INFO_SP, 14)
    fun textColor(c: Context): Int = int(c, TEXT_COLOR, -1)
    fun bgColor(c: Context): Int = int(c, BG_COLOR, -16777216)
    fun bgAlpha(c: Context): Int = int(c, BG_ALPHA, 35)
    fun align(c: Context): Int = int(c, ALIGN, 0)
    fun is24h(c: Context): Boolean = bool(c, H24, true)
    fun longDate(c: Context): Boolean = bool(c, LONG_DATE, false)
    fun showLunar(c: Context): Boolean = bool(c, SHOW_LUNAR, true)
    fun lunarDetail(c: Context): Boolean = bool(c, LUNAR_DETAIL, true)
    fun showEvents(c: Context): Boolean = bool(c, SHOW_EVENTS, true)
    fun showWeather(c: Context): Boolean = bool(c, SHOW_WEATHER, true)
    fun showExtra(c: Context): Boolean = bool(c, SHOW_EXTRA, true)
    fun lockCard(c: Context): Boolean = bool(c, LOCK_CARD, false)
    fun lockStyle(c: Context): Int = int(c, LOCK_STYLE, 0)
    fun lockColor(c: Context): Int = int(c, LOCK_COLOR, 0)
    fun locName(c: Context): String = str(c, LOC_NAME, "Quận 11")
    fun padDp(c: Context): Int = int(c, PAD_DP, 6).coerceIn(0, 24)
    fun corner(c: Context): Int = int(c, CORNER, 2).coerceIn(0, 3)

    private fun parseCoord(s: String, min: Double, max: Double, def: Double): Double {
        val v = s.trim().replace(',', '.').toDoubleOrNull() ?: return def
        return if (v < min || v > max) def else v
    }

    fun lat(c: Context): Double = parseCoord(str(c, LAT, DEF_LAT.toString()), -90.0, 90.0, DEF_LAT)
    fun lon(c: Context): Double = parseCoord(str(c, LON, DEF_LON.toString()), -180.0, 180.0, DEF_LON)
}
