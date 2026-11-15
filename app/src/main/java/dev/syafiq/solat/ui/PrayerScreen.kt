package dev.syafiq.solat.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.syafiq.solat.MainViewModel
import dev.syafiq.solat.data.MYT
import dev.syafiq.solat.data.Prayer
import dev.syafiq.solat.data.formatHijri
import dev.syafiq.solat.data.nextPrayer
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PrayerScreen(vm: MainViewModel, onChangeZone: () -> Unit, onNeedLocation: () -> Unit) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000L - now.toEpochMilli() % 1000)
        }
    }
    val today = now.atZone(MYT).toLocalDate()
    // reload when the date rolls past midnight or month table runs out
    LaunchedEffect(today) { if (vm.zone != null && vm.days[today] == null && !vm.busy) vm.load() }

    val day = vm.days[today]
    val next = nextPrayer(vm.days, now)
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val timeFmt = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", Locale.ENGLISH) }
    val ampm = remember { DateTimeFormatter.ofPattern("a", Locale.ENGLISH) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        // Zone header
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onChangeZone)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Label(
                    vm.zone?.let { "${it.code} · ${it.state}" } ?: "No zone",
                    color = colors.dim, size = 12.sp, caps = true,
                )
                Spacer(Modifier.height(4.dp))
                Label(
                    (if (vm.autoZone) vm.place else null) ?: vm.zone?.district ?: "Tap to choose your zone",
                    size = 16.sp, medium = true, maxLines = 2,
                )
            }
            Spacer(Modifier.width(12.dp))
            Pill(if (vm.autoZone) "AUTO" else "MANUAL", active = vm.autoZone)
        }

        Spacer(Modifier.height(36.dp))
        Label(
            today.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).uppercase() + "  |  " +
                today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)),
            size = 20.sp,
        )
        day?.let { Label(formatHijri(it.hijri), color = colors.dim, size = 20.sp) }

        // Countdown
        Spacer(Modifier.height(28.dp))
        if (next != null) {
            val (prayer, at) = next
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(Red, CircleShape))
                Spacer(Modifier.width(10.dp))
                Label("${prayer.label} in", caps = true, size = 16.sp, medium = true)
            }
            Spacer(Modifier.height(4.dp))
            Dots(countdown(Duration.between(now, at)), size = 64.sp)
        } else {
            Dots(if (vm.busy) "LOADING" else "--:--:--", size = 48.sp, color = colors.dim)
        }

        vm.error?.let {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(it, color = Red, size = 13.sp, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(12.dp))
                Label("Retry", caps = true, size = 12.sp, medium = true, modifier = Modifier
                    .clickable {
                        when {
                            vm.autoZone && vm.location == null -> onNeedLocation()
                            vm.zone == null -> onChangeZone()
                            else -> vm.load(force = true)
                        }
                    }
                    .padding(8.dp))
            }
        }

        // Times
        Spacer(Modifier.height(32.dp))
        if (day != null) {
            val nextPrayer = next?.takeIf { it.second.atZone(MYT).toLocalDate() == today }?.first

            // secondary times: one balanced row of three equal cells
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.faint, RoundedCornerShape(8.dp))
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SECONDARY.forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.width(1.dp).height(32.dp).background(colors.dim.copy(alpha = 0.3f)))
                    val zt = day.times.getValue(p).atZone(MYT)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Label(p.label, caps = true, size = 11.sp, color = if (p == nextPrayer) Red else colors.dim)
                        Spacer(Modifier.height(4.dp))
                        Dots(zt.format(timeFmt) + if (is24h) "" else zt.format(ampm).lowercase(), size = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            MAIN.forEach { p ->
                val t = day.times.getValue(p)
                val isNext = p == nextPrayer
                val passed = !t.isAfter(now)
                val tint = when {
                    isNext -> colors.fg
                    passed -> colors.dim
                    else -> colors.fg
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(if (isNext) Red else Color.Transparent, CircleShape))
                    Spacer(Modifier.width(12.dp))
                    Label(p.label, size = 17.sp, color = tint, medium = isNext, modifier = Modifier.weight(1f))
                    val zt = t.atZone(MYT)
                    Dots(zt.format(timeFmt), size = 24.sp, color = tint)
                    if (!is24h) {
                        Spacer(Modifier.width(6.dp))
                        Label(zt.format(ampm), size = 11.sp, color = colors.dim, modifier = Modifier.width(22.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.faint))
            }
        }

        Spacer(Modifier.height(28.dp))
        Label("Times by JAKIM via api.waktusolat.app", size = 11.sp, color = colors.dim)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Pill(text: String, active: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(colors.faint, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(5.dp).background(if (active) Red else colors.dim, CircleShape))
        Label(text, size = 11.sp, medium = true)
    }
}

private val SECONDARY = listOf(Prayer.IMSAK, Prayer.SYURUK, Prayer.DHUHA)
private val MAIN = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

internal fun countdown(d: Duration): String {
    val s = d.seconds.coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
}
