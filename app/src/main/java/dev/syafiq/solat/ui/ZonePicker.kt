package dev.syafiq.solat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.syafiq.solat.MainViewModel
import dev.syafiq.solat.data.Zone

@Composable
fun ZonePicker(vm: MainViewModel, onAuto: () -> Unit, onPick: (Zone) -> Unit, onClose: () -> Unit) {
    LaunchedEffect(Unit) { vm.loadZones() }
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val filtered = vm.zones.filter {
        q.isEmpty() || it.code.contains(q, true) || it.state.contains(q, true) || it.district.contains(q, true)
    }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dots("ZONE", size = 28.sp, modifier = Modifier.weight(1f))
            Label("Close", caps = true, size = 12.sp, medium = true, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.faint, RoundedCornerShape(24.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            if (query.isEmpty()) Label("Search district, state or code", color = colors.dim)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = SansFont, fontSize = 14.sp, color = colors.fg),
                cursorBrush = SolidColor(Red),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))

        // pinned: never scrolls away
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onAuto).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(Red, CircleShape))
            Spacer(Modifier.width(12.dp))
            Label("Use my location", medium = true, size = 16.sp)
        }
        vm.zone?.let { z ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.faint, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Label("Selected", caps = true, size = 11.sp, color = colors.dim)
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dots(z.code, size = 16.sp, color = Red)
                        Spacer(Modifier.width(10.dp))
                        Label((if (vm.autoZone) vm.place else null) ?: z.district, size = 14.sp, maxLines = 1)
                    }
                }
                Pill(if (vm.autoZone) "AUTO" else "MANUAL", active = vm.autoZone)
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(colors.faint))

        LazyColumn(Modifier.weight(1f)) {
            when {
                vm.zones.isEmpty() && vm.busy -> item { Label("Loading zones…", color = colors.dim, modifier = Modifier.padding(vertical = 16.dp)) }
                vm.zones.isEmpty() -> item {
                    Label(
                        (vm.error ?: "Couldn't load zones") + "  ·  tap to retry",
                        color = Red, size = 13.sp,
                        modifier = Modifier.clickable { vm.loadZones() }.padding(vertical = 16.dp),
                    )
                }
            }
            filtered.groupBy { it.state }.forEach { (state, list) ->
                item(key = "h_$state") {
                    Label(state, caps = true, size = 11.sp, color = colors.dim, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
                }
                items(list, key = { it.code }) { z -> ZoneRow(z, selected = z.code == vm.zone?.code) { onPick(z) } }
            }
        }
    }
}

@Composable
private fun ZoneRow(z: Zone, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dots(z.code, size = 16.sp, color = if (selected) Red else colors.fg, modifier = Modifier.width(64.dp))
        Label(z.district, size = 14.sp, color = if (selected) colors.fg else colors.dim, modifier = Modifier.weight(1f))
    }
}
