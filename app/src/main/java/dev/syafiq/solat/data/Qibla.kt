package dev.syafiq.solat.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Same great-circle formula as the reference app's QiblaMath. */
object Qibla {
    private const val KAABA_LAT = 21.422487
    private const val KAABA_LNG = 39.826206

    fun normalize(deg: Double): Double = ((deg % 360) + 360) % 360

    /** Initial bearing from (lat, lng) to the Kaaba, clockwise from true north. */
    fun bearing(lat: Double, lng: Double): Double {
        val phi = Math.toRadians(lat)
        val phiK = Math.toRadians(KAABA_LAT)
        val dLng = Math.toRadians(KAABA_LNG - lng)
        val y = sin(dLng) * cos(phiK)
        val x = cos(phi) * sin(phiK) - sin(phi) * cos(phiK) * cos(dLng)
        return normalize(Math.toDegrees(atan2(y, x)))
    }

    /** Signed shortest turn from heading to bearing, in [-180, 180). Positive = turn right. */
    fun turn(heading: Double, bearing: Double): Double = normalize(bearing - heading + 180) - 180
}
