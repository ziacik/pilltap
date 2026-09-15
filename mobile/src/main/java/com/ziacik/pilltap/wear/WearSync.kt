package com.ziacik.pilltap.wear

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.ziacik.pilltap.data.IntakeStore
import com.ziacik.pilltap.reminder.ReminderScheduler
import java.time.LocalDate

object WearSync {
	const val STATUS_PATH = "/pilltap/status"
	const val INTAKE_PATH = "/pilltap/intake"

	fun publishToday(context: Context) {
		val record = IntakeStore(context).today()
		val request = PutDataMapRequest.create(STATUS_PATH).apply {
			dataMap.putString("date", LocalDate.now().toString())
			dataMap.putBoolean("taken", record != null)
			dataMap.putLong("takenAt", record?.takenAt ?: 0L)
			dataMap.putLong("version", System.currentTimeMillis())
		}.asPutDataRequest().setUrgent()
		Wearable.getDataClient(context).putDataItem(request)
	}
}

class WearDataListenerService : WearableListenerService() {
	override fun onDataChanged(dataEvents: DataEventBuffer) {
		for (event in dataEvents) {
			if (event.type != DataEvent.TYPE_CHANGED) continue
			if (event.dataItem.uri.path != WearSync.INTAKE_PATH) continue
			val map = DataMapItem.fromDataItem(event.dataItem).dataMap
			val takenAt = if (map.containsKey("takenAt")) map.getLong("takenAt") else System.currentTimeMillis()
			IntakeStore(this).recordIfMissing(takenAt, "watch")
			ReminderScheduler.cancelNotification(this)
			ReminderScheduler.schedule(this)
			WearSync.publishToday(this)
		}
	}
}
