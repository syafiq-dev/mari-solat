package dev.syafiq.solat

import dev.syafiq.solat.data.Prayer
import dev.syafiq.solat.data.Qibla
import dev.syafiq.solat.data.formatHijri
import dev.syafiq.solat.data.nextPrayer
import dev.syafiq.solat.data.parseGpsZone
import dev.syafiq.solat.data.parseMonth
import dev.syafiq.solat.data.parseZones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class LogicTest {
    private fun epoch(d: String) = LocalDateTime.parse(d).atZone(dev.syafiq.solat.data.MYT).toEpochSecond()

    private fun day(day: Int, date: String) = """
        {"day":$day,"hijri":"1448-05-${22 + day}",
         "imsak":${epoch("${date}T05:40")},"fajr":${epoch("${date}T05:50")},"syuruk":${epoch("${date}T07:00")},
         "dhuha":${epoch("${date}T07:25")},"dhuhr":${epoch("${date}T13:00")},"asr":${epoch("${date}T16:20")},
         "maghrib":${epoch("${date}T19:05")},"isha":${epoch("${date}T20:15")}}
    """

    private val month = """{"zone":"SGR01","year":2026,"month":"NOV","month_number":11,"last_updated":null,
        "prayers":[${day(14, "2026-11-14")},${day(15, "2026-11-15")}]}"""

    @Test fun parsesMonth() {
        val days = parseMonth(month)
        assertEquals(LocalDate.of(2026, 11, 14), days[0].date)
        assertEquals(epoch("2026-11-14T16:20"), days[0].times.getValue(Prayer.ASR).epochSecond)
    }

    @Test fun nextPrayerRollsToTomorrow() {
        val days = parseMonth(month).associateBy { it.date }
        val afternoon = LocalDateTime.parse("2026-11-14T14:00").atZone(dev.syafiq.solat.data.MYT).toInstant()
        assertEquals(Prayer.ASR, nextPrayer(days, afternoon)!!.first)
        val night = LocalDateTime.parse("2026-11-14T21:00").atZone(dev.syafiq.solat.data.MYT).toInstant()
        val (p, at) = nextPrayer(days, night)!!
        assertEquals(Prayer.FAJR, p)
        assertEquals(epoch("2026-11-15T05:50"), at.epochSecond)
        val lastNight = LocalDateTime.parse("2026-11-15T21:00").atZone(dev.syafiq.solat.data.MYT).toInstant()
        assertNull(nextPrayer(days, lastNight))
    }

    @Test fun parsesZones() {
        assertEquals("Gombak", parseZones("""[{"jakimCode":"SGR01","negeri":"Selangor ","daerah":"Gombak"}]""")[0].district)
        assertEquals("SGR01", parseGpsZone("""{"zone":"SGR01","state":"Selangor","district":"Gombak"}""").code)
    }

    @Test fun qiblaFromKualaLumpur() {
        // KL's qibla is about 292.5° from true north
        assertEquals(292.5, Qibla.bearing(3.139, 101.6869), 0.5)
        assertEquals(-10.0, Qibla.turn(300.0, 290.0), 1e-9)
        assertEquals(20.0, Qibla.turn(350.0, 10.0), 1e-9)
    }

    @Test fun hijri() = assertEquals("23 Jamadilawal 1448", formatHijri("1448-05-23"))
}
