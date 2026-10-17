package com.ziacik.pilltap

import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

internal data class WearDose(val slotId: Long, val medicineName: String, val time: String, val takenAt: Long)

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
    private val dataClient by lazy { Wearable.getDataClient(this) }
    private var doses by mutableStateOf<List<WearDose>>(emptyList())
    private var nfcStatus by mutableStateOf(NfcStatus.UNAVAILABLE)
    private var nfcError by mutableStateOf<String?>(null)
    private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PillTapWearRoot(doses, nfcStatus, nfcError, ::markTaken) }
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
        loadStatus()
        startNfcReader()
    }

    override fun onPause() {
        stopNfcReader()
        dataClient.removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == STATUS_PATH) {
                applyStatus(DataMapItem.fromDataItem(event.dataItem))
            }
        }
    }

    private fun loadStatus() {
        dataClient.dataItems.addOnSuccessListener { buffer ->
            try {
                for (item in buffer) if (item.uri.path == STATUS_PATH) applyStatus(DataMapItem.fromDataItem(item))
            } finally { buffer.release() }
        }
    }

    private fun applyStatus(item: DataMapItem) {
        val map = item.dataMap
        if (map.getString("date") != LocalDate.now().toString()) {
            runOnUiThread { doses = emptyList() }
            return
        }
        val ids = map.getLongArray("slotIds") ?: longArrayOf()
        val names = map.getStringArrayList("names")
        val times = map.getStringArrayList("times")
        val taken = map.getLongArray("takenAt") ?: longArrayOf()
        val updated = ids.mapIndexed { index, id ->
            WearDose(
                id,
                names?.getOrNull(index).orEmpty(),
                times?.getOrNull(index).orEmpty(),
                taken.getOrElse(index) { 0L },
            )
        }
        runOnUiThread { doses = updated }
    }

    private fun startNfcReader() {
        val adapter = nfcAdapter
        if (adapter == null) {
            nfcStatus = NfcStatus.UNAVAILABLE
            return
        }
        if (!adapter.isEnabled) {
            nfcStatus = NfcStatus.DISABLED
            return
        }
        runCatching {
            adapter.enableReaderMode(
                this,
                ::onTagDiscovered,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null,
            )
        }.onSuccess {
            nfcStatus = NfcStatus.READY
            nfcError = null
        }.onFailure { error ->
            nfcStatus = NfcStatus.ERROR
            nfcError = "${error.javaClass.simpleName}: ${error.message ?: "(bez správy)"}"
            Log.e("PillTapNfc", "enableReaderMode failed", error)
        }
    }

    private fun stopNfcReader() {
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }

    private fun onTagDiscovered(tag: Tag) {
        val matches = runCatching {
            val ndef = Ndef.get(tag) ?: return@runCatching false
            ndef.connect()
            try {
                val message = ndef.ndefMessage ?: return@runCatching false
                message.records.any { record ->
                    record.tnf == NdefRecord.TNF_MIME_MEDIA &&
                        record.type.toString(Charsets.US_ASCII) == PILLTAP_MIME &&
                        record.payload.toString(Charsets.UTF_8) == "take"
                }
            } finally {
                ndef.close()
            }
        }.getOrDefault(false)
        if (!matches) return
        runOnUiThread {
            val pending = doses.filter { it.takenAt == 0L }
            val now = LocalTime.now().toSecondOfDay() / 60
            val closest = pending.sortedBy { dose ->
                val minute = dose.time.take(2).toIntOrNull()?.times(60)?.plus(dose.time.takeLast(2).toIntOrNull() ?: 0) ?: 0
                abs(minute - now)
            }
            if (closest.size == 1) markTaken(closest.first().slotId)
        }
    }

    private fun markTaken(slotId: Long) {
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        doses = doses.map { if (it.slotId == slotId && it.takenAt == 0L) it.copy(takenAt = now) else it }
        val request = PutDataMapRequest.create(INTAKE_PATH).apply {
            dataMap.putLong("slotId", slotId)
            dataMap.putString("date", today)
            dataMap.putLong("version", System.nanoTime())
        }.asPutDataRequest().setUrgent()
        dataClient.putDataItem(request)
    }

    companion object {
        private const val STATUS_PATH = "/pilltap/status"
        private const val INTAKE_PATH = "/pilltap/intake"
        private const val PILLTAP_MIME = "application/vnd.com.ziacik.pilltap"
    }
}

internal enum class NfcStatus { UNAVAILABLE, DISABLED, READY, ERROR }
