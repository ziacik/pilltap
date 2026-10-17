package com.ziacik.pilltap

import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziacik.pilltap.data.DoseSlot
import com.ziacik.pilltap.data.IntakeRecord
import com.ziacik.pilltap.data.Medicine
import com.ziacik.pilltap.data.TodayDose
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class RootScreen { TODAY, MEDICINES, HISTORY, SETTINGS }

private val Coral = Color(0xFFF26B5B)
private val Apricot = Color(0xFFFFA56E)
private val Cream = Color(0xFFFFF8F4)
private val Ink = Color(0xFF2B2026)
private val MutedInk = Color(0xFF75666E)
private val DarkBackground = Color(0xFF171215)
private val DarkSurface = Color(0xFF241C20)
private val DarkCoral = Color(0xFFFF8B7F)

private val LightColors = lightColorScheme(
    primary = Coral, onPrimary = Color.White, primaryContainer = Color(0xFFFFDED8),
    onPrimaryContainer = Ink, secondary = Apricot, onSecondary = Ink,
    background = Cream, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFF7ECE8), onSurfaceVariant = MutedInk,
)
private val DarkColors = darkColorScheme(
    primary = DarkCoral, onPrimary = Color(0xFF4B0C09),
    primaryContainer = Color(0xFF6B2722), onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFFFB68A), onSecondary = Color(0xFF4C2610),
    background = DarkBackground, onBackground = Color(0xFFF4E8EC),
    surface = DarkSurface, onSurface = Color(0xFFF4E8EC),
    surfaceVariant = Color(0xFF382D32), onSurfaceVariant = Color(0xFFD8C4CC),
)

@Composable
internal fun PillTapRoot(
    medicines: List<Medicine>,
    slotsByMedicine: Map<Long, List<DoseSlot>>,
    today: List<TodayDose>,
    history: List<IntakeRecord>,
    writingTag: Boolean,
    canExactAlarm: Boolean,
    showNfcPicker: Boolean,
    onMarkTaken: (Long) -> Unit,
    onDeleteIntake: (Long) -> Unit,
    onSaveMedicine: (Long?, String, List<Int>) -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
    onDeleteMedicine: (Long) -> Unit,
    onWriteTag: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onNfcChoice: (Long) -> Unit,
    onNfcDismiss: () -> Unit,
) {
    var screen by remember { mutableStateOf(RootScreen.TODAY) }
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    NavigationBarItem(screen == RootScreen.TODAY, { screen = RootScreen.TODAY }, { Icon(Icons.Default.Home, null) }, label = { Text("Dnes") }, colors = colors)
                    NavigationBarItem(screen == RootScreen.MEDICINES, { screen = RootScreen.MEDICINES }, { Icon(Icons.Default.AddCircle, null) }, label = { Text("Lieky") }, colors = colors)
                    NavigationBarItem(screen == RootScreen.HISTORY, { screen = RootScreen.HISTORY }, { Icon(Icons.Default.History, null) }, label = { Text("História") }, colors = colors)
                    NavigationBarItem(screen == RootScreen.SETTINGS, { screen = RootScreen.SETTINGS }, { Icon(Icons.Default.Settings, null) }, label = { Text("Nastavenia") }, colors = colors)
                }
            },
        ) { padding ->
            when (screen) {
                RootScreen.TODAY -> TodayRoot(today, onMarkTaken, onDeleteIntake, { screen = RootScreen.MEDICINES }, Modifier.padding(padding))
                RootScreen.MEDICINES -> MedicinesRoot(medicines, slotsByMedicine, onSaveMedicine, onSetActive, onDeleteMedicine, Modifier.padding(padding))
                RootScreen.HISTORY -> HistoryRoot(history, Modifier.padding(padding))
                RootScreen.SETTINGS -> SettingsRoot(writingTag, canExactAlarm, onWriteTag, onRequestExactAlarm, Modifier.padding(padding))
            }
        }
        if (showNfcPicker) {
            AlertDialog(
                onDismissRequest = onNfcDismiss,
                title = { Text("Ktorú dávku si užil?") },
                text = {
                    Column {
                        today.filter { it.record == null }.forEach { dose ->
                            TextButton(onClick = { onNfcChoice(dose.slot.id) }) {
                                Text("${dose.slot.medicineName} · ${formatMinute(dose.slot.minuteOfDay)}")
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onNfcDismiss) { Text("Zrušiť") } },
            )
        }
    }
}

@Composable
private fun AppHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_pilltap_mark), null, Modifier.size(32.dp))
            }
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TodayRoot(
    today: List<TodayDose>,
    onMarkTaken: (Long) -> Unit,
    onDeleteIntake: (Long) -> Unit,
    onAddMedicine: () -> Unit,
    modifier: Modifier,
) {
    var deleteRecord by remember { mutableStateOf<IntakeRecord?>(null) }
    val completed = today.count { it.record != null }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        AppHeader("PillTap", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.forLanguageTag("sk-SK"))))
        Spacer(Modifier.height(22.dp))
        if (today.isEmpty()) {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Žiadne aktívne lieky", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Nemáš naplánované žiadne dávky. Keď začneš niečo užívať, pridaj liek a jeho časy.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = onAddMedicine, shape = RoundedCornerShape(16.dp)) { Text("Nastaviť lieky") }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Coral, Apricot))).padding(22.dp)) {
                Column {
                    Text("Dnešné dávky", fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f))
                    Text("$completed / ${today.size}", fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (completed == today.size) "Všetko vybavené ✓" else "Každú dávku môžeš potvrdiť samostatne.", color = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(today, key = { it.slot.id }) { dose ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dose.slot.medicineName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("Plán ${formatMinute(dose.slot.minuteOfDay)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (dose.record != null) {
                                    Text("Užité o ${formatRootTime(dose.record.takenAt)}", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (dose.record == null) {
                                Button(onClick = { onMarkTaken(dose.slot.id) }) { Text("Užil som") }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    TextButton(onClick = { deleteRecord = dose.record }) { Text("Zrušiť") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (deleteRecord != null) {
        val record = deleteRecord!!
        AlertDialog(
            onDismissRequest = { deleteRecord = null },
            title = { Text("Zmazať potvrdenie?") },
            text = { Text("Dávka ${record.medicineName} sa bude znova zobrazovať ako neužitá.") },
            confirmButton = {
                TextButton(onClick = { deleteRecord = null; onDeleteIntake(record.id) }) { Text("Zmazať") }
            },
            dismissButton = { TextButton(onClick = { deleteRecord = null }) { Text("Späť") } },
        )
    }
}

@Composable
private fun MedicinesRoot(
    medicines: List<Medicine>,
    slotsByMedicine: Map<Long, List<DoseSlot>>,
    onSave: (Long?, String, List<Int>) -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    var editingMedicine by remember { mutableStateOf<Medicine?>(null) }
    var deleting by remember { mutableStateOf<Medicine?>(null) }
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        AppHeader("Moje lieky", "Liečba a denné rozpisy")
        Spacer(Modifier.height(16.dp))
        Button(onClick = { editingMedicine = null; editing = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Text("+ Pridať liek")
        }
        Spacer(Modifier.height(16.dp))
        if (medicines.isEmpty()) {
            Text("Zatiaľ žiadne lieky. Môžeš ich pridať, keď ich budeš potrebovať.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(medicines, key = { it.id }) { medicine ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(medicine.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Text(if (medicine.active) "Aktívny" else "Pozastavený", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                                Switch(checked = medicine.active, onCheckedChange = { onSetActive(medicine.id, it) })
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                slotsByMedicine[medicine.id].orEmpty().joinToString(" · ") { formatMinute(it.minuteOfDay) },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editingMedicine = medicine; editing = true }) { Text("Upraviť") }
                                TextButton(onClick = { deleting = medicine }) { Text("Odstrániť") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (editing) {
        MedicineEditor(
            medicine = editingMedicine,
            times = slotsByMedicine[editingMedicine?.id].orEmpty().map { it.minuteOfDay },
            onDismiss = { editing = false },
            onSave = { name, minutes ->
                onSave(editingMedicine?.id, name, minutes)
                editing = false
            },
        )
    }
    deleting?.let { medicine ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Odstrániť ${medicine.name}?") },
            text = { Text("Rozpis a pripomienky sa odstránia, ale história užitých dávok zostane zachovaná.") },
            confirmButton = { TextButton(onClick = { onDelete(medicine.id); deleting = null }) { Text("Odstrániť") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Zrušiť") } },
        )
    }
}

@Composable
private fun MedicineEditor(
    medicine: Medicine?,
    times: List<Int>,
    onDismiss: () -> Unit,
    onSave: (String, List<Int>) -> Unit,
) {
    var name by remember(medicine?.id) { mutableStateOf(medicine?.name.orEmpty()) }
    var chosenTimes by remember(medicine?.id) { mutableStateOf(if (medicine == null) listOf(8 * 60) else times) }
    var error by remember(medicine?.id) { mutableStateOf("") }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (medicine == null) "Nový liek" else "Upraviť liek") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = "" },
                    label = { Text("Názov lieku") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("Časy užívania", fontWeight = FontWeight.SemiBold)
                chosenTimes.sorted().forEach { minute ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            TimePickerDialog(context, { _, hour, m ->
                                chosenTimes = (chosenTimes - minute + (hour * 60 + m)).distinct().sorted()
                            }, minute / 60, minute % 60, true).show()
                        }) { Text(formatMinute(minute), fontSize = 18.sp) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { chosenTimes = chosenTimes - minute }) { Text("Odobrať") }
                    }
                }
                TextButton(onClick = {
                    TimePickerDialog(context, { _, hour, m ->
                        chosenTimes = (chosenTimes + (hour * 60 + m)).distinct().sorted()
                    }, 8, 0, true).show()
                }) { Text("+ Pridať čas") }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> error = "Zadaj názov lieku."
                    chosenTimes.isEmpty() -> error = "Zadaj aspoň jeden čas."
                    else -> onSave(name.trim(), chosenTimes.sorted())
                }
            }) { Text("Uložiť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } },
    )
}

@Composable
private fun HistoryRoot(history: List<IntakeRecord>, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        AppHeader("História", if (history.isEmpty()) "Zatiaľ bez záznamov" else "${history.size} záznamov")
        Spacer(Modifier.height(20.dp))
        if (history.isEmpty()) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text("Prvý záznam sa tu objaví po potvrdení dávky.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history, key = { it.id }) { record ->
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.medicineName, fontWeight = FontWeight.Bold)
                                Text(
                                    "${formatRootDate(record.takenAt)} · ${record.scheduledMinute?.let { formatMinute(it) + " plán" } ?: "starší záznam"}",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(sourceLabel(record.source), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatRootTime(record.takenAt), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRoot(
    writingTag: Boolean,
    canExactAlarm: Boolean,
    onWriteTag: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)) {
        AppHeader("Nastavenia", "NFC a upozornenia")
        Spacer(Modifier.height(20.dp))
        SettingCard("PillTap NFC tag", if (writingTag) "Prilož tag k zadnej strane telefónu." else "Tag potvrdí najbližšiu nepotvrdenú dávku. Ak je viac dávok rovnako blízko, vyberieš si.") {
            Button(onClick = onWriteTag, enabled = !writingTag, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(if (writingTag) "Čakám na tag…" else "Zapísať NFC tag")
            }
        }
        if (!canExactAlarm) {
            Spacer(Modifier.height(12.dp))
            SettingCard("Presný čas je vypnutý", "Android zatiaľ nepovolil PillTapu presné alarmy. Pripomienky môžu meškať.") {
                TextButton(onClick = onRequestExactAlarm) { Text("Povoliť presné alarmy") }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, description: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(5.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

private fun formatMinute(minute: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)

private fun formatRootTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun formatRootDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. M. yyyy"))

private fun sourceLabel(source: String): String = when (source) {
    "nfc" -> "NFC tag"
    "watch" -> "Hodinky"
    "notification" -> "Upozornenie"
    else -> "Telefón"
}
