package dev.syafiq.solat.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.YearMonth
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Talks to api.waktusolat.app (same backend as the reference app) and caches in SharedPreferences. */
class Repo(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("solat", Context.MODE_PRIVATE)

    var zone: Zone?
        get() = prefs.getString("zone", null)?.split("|")?.takeIf { it.size == 3 }
            ?.let { Zone(it[0], it[1], it[2]) }
        set(z) { prefs.edit().putString("zone", z?.let { "${it.code}|${it.state}|${it.district}" }).apply() }

    var autoZone: Boolean
        get() = prefs.getBoolean("auto", true)
        set(v) { prefs.edit().putBoolean("auto", v).apply() }

    suspend fun month(zone: String, ym: YearMonth, force: Boolean = false): List<PrayerDay> {
        val key = "m_${zone}_$ym"
        if (!force) prefs.getString(key, null)?.let { return parseMonth(it) }
        return try {
            val body = get("/v2/solat/$zone?year=${ym.year}&month=${ym.monthValue}")
            parseMonth(body).also { prefs.edit().putString(key, body).apply() } // parse first so bad data is never cached
        } catch (e: Exception) {
            prefs.getString(key, null)?.let { parseMonth(it) } ?: throw e
        }
    }

    suspend fun zones(): List<Zone> {
        prefs.getString("zones", null)?.let { return parseZones(it) }
        val body = get("/zones")
        return parseZones(body).also { prefs.edit().putString("zones", body).apply() }
    }

    suspend fun zoneAt(lat: Double, lng: Double): Zone = parseGpsZone(get("/zones/$lat/$lng"))

    @SuppressLint("MissingPermission") // callers check permission first
    suspend fun location(): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) } ?: return lastKnown(lm)
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    lm.getCurrentLocation(provider, cancel, executor) { cont.resume(it) }
                } else {
                    val listener = LocationListener { if (cont.isActive) cont.resume(it) }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        }
        return fresh ?: lastKnown(lm)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val c = URL("https://api.waktusolat.app$path").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            if (c.responseCode != 200) throw IOException("Server replied ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        val executor = Executors.newSingleThreadExecutor()
    }
}
