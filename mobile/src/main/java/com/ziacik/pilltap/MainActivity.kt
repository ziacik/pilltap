package com.ziacik.pilltap

import android.Manifest
import android.app.AlarmManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.ziacik.pilltap.data.IntakeStore
import com.ziacik.pilltap.nfc.PillTapNfc
import com.ziacik.pilltap.reminder.ReminderScheduler
import com.ziacik.pilltap.wear.WearSync
import java.time.LocalTime
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private lateinit var store: IntakeStore
    private var refresh by mutableIntStateOf(0)
    private var writingTag by mutableStateOf(false)
    private var showNfcPicker by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        ReminderScheduler.schedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = IntakeStore(this)
        ReminderScheduler.ensureChannel(this)
        ReminderScheduler.schedule(this)
        requestNotificationPermission()
        handleIntent(intent)
        setContent {
            refresh
            PillTapRoot(
                medicines = store.medicines(),
                slotsByMedicine = store.medicines().associate { it.id to store.slots(it.id) },
                today = store.todayDoses(),
                history = store.history(),
                writingTag = writingTag,
                canExactAlarm = canScheduleExactAlarm(),
                showNfcPicker = showNfcPicker,
                onMarkTaken = { markTaken(it, "phone") },
                onDeleteIntake = { deleteIntake(it) },
                onSaveMedicine = { id, name, times ->
                    changePlan { store.saveMedicine(id, name, times) }
                },
                onSetActive = { id, active -> changePlan { store.setActive(id, active) } },
                onDeleteMedicine = { id -> changePlan { store.deleteMedicine(id) } },
                onWriteTag = { beginTagWrite() },
                onRequestExactAlarm = { requestExactAlarmAccess() },
                onNfcChoice = { id -> showNfcPicker = false; markTaken(id, "nfc") },
                onNfcDismiss = { showNfcPicker = false },
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
        ReminderScheduler.schedule(this)
        WearSync.publishToday(this)
    }

    override fun onPause() {
        if (writingTag) stopTagWrite()
        super.onPause()
    }

    private fun changePlan(action: () -> Unit) {
        val oldSlots = store.activeSlots()
        runCatching {
            action()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Zmenu sa nepodarilo uložiť.", Toast.LENGTH_LONG).show()
        }
        ReminderScheduler.cancelSlots(this, oldSlots)
        ReminderScheduler.schedule(this)
        WearSync.publishToday(this)
        refresh++
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED || intent.type != PillTapNfc.MIME_TYPE) return
        val pending = store.todayDoses().filter { it.record == null }
        if (pending.isEmpty()) {
            Toast.makeText(this, "Žiadna nepotvrdená dávka.", Toast.LENGTH_SHORT).show()
            return
        }
        val now = LocalTime.now().toSecondOfDay() / 60
        val nearest = pending.sortedBy { abs(it.slot.minuteOfDay - now) }
        if (nearest.size > 1 && abs(nearest[0].slot.minuteOfDay - now) ==
            abs(nearest[1].slot.minuteOfDay - now)) {
            showNfcPicker = true
        } else {
            markTaken(nearest.first().slot.id, "nfc")
        }
    }

    private fun markTaken(slotId: Long, source: String) {
        val slot = store.activeSlot(slotId) ?: return
        val created = store.recordDose(slotId, source)
        if (created) ReminderScheduler.cancelNotification(this, slotId)
        ReminderScheduler.schedule(this)
        WearSync.publishToday(this)
        refresh++
        val message = if (created) "Zaznamenané: ${slot.medicineName}." else "Táto dávka je už potvrdená."
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun deleteIntake(id: Long) {
        if (store.deleteIntake(id)) {
            ReminderScheduler.schedule(this)
            WearSync.publishToday(this)
            refresh++
        }
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
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            null,
        )
    }

    private fun stopTagWrite() {
        NfcAdapter.getDefaultAdapter(this)?.disableReaderMode(this)
        writingTag = false
        refresh++
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun canScheduleExactAlarm(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:$packageName".toUri()
            })
        }
    }
}
