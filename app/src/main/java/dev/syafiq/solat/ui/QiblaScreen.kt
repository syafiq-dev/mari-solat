package dev.syafiq.solat.ui

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.syafiq.solat.MainViewModel
import dev.syafiq.solat.data.Qibla
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val ALIGNED_DEG = 3.0

@Composable
fun QiblaScreen(vm: MainViewModel, onNeedLocation: () -> Unit) {
    val loc = vm.location
    val declination = remember(loc) {
        loc?.let { GeomagneticField(it.latitude.toFloat(), it.longitude.toFloat(), it.altitude.toFloat(), System.currentTimeMillis()).declination } ?: 0f
    }
    val compass = rememberCompass(declination)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Label("Kiblat", caps = true, size = 24.sp, color = colors.dim, modifier = Modifier.fillMaxWidth())

        if (!compass.available) {
            Spacer(Modifier.weight(1f))
            Label("This phone has no compass sensor.", color = colors.dim, align = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            return@Column
        }
        if (loc == null) {
            Spacer(Modifier.weight(1f))
            Dots("NO FIX", size = 40.sp, color = colors.dim)
            Spacer(Modifier.height(12.dp))
            Label("Qibla needs your location.", color = colors.dim)
            Spacer(Modifier.height(16.dp))
            Pill("Allow location", active = true, modifier = Modifier.clickable(onClick = onNeedLocation))
            Spacer(Modifier.weight(1f))
            return@Column
        }

        val bearing = remember(loc) { Qibla.bearing(loc.latitude, loc.longitude) }
        val turn = Qibla.turn(compass.heading.toDouble(), bearing)
        val aligned = abs(turn) <= ALIGNED_DEG

        Spacer(Modifier.height(24.dp))
        Dots("${Qibla.normalize(compass.heading.toDouble()).roundToInt()}°", size = 56.sp, color = if (aligned) Red else colors.fg)
        Label(
            when {
                aligned -> "Facing Qibla"
                turn > 0 -> "Turn ${abs(turn).roundToInt()}° right"
                else -> "Turn ${abs(turn).roundToInt()}° left"
            },
            caps = true, size = 12.sp, color = colors.dim,
        )

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Dial(heading = compass.heading, bearing = bearing.toFloat(), aligned = aligned)
        }

        if (compass.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            Label("Compass not calibrated. Move your phone in a figure 8, away from magnets and metal.", size = 12.sp, color = Red, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Label(
            "Qibla ${"%.1f".format(bearing)}° from true north  ·  ${"%.4f".format(loc.latitude)}, ${"%.4f".format(loc.longitude)}",
            size = 11.sp, color = colors.dim, align = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Dial(heading: Float, bearing: Float, aligned: Boolean) {
    val c = colors
    val measurer = rememberTextMeasurer()
    // unwrap so the dial never spins the long way round when crossing north
    val unwrapped = remember { floatArrayOf(heading) } // plain holder: updating it must not trigger recomposition
    unwrapped[0] += Qibla.turn(Qibla.normalize(unwrapped[0].toDouble()), heading.toDouble()).toFloat()
    val rot by animateFloatAsState(-unwrapped[0], spring(stiffness = 120f), label = "dial")

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(12.dp),
    ) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)

        // fixed marker: where the phone points
        drawCircle(c.fg, 3.dp.toPx(), Offset(center.x, center.y - r - 2.dp.toPx()))

        rotate(rot, center) {
            // 72 dots, one every 5°, bigger every 30°
            for (i in 0 until 72) {
                val a = Math.toRadians(i * 5.0 - 90)
                val big = i % 6 == 0
                val rr = r - 8.dp.toPx()
                drawCircle(
                    if (big) c.fg else c.dim,
                    (if (big) 2.4f else 1.4f).dp.toPx(),
                    Offset(center.x + rr * cos(a).toFloat(), center.y + rr * sin(a).toFloat()),
                )
            }
            listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, deg) ->
                val a = Math.toRadians(deg - 90.0)
                val rr = r - 36.dp.toPx() // inset so the letters clear the tick ring
                val layout = measurer.measure(label, TextStyle(fontFamily = DotFont, fontWeight = FontWeight.Bold, fontSize = 23.sp, color = if (label == "N") c.fg else c.dim))
                val p = Offset(center.x + rr * cos(a).toFloat(), center.y + rr * sin(a).toFloat())
                rotate(-rot, p) { // keep letters upright
                    drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
                }
            }
            // Qibla: dotted line from centre to a red dot on the ring
            val qa = Math.toRadians(bearing - 90.0)
            val steps = 9
            for (s in 1..steps) {
                val rr = (r - 56.dp.toPx()) * s / steps
                drawCircle(if (aligned) Red else c.dim, 2.dp.toPx(), Offset(center.x + rr * cos(qa).toFloat(), center.y + rr * sin(qa).toFloat()))
            }
            val qr = r - 8.dp.toPx()
            drawCircle(Red, 9.dp.toPx(), Offset(center.x + qr * cos(qa).toFloat(), center.y + qr * sin(qa).toFloat()))
        }
        drawCircle(if (aligned) Red else c.fg, 6.dp.toPx(), center)
    }
}

private class CompassState {
    var heading by mutableFloatStateOf(0f)
    var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    var available by mutableStateOf(true)
}

/** Heading in degrees from true north, via the rotation-vector sensor plus magnetic declination. */
@Composable
private fun rememberCompass(declination: Float): CompassState {
    val ctx = LocalContext.current
    val state = remember { CompassState() }
    DisposableEffect(declination) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            state.available = false
            return@DisposableEffect onDispose { }
        }
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, orient)
                val deg = Math.toDegrees(orient[0].toDouble()).toFloat() + declination
                state.heading = Qibla.normalize(deg.toDouble()).toFloat()
            }

            override fun onAccuracyChanged(s: Sensor, accuracy: Int) = Unit
        }
        // the rotation vector rarely reports accuracy itself; the magnetometer's tells us when calibration is off
        val magListener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                state.accuracy = e.accuracy
            }

            override fun onAccuracyChanged(s: Sensor, accuracy: Int) {
                state.accuracy = accuracy
            }
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sm.registerListener(magListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        onDispose {
            sm.unregisterListener(listener)
            sm.unregisterListener(magListener)
        }
    }
    return state
}
