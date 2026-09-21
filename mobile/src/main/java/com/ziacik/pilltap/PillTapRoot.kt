package com.ziacik.pilltap

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziacik.pilltap.data.IntakeRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class RootScreen { TODAY, HISTORY, SETTINGS }

private val Coral = Color(0xFFF26B5B)
private val Apricot = Color(0xFFFFA56E)
private val Cream = Color(0xFFFFF8F4)
private val Ink = Color(0xFF2B2026)
private val MutedInk = Color(0xFF75666E)
private val DarkBackground = Color(0xFF171215)
private val DarkSurface = Color(0xFF241C20)
private val DarkCoral = Color(0xFFFF8B7F)

private val LightColors = lightColorScheme(
	primary = Coral,
	onPrimary = Color.White,
	primaryContainer = Color(0xFFFFDED8),
	onPrimaryContainer = Ink,
	secondary = Apricot,
	onSecondary = Ink,
	background = Cream,
	onBackground = Ink,
	surface = Color.White,
	onSurface = Ink,
	surfaceVariant = Color(0xFFF7ECE8),
	onSurfaceVariant = MutedInk,
)

private val DarkColors = darkColorScheme(
	primary = DarkCoral,
	onPrimary = Color(0xFF4B0C09),
	primaryContainer = Color(0xFF6B2722),
	onPrimaryContainer = Color(0xFFFFDAD5),
	secondary = Color(0xFFFFB68A),
	onSecondary = Color(0xFF4C2610),
	background = DarkBackground,
	onBackground = Color(0xFFF4E8EC),
	surface = DarkSurface,
	onSurface = Color(0xFFF4E8EC),
	surfaceVariant = Color(0xFF382D32),
	onSurfaceVariant = Color(0xFFD8C4CC),
)

@Composable
internal fun PillTapRoot(
	today: IntakeRecord?,
	history: List<IntakeRecord>,
	reminderHour: Int,
	reminderMinute: Int,
	writingTag: Boolean,
	canExactAlarm: Boolean,
	onMarkTaken: () -> Unit,
	onDeleteToday: () -> Unit,
	onWriteTag: () -> Unit,
	onPickTime: () -> Unit,
	onRequestExactAlarm: () -> Unit,
) {
	var screen by remember { mutableStateOf(RootScreen.TODAY) }
	MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
		Scaffold(
			containerColor = MaterialTheme.colorScheme.background,
			bottomBar = {
				NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
					val navColors = NavigationBarItemDefaults.colors(
						selectedIconColor = MaterialTheme.colorScheme.primary,
						selectedTextColor = MaterialTheme.colorScheme.primary,
						indicatorColor = MaterialTheme.colorScheme.primaryContainer,
					)
					NavigationBarItem(screen == RootScreen.TODAY, { screen = RootScreen.TODAY }, { Icon(Icons.Default.Home, null) }, label = { Text("Dnes") }, colors = navColors)
					NavigationBarItem(screen == RootScreen.HISTORY, { screen = RootScreen.HISTORY }, { Icon(Icons.Default.History, null) }, label = { Text("História") }, colors = navColors)
					NavigationBarItem(screen == RootScreen.SETTINGS, { screen = RootScreen.SETTINGS }, { Icon(Icons.Default.Settings, null) }, label = { Text("Nastavenia") }, colors = navColors)
				}
			},
		) { padding ->
			when (screen) {
				RootScreen.TODAY -> TodayRoot(today, onMarkTaken, onDeleteToday, Modifier.padding(padding))
				RootScreen.HISTORY -> HistoryRoot(history, Modifier.padding(padding))
				RootScreen.SETTINGS -> SettingsRoot(reminderHour, reminderMinute, writingTag, canExactAlarm, onWriteTag, onPickTime, onRequestExactAlarm, Modifier.padding(padding))
			}
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
private fun TodayRoot(today: IntakeRecord?, onMarkTaken: () -> Unit, onDeleteToday: () -> Unit, modifier: Modifier) {
	var confirmDelete by remember { mutableStateOf(false) }
	Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
		AppHeader("PillTap", todaySubtitle())
		Spacer(Modifier.height(24.dp))
		if (today != null) {
			Box(
				Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
					.background(Brush.linearGradient(listOf(Coral, Apricot))).padding(24.dp),
			) {
				Column {
					Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
						Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
							Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(34.dp))
						}
					}
					Spacer(Modifier.height(28.dp))
					Text("Dávka vybavená", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
					Text("Dnes o ${formatRootTime(today.takenAt)}", color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp)
					Spacer(Modifier.height(14.dp))
					Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f)) {
						Text(rootSourceLabel(today.source), Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
					}
				}
			}
			Spacer(Modifier.height(12.dp))
			TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
				Text("Zmazať dnešný záznam", color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		} else {
			Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
				Column(Modifier.padding(24.dp)) {
					Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
						Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
							Image(painterResource(R.drawable.ic_pilltap_mark), null, Modifier.size(30.dp))
						}
					}
					Spacer(Modifier.height(28.dp))
					Text("Dnes ešte čaká", fontSize = 25.sp, fontWeight = FontWeight.Bold)
					Text("Keď si liek vezmeš, stačí jeden tap.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
				}
			}
			Spacer(Modifier.height(18.dp))
			Button(
				onClick = onMarkTaken,
				modifier = Modifier.fillMaxWidth().height(58.dp),
				shape = RoundedCornerShape(18.dp),
				colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
			) { Text("Užil som liek", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
			Spacer(Modifier.height(12.dp))
			Text("Alebo prilož PillTap NFC tag.", modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
		}
		Spacer(Modifier.weight(1f))
		Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
			Text("Jedno potvrdenie za deň. NFC, telefón aj hodinky zapisujú ten istý stav.", Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
	}
	if (confirmDelete) {
		AlertDialog(
			onDismissRequest = { confirmDelete = false },
			title = { Text("Zmazať dnešný záznam?") },
			text = { Text("PillTap bude dnešnú dávku znova považovať za neužitú.") },
			confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteToday() }) { Text("Zmazať") } },
			dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Zrušiť") } },
		)
	}
}

@Composable
private fun HistoryRoot(history: List<IntakeRecord>, modifier: Modifier) {
	Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
		AppHeader("História", if (history.isEmpty()) "Zatiaľ bez záznamov" else "${history.size} ${recordCountLabel(history.size)}")
		Spacer(Modifier.height(20.dp))
		if (history.isEmpty()) {
			Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
				Text("Prvý záznam sa tu objaví po potvrdení dávky.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		} else {
			LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				items(history, key = { it.id }) { item ->
					Card(
						Modifier.fillMaxWidth(),
						shape = RoundedCornerShape(20.dp),
						colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
						elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
					) {
						Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
							Row(verticalAlignment = Alignment.CenterVertically) {
								Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
									Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
								}
								Spacer(Modifier.size(12.dp))
								Column {
									Text(formatRootDate(item.takenAt), fontWeight = FontWeight.SemiBold)
									Text(rootSourceLabel(item.source), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
								}
							}
							Text(formatRootTime(item.takenAt), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SettingsRoot(
	hour: Int,
	minute: Int,
	writingTag: Boolean,
	canExactAlarm: Boolean,
	onWriteTag: () -> Unit,
	onPickTime: () -> Unit,
	onRequestExactAlarm: () -> Unit,
	modifier: Modifier,
) {
	Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)) {
		AppHeader("Nastavenia", "Pripomienka a NFC")
		Spacer(Modifier.height(20.dp))
		SettingCard("Denná pripomienka", "PillTap sa ozve, ak ešte nemáš dnešnú dávku potvrdenú.") {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
				Text(String.format(Locale.ROOT, "%02d:%02d", hour, minute), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
				TextButton(onClick = onPickTime) { Text("Zmeniť") }
			}
		}
		Spacer(Modifier.height(12.dp))
		SettingCard("PillTap NFC tag", if (writingTag) "Prilož tag k zadnej strane telefónu." else "Zapíš tag raz a potom ním každý deň potvrdíš dávku.") {
			Button(onClick = onWriteTag, enabled = !writingTag, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
				Text(if (writingTag) "Čakám na tag…" else "Zapísať NFC tag")
			}
		}
		if (!canExactAlarm) {
			Spacer(Modifier.height(12.dp))
			SettingCard("Presný čas je vypnutý", "Android zatiaľ nepovolil PillTapu presné alarmy.") {
				TextButton(onClick = onRequestExactAlarm) { Text("Povoliť presné alarmy") }
			}
		}
		Spacer(Modifier.height(18.dp))
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

private fun todaySubtitle(): String =
	LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale("sk", "SK"))).replaceFirstChar { it.uppercase() }

private fun recordCountLabel(count: Int): String = when {
	count == 1 -> "záznam"
	count in 2..4 -> "záznamy"
	else -> "záznamov"
}

private fun formatRootTime(epochMillis: Long): String =
	Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun formatRootDate(epochMillis: Long): String =
	Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. M. yyyy"))

private fun rootSourceLabel(source: String): String = when (source) {
	"nfc" -> "NFC tag"
	"watch" -> "Hodinky"
	"notification" -> "Upozornenie"
	else -> "Telefón"
}
