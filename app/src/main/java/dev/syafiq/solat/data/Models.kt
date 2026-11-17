package dev.syafiq.solat.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Malaysian time; JAKIM data is always for this zone, whatever the phone is set to. */
val MYT: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")

/** Order matches the JSON keys in api.waktusolat.app /v2/solat responses. */
enum class Prayer(val key: String, val label: String) {
    IMSAK("imsak", "Imsak"),
    FAJR("fajr", "Subuh"),
    SYURUK("syuruk", "Sunrise"),
    DHUHA("dhuha", "Dhuha"),
    DHUHR("dhuhr", "Zohor"),
    ASR("asr", "Asar"),
    MAGHRIB("maghrib", "Maghrib"),
    ISHA("isha", "Isyak"),
}

/** Prayers the countdown targets (Imsak and Dhuha are shown but not counted down to). */
val COUNTDOWN = listOf(Prayer.FAJR, Prayer.SYURUK, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

data class Zone(val code: String, val state: String, val district: String)

data class PrayerDay(val date: LocalDate, val hijri: String, val times: Map<Prayer, Instant>)

fun parseMonth(json: String): List<PrayerDay> {
    val o = JSONObject(json)
    val year = o.getInt("year")
    val month = o.getInt("month_number")
    val arr = o.getJSONArray("prayers")
    return (0 until arr.length()).map { i ->
        val p = arr.getJSONObject(i)
        PrayerDay(
            date = LocalDate.of(year, month, p.getInt("day")),
            hijri = p.getString("hijri"),
            // API gives epoch seconds
            times = Prayer.entries.associateWith { Instant.ofEpochSecond(p.getLong(it.key)) },
        )
    }
}

fun parseZones(json: String): List<Zone> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map {
        val z = arr.getJSONObject(it)
        Zone(z.getString("jakimCode").trim(), z.getString("negeri").trim(), z.getString("daerah").trim())
    }
}

fun parseGpsZone(json: String): Zone {
    val o = JSONObject(json)
    return Zone(o.getString("zone"), o.getString("state"), o.getString("district"))
}

/** Next prayer in [COUNTDOWN] after [now], looking at today then tomorrow. */
fun nextPrayer(days: Map<LocalDate, PrayerDay>, now: Instant): Pair<Prayer, Instant>? {
    val today = now.atZone(MYT).toLocalDate()
    for (d in listOf(today, today.plusDays(1))) {
        val day = days[d] ?: continue
        for (p in COUNTDOWN) {
            val t = day.times.getValue(p)
            if (t.isAfter(now)) return p to t
        }
    }
    return null
}

private val HIJRI_MONTHS = listOf(
    "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
    "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah",
)

/** "1448-05-23" -> "23 Jamadilawal 1448" */
fun formatHijri(iso: String): String {
    val (y, m, d) = iso.split("-").map { it.toInt() }
    return "$d ${HIJRI_MONTHS[m - 1]} $y"
}

/** Widget dot (0..143) holding [t]: the day in 10-minute slots from midnight, Malaysian time. */
fun dotOf(t: Instant): Int = t.atZone(MYT).let { (it.hour * 60 + it.minute) / 10 }

/** "1h 20m" / "45m", rounding up so it never shows 0m before the prayer. */
fun countdown(from: Instant, to: Instant): String {
    val m = (Duration.between(from, to).seconds + 59) / 60
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
