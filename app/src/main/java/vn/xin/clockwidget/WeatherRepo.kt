package vn.xin.clockwidget

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class Weather(
    val tempC: Double,
    val feelsC: Double,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int,
    val windKmh: Double,
    val maxC: Double,
    val minC: Double,
    val rainTodayPct: Int,
    val rainNext6hPct: Int,
    val uvMax: Double,
    val aqi: Int,
    val fetchedAt: Long
)

/** Lấy thời tiết từ Open-Meteo (không cần khóa API, dùng phi thương mại) và lưu bản sao để dùng khi mất mạng. */
object WeatherRepo {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val AIR = "https://air-quality-api.open-meteo.com/v1/air-quality"

    /** Lý do thất bại của lần httpGet gần nhất (hiện ở thanh trạng thái để biết vì sao không có thời tiết). */
    @Volatile
    private var httpErr = ""

    private fun httpGetOnce(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "ClockWidget/1.2 (personal use)")
            val code = conn.responseCode
            if (code != 200) {
                httpErr = "HTTP $code"
                null
            } else {
                conn.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            httpErr = e.javaClass.simpleName + (e.message?.let { ": " + it.take(60) } ?: "")
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Thử tối đa 2 lần (cách nhau 2 giây): mạng vừa bật lại thường lỗi nhanh ở lần đầu.
     * Chỉ thử lại khi lần đầu thất bại trong < 4 giây, để tổng thời gian không vượt giới hạn của receiver.
     */
    private fun httpGet(urlStr: String): String? {
        httpErr = ""
        val t0 = System.currentTimeMillis()
        var r = httpGetOnce(urlStr)
        if (r == null && System.currentTimeMillis() - t0 < 4000) {
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                // Bỏ qua.
            }
            r = httpGetOnce(urlStr)
        }
        return r
    }

    private fun parse(json: String, aqi: Int, fetchedAt: Long): Weather? {
        return try {
            val o = JSONObject(json)
            val cur = o.getJSONObject("current")
            val daily = o.getJSONObject("daily")

            val temp = cur.optDouble("temperature_2m", Double.NaN)
            if (temp.isNaN()) return null

            var rain6 = 0
            val hourly = o.optJSONObject("hourly")
            if (hourly != null) {
                val times = hourly.optJSONArray("time")
                val probs = hourly.optJSONArray("precipitation_probability")
                if (times != null && probs != null) {
                    val key = cur.optString("time", "").take(13)
                    var start = 0
                    for (i in 0 until times.length()) {
                        if (times.optString(i).startsWith(key)) {
                            start = i
                            break
                        }
                    }
                    val end = minOf(start + 6, probs.length())
                    for (i in start until end) {
                        rain6 = maxOf(rain6, probs.optInt(i, 0))
                    }
                }
            }

            val maxArr = daily.optJSONArray("temperature_2m_max")
            val minArr = daily.optJSONArray("temperature_2m_min")
            val rainArr = daily.optJSONArray("precipitation_probability_max")
            val uvArr = daily.optJSONArray("uv_index_max")

            Weather(
                tempC = temp,
                feelsC = cur.optDouble("apparent_temperature", Double.NaN),
                code = cur.optInt("weather_code", -1),
                isDay = cur.optInt("is_day", 1) == 1,
                humidity = cur.optInt("relative_humidity_2m", -1),
                windKmh = cur.optDouble("wind_speed_10m", Double.NaN),
                maxC = maxArr?.optDouble(0, Double.NaN) ?: Double.NaN,
                minC = minArr?.optDouble(0, Double.NaN) ?: Double.NaN,
                rainTodayPct = rainArr?.optInt(0, 0) ?: 0,
                rainNext6hPct = rain6,
                uvMax = uvArr?.optDouble(0, Double.NaN) ?: Double.NaN,
                aqi = aqi,
                fetchedAt = fetchedAt
            )
        } catch (e: Exception) {
            null
        }
    }

    fun load(ctx: Context): Weather? {
        val json = Prefs.str(ctx, Prefs.W_JSON, "")
        if (json.isEmpty()) return null
        return parse(json, Prefs.int(ctx, Prefs.W_AQI, -1), Prefs.long(ctx, Prefs.W_TIME, 0L))
    }

    /** Trả về true nếu lấy và lưu thành công. Gọi ngoài luồng chính. */
    fun refresh(ctx: Context): Boolean {
        val startMs = System.currentTimeMillis()
        val ll = String.format(Locale.US, "latitude=%.4f&longitude=%.4f", Prefs.lat(ctx), Prefs.lon(ctx))
        val url = FORECAST + "?" + ll +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day,wind_speed_10m" +
            "&hourly=precipitation_probability" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,uv_index_max" +
            "&timezone=auto&forecast_days=2"

        val forecast = httpGet(url)
        if (forecast == null) {
            Prefs.putStr(ctx, Prefs.LAST_ERR, "Không lấy được thời tiết (" + httpErr + ")")
            return false
        }
        if (parse(forecast, -1, 0L) == null) {
            Prefs.putStr(ctx, Prefs.LAST_ERR, "Dữ liệu thời tiết không đọc được")
            return false
        }

        var aqi = -1
        // Bỏ qua AQI nếu mạng đã chậm: dữ liệu chính quan trọng hơn và receiver có giới hạn thời gian.
        val air = if (System.currentTimeMillis() - startMs > 9000) null else httpGet(AIR + "?" + ll + "&current=us_aqi&timezone=auto")
        if (air != null) {
            try {
                aqi = JSONObject(air).getJSONObject("current").optInt("us_aqi", -1)
            } catch (e: Exception) {
                aqi = -1
            }
        }

        Prefs.putStr(ctx, Prefs.W_JSON, forecast)
        Prefs.putInt(ctx, Prefs.W_AQI, aqi)
        Prefs.putLong(ctx, Prefs.W_TIME, System.currentTimeMillis())
        Prefs.putStr(ctx, Prefs.LAST_ERR, "")
        return true
    }

    fun ageMinutes(ctx: Context): Long {
        val t = Prefs.long(ctx, Prefs.W_TIME, 0L)
        return if (t == 0L) Long.MAX_VALUE else (System.currentTimeMillis() - t) / 60000L
    }
}
