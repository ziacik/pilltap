package com.ziacik.pilltap

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziacik.pilltap.data.IntakeRecord
import com.ziacik.pilltap.data.IntakeStore
import com.ziacik.pilltap.data.Prefs
import com.ziacik.pilltap.nfc.PillTapNfc
import com.ziacik.pilltap.reminder.ReminderScheduler
import com.ziacik.pilltap.wear.WearSync
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
	private lateinit var store: IntakeStore
	private lateinit var prefs: Prefs
	private var refresh by mutableIntStateOf(0)
	private var writingTag by mutableStateOf(false)

	private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		store = IntakeStore(this)
		prefs = Prefs(this)
		ReminderScheduler.ensureChannel(this)
		ReminderScheduler.schedule(this)
		requestNotificationPermission()
		handleIntent(intent)
		setContent {
			refresh
			PillTapApp(
				today = store.today(),
				history = store.history(),
				reminderHour = prefs.reminderHour,
				reminderMinute = prefs.reminderMinute,
				writingTag = writingTag,
				canExactAlarm = canScheduleExactAlarm(),
				onMarkTaken = { markTaken("phone") },
				onWriteTag = { beginTagWrite() },
				onPickTime = { pickReminderTime() },
				onRequestExactAlarm = { requestExactAlarmAccess() },
			)
		}
	}
	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handleIntent(intent)
	}
	override fun onResume() {
		super.onResume()
		refresh++
		WearSync.publishToday(this)
	}
	override fun onPause() {
		if (writingTag) stopTagWrite()
		super.onPause()
	}
	private fun handleIntent(intent: Intent?) {
		if (intent?.action == NfcAdapter.ACTION_NDEF_DISCOVERED && intent.type == PillTapNfc.MIME_TYPE) markTaken("nfc")
	}
	private fun markTaken(source: String) {
		val created = store.recordIfMissing(source = source)
		ReminderScheduler.cancelNotification(this)
		ReminderScheduler.schedule(this)
		WearSync.publishToday(this)
		refresh++
		Toast.makeText(this, if (created) "Dnešná dávka zaznamenaná." else "Dnešná dávka už bola zaznamenaná.", Toast.LENGTH_SHORT).show()
	}
	private fun beginTagWrite() {
		val adapter = NfcAdapter.getDefaultAdapter(this)
		if (adapter == null) {
			Toast.makeText(this, "Telefón nemá NFC.", Toast.LENGTH_SHORT).show()
			return
		}
		if (!adapter.isEnabled) {
			Toast.makeText(this, "Zapni NFC a skús znova.", Toast.LENGTH_SHORT).show()
			return
		}
		writingTag = true
		adapter.enableReaderMode(
			this,
			{ tag ->
				runCatching { PillTapNfc.write(tag) }
					.onSuccess { runOnUiThread {
						Toast.makeText(this, "PillTap tag je zapísaný.", Toast.LENGTH_SHORT).show()
						stopTagWrite()
					} }
					.onFailure { error -> runOnUiThread {
						Toast.makeText(this, error.message ?: "Tag sa nepodarilo zapísať.", Toast.LENGTH_LONG).show()
						stopTagWrite()
					} }
			},
			NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
			null,
		)
	}
	private fun stopTagWrite() {
		NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
		writingTag = false
		refresh++
	}
	private fun pickReminderTime() {
		TimePickerDialog(this, { _, hour, minute ->
			prefs.reminderHour = hour
			prefs.reminderMinute = minute
			ReminderScheduler.schedule(this)
			refresh++
		}, prefs.reminderHour, prefs.reminderMinute, true).show()
	}
	private fun requestNotificationPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
			checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
		) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
	}
	private fun canScheduleExactAlarm(): Boolean =
		Build.VERSION.SDK_INT < Build.VERSION_CODES.S || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
	private fun requestExactAlarmAccess() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
				data = android.net.Uri.parse("package:$packageName")
			})
		}
	}
}

private enum class Screen { TODAY, HISTORY, SETTINGS }

@Composable
private fun PillTapApp(
	today: IntakeRecord?,
	history: List<IntakeRecord>,
	reminderHour: Int,
	reminderMinute: Int,
	writingTag: Boolean,
	canExactAlarm: Boolean,
	onMarkTaken: () -> Unit,
	onWriteTag: () -> Unit,
	onPickTime: () -> Unit,
	onRequestExactAlarm: () -> Unit,
) {
	var screen by mutableStateOf(Screen.TODAY)
	MaterialTheme {
		Scaffold(bottomBar = {
			NavigationBar {
				NavigationBarItem(screen == Screen.TODAY, { screen = Screen.TODAY }, { Icon(Icons.Default.Home, null) }, label = { Text("Dnes") })
				NavigationBarItem(screen == Screen.HISTORY, { screen = Screen.HISTORY }, { Icon(Icons.Default.History, null) }, label = { Text("História") })
				NavigationBarItem(screen == Screen.SETTINGS, { screen = Screen.SETTINGS }, { Icon(Icons.Default.Settings, null) }, label = { Text("Nastavenia") })
			}
		}) { padding ->
			when (screen) {
				Screen.TODAY -> TodayScreen(today, onMarkTaken, Modifier.padding(padding))
				Screen.HISTORY -> HistoryScreen(history, Modifier.padding(padding))
				Screen.SETTINGS -> SettingsScreen(reminderHour, reminderMinute, writingTag, canExactAlarm, onWriteTag, onPickTime, onRequestExactAlarm, Modifier.padding(padding))
			}
		}
	}
}

@Composable
private fun TodayScreen(today: IntakeRecord?, onMarkTaken: () -> Unit, modifier: Modifier = Modifier) {
	Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
		Text("PillTap", fontSize = 34.sp, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(28.dp))
		if (today != null) {
			Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
			Spacer(Modifier.height(12.dp))
			Text("Dnes užité", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
			Text(formatTime(today.takenAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
		} else {
			Text("Dnes ešte nezaznamenané", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
			Spacer(Modifier.height(16.dp))
			Button(onClick = onMarkTaken) { Text("Užil som") }
		}
	}
}

@Composable
private fun HistoryScreen(history: List<IntakeRecord>, modifier: Modifier = Modifier) {
	Column(modifier.fillMaxSize().padding(20.dp)) {
		Text("História", fontSize = 28.sp, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(12.dp))
		if (history.isEmpty()) Text("Zatiaľ žiadne záznamy.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			items(history, key = { it.id }) { item ->
				Card(Modifier.fillMaxWidth()) {
					Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
						Column {
							Text(formatDate(item.takenAt), fontWeight = FontWeight.SemiBold)
							Text(sourceLabel(item.source), color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
						Text(formatTime(item.takenAt), fontWeight = FontWeight.Medium)
					}
				}
			}
		}
	}
}

@Composable
private fun SettingsScreen(
	hour: Int,
	minute: Int,
	writingTag: Boolean,
	canExactAlarm: Boolean,
	onWriteTag: () -> Unit,
	onPickTime: () -> Unit,
	onRequestExactAlarm: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Column(modifier.fillMaxSize().padding(20.dp)) {
		Text("Nastavenia", fontSize = 28.sp, fontWeight = FontWeight.Bold)
		Spacer(Modifier.height(20.dp))
		Card(Modifier.fillMaxWidth()) {
			Column(Modifier.padding(16.dp)) {
				Text("Denná pripomienka", fontWeight = FontWeight.SemiBold)
				Text(String.format("%02d:%02d", hour, minute), fontSize = 26.sp)
				TextButton(onClick = onPickTime) { Text("Zmeniť čas") }
			}
		}
		Spacer(Modifier.height(12.dp))
		Card(Modifier.fillMaxWidth()) {
			Column(Modifier.padding(16.dp)) {
				Text("NFC tag", fontWeight = FontWeight.SemiBold)
				Text(if (writingTag) "Prilož NFC tag k telefónu…" else "Zapíš do tagu PillTap príkaz na potvrdenie dávky.")
				Spacer(Modifier.height(8.dp))
				Button(onClick = onWriteTag, enabled = !writingTag) { Text(if (writingTag) "Čakám na tag…" else "Zapísať NFC tag") }
			}
		}
		if (!canExactAlarm) {
			Spacer(Modifier.height(12.dp))
			Card(Modifier.fillMaxWidth()) {
				Column(Modifier.padding(16.dp)) {
					Text("Presný čas", fontWeight = FontWeight.SemiBold)
					Text("Android zatiaľ nepovolil PillTapu presné alarmy.")
					TextButton(onClick = onRequestExactAlarm) { Text("Povoliť presné alarmy") }
				}
			}
		}
	}
}

private fun formatTime(epochMillis: Long): String =
	Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private fun formatDate(epochMillis: Long): String =
	Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d. M. yyyy"))
private fun sourceLabel(source: String): String = when (source) {
	"nfc" -> "NFC tag"
	"watch" -> "Hodinky"
	"notification" -> "Upozornenie"
	else -> "Telefón"
}
