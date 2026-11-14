package dev.syafiq.solat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.syafiq.solat.ui.PrayerScreen
import dev.syafiq.solat.ui.QiblaScreen
import dev.syafiq.solat.ui.Red
import dev.syafiq.solat.ui.SolatTheme
import dev.syafiq.solat.ui.ZonePicker
import dev.syafiq.solat.ui.colors
import dev.syafiq.solat.ui.dotGrid
import dev.syafiq.solat.ui.Dots

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clear = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = clear, navigationBarStyle = clear)
        setContent { SolatTheme { App(vm) } }
    }
}

private val LOCATION_PERMS = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

private fun Context.hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.locationOn() = LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java))

@Composable
private fun App(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var picking by rememberSaveable { mutableStateOf(false) }

    val activity = LocalContext.current as ComponentActivity
    var fromButton by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val granted = res.values.any { it }
        when {
            // Android stops showing the prompt after two denials, so send the user to app settings instead
            !granted && fromButton && !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) ->
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
            granted && fromButton && !activity.locationOn() -> activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
        fromButton = false
        vm.onPermission(granted)
        if (!granted && vm.zone == null) picking = true
    }
    val askLocation = {
        fromButton = true
        permission.launch(LOCATION_PERMS)
    }
    LaunchedEffect(Unit) { permission.launch(LOCATION_PERMS) }
    // back from the settings screens: retry if location is now usable
    LifecycleResumeEffect(Unit) {
        if (vm.location == null && activity.hasLocationPermission() && activity.locationOn()) vm.onPermission(true)
        onPauseOrDispose { }
    }

    Box(
        Modifier
            .fillMaxSize()
            .dotGrid(colors.bg, colors.faint)
            .systemBarsPadding(),
    ) {
        if (picking) {
            BackHandler { picking = false }
            ZonePicker(
                vm = vm,
                onAuto = {
                    picking = false
                    vm.useAuto() // detection runs once the permission result comes back
                    askLocation()
                },
                onPick = { vm.pick(it); picking = false },
                onClose = { picking = false },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> PrayerScreen(vm, onChangeZone = { picking = true }, onNeedLocation = askLocation)
                        else -> QiblaScreen(vm, onNeedLocation = askLocation)
                    }
                }
                TabBar(tab) { tab = it }
            }
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf("WAKTU", "KIBLAT").forEachIndexed { i, name ->
            val on = i == selected
            Column(
                Modifier
                    .clickable { onSelect(i) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Dots(name, size = 18.sp, color = if (on) colors.fg else colors.dim)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(6.dp)
                        .background(if (on) Red else Color.Transparent, CircleShape),
                )
            }
        }
    }
}
