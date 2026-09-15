package com.ziacik.pilltap

import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {
	private val dataClient by lazy { Wearable.getDataClient(this) }
	private var taken by mutableStateOf(false)
	private var takenAt by mutableLongStateOf(0L)
	private var nfcStatus by mutableStateOf(NfcStatus.UNAVAILABLE)
	private var nfcError by mutableStateOf<String?>(null)
	private val nfcAdapter by lazy { NfcAdapter.getDefaultAdapter(this) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent { WearScreen(taken, takenAt, nfcStatus, nfcError, ::markTaken) }
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
		val isToday = map.getString("date") == LocalDate.now().toString()
		taken = isToday && map.getBoolean("taken")
		takenAt = if (taken && map.containsKey("takenAt")) map.getLong("takenAt") else 0L
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
				NfcAdapter.FLAG_READER_NFC_A or
					NfcAdapter.FLAG_READER_NFC_B or
					NfcAdapter.FLAG_READER_NFC_F or
					NfcAdapter.FLAG_READER_NFC_V,
				null,
			)
		}.onSuccess {
			nfcStatus = NfcStatus.READY
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

		if (matches) runOnUiThread { markTaken() }
	}
	private fun markTaken() {
		val now = System.currentTimeMillis()
		taken = true
		takenAt = now
		val request = PutDataMapRequest.create(INTAKE_PATH).apply {
			dataMap.putLong("takenAt", now)
			dataMap.putLong("version", now)
		}.asPutDataRequest().setUrgent()
		dataClient.putDataItem(request)
	}
	companion object {
		private const val STATUS_PATH = "/pilltap/status"
		private const val INTAKE_PATH = "/pilltap/intake"
		private const val PILLTAP_MIME = "application/vnd.com.ziacik.pilltap"
	}
}

private enum class NfcStatus { UNAVAILABLE, DISABLED, READY, ERROR }

@Composable
private fun WearScreen(taken: Boolean, takenAt: Long, nfcStatus: NfcStatus, nfcError: String?, onMarkTaken: () -> Unit) {
	MaterialTheme {
		Column(
			Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center,
		) {
			Text("PillTap", fontWeight = FontWeight.Bold)
			Text(
				when (nfcStatus) {
					NfcStatus.READY -> "NFC pripravené"
					NfcStatus.DISABLED -> "NFC je vypnuté"
					NfcStatus.UNAVAILABLE -> "NFC reader nedostupný"
					NfcStatus.ERROR -> "NFC reader zlyhal"
				},
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
			if (nfcStatus == NfcStatus.ERROR && nfcError != null) {
				Text(nfcError, color = MaterialTheme.colorScheme.error)
			}
			Spacer(Modifier.height(10.dp))
			if (taken) {
				Text("✓ Dnes užité", color = MaterialTheme.colorScheme.primary)
				if (takenAt > 0L) Text(formatTime(takenAt))
			} else {
				Text("Dnes ešte nie")
				Spacer(Modifier.height(10.dp))
				Box(
					Modifier.fillMaxWidth()
						.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
						.clickable(onClick = onMarkTaken)
						.padding(vertical = 12.dp),
					contentAlignment = Alignment.Center,
				) {
					Text("Užil som", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
				}
			}
		}
	}
}

private fun formatTime(epochMillis: Long): String =
	Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
