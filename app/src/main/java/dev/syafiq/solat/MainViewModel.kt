package dev.syafiq.solat

import android.app.Application
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.syafiq.solat.data.MYT
import dev.syafiq.solat.data.PrayerDay
import dev.syafiq.solat.data.Repo
import dev.syafiq.solat.data.Zone
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repo(app)

    var zone by mutableStateOf(repo.zone); private set
    var autoZone by mutableStateOf(repo.autoZone); private set
    var days by mutableStateOf<Map<LocalDate, PrayerDay>>(emptyMap()); private set
    var location by mutableStateOf<Location?>(null); private set
    var zones by mutableStateOf<List<Zone>>(emptyList()); private set
    private var jobs by mutableStateOf(0)
    val busy get() = jobs > 0
    var error by mutableStateOf<String?>(null); private set

    init {
        if (zone != null) load()
    }

    /** Called once the location permission prompt resolves. */
    fun onPermission(granted: Boolean) {
        when {
            granted && (autoZone || zone == null) -> detect()
            granted -> refreshLocation()
            autoZone && zone != null -> error = "Location is off, showing your last zone"
        }
    }

    fun useAuto() {
        autoZone = true; repo.autoZone = true
    }

    fun pick(z: Zone) {
        autoZone = false; repo.autoZone = false
        applyZone(z)
    }

    fun loadZones() {
        if (zones.isNotEmpty()) return
        launchTask { zones = repo.zones() }
    }

    fun load(force: Boolean = false) {
        val code = zone?.code ?: return
        launchTask {
            val today = LocalDate.now(MYT)
            val ym = YearMonth.from(today)
            var list = repo.month(code, ym, force)
            // last day of the month: tomorrow's Subuh lives in next month's table
            if (today == ym.atEndOfMonth()) list = list + runCatching { repo.month(code, ym.plusMonths(1), force) }.getOrDefault(emptyList())
            days = list.associateBy { it.date }
        }
    }

    private fun detect() = launchTask {
        val loc = repo.location() ?: throw IllegalStateException("Couldn't get your location. Turn on location or pick a zone.")
        location = loc
        applyZone(repo.zoneAt(loc.latitude, loc.longitude))
    }

    private fun refreshLocation() = viewModelScope.launch {
        runCatching { repo.location() }.getOrNull()?.let { location = it }
    }

    private fun applyZone(z: Zone) {
        val changed = z.code != zone?.code
        zone = z; repo.zone = z
        if (changed || days.isEmpty()) load()
    }

    private fun launchTask(block: suspend () -> Unit) = viewModelScope.launch {
        jobs++; error = null
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            error = if (e.message?.startsWith("Server") == true) e.message else "No internet connection"
        } catch (e: Exception) {
            error = e.message ?: "Something went wrong"
        } finally {
            jobs--
        }
    }
}
