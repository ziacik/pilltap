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
import java.util.Locale

object WearSync {
    const val STATUS_PATH = "/pilltap/status"
    const val INTAKE_PATH = "/pilltap/intake"

    fun publishToday(context: Context) {
        val doses = IntakeStore(context).todayDoses()
        val request = PutDataMapRequest.create(STATUS_PATH).apply {
            dataMap.putString("date", LocalDate.now().toString())
            dataMap.putLongArray("slotIds", doses.map { it.slot.id }.toLongArray())
            dataMap.putStringArrayList("names", ArrayList(doses.map { it.slot.medicineName }))
            dataMap.putStringArrayList("times", ArrayList(doses.map {
                String.format(Locale.ROOT, "%02d:%02d", it.slot.minuteOfDay / 60, it.slot.minuteOfDay % 60)
            }))
            dataMap.putLongArray("takenAt", doses.map { it.record?.takenAt ?: 0L }.toLongArray())
            dataMap.putLong("version", System.nanoTime())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }
}

class WearDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED || event.dataItem.uri.path != WearSync.INTAKE_PATH) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            if (!map.containsKey("slotId") || map.getString("date") != LocalDate.now().toString()) continue
            val slotId = map.getLong("slotId")
            if (IntakeStore(this).recordDose(slotId, "watch")) {
                ReminderScheduler.cancelNotification(this, slotId)
            }
            ReminderScheduler.schedule(this)
            WearSync.publishToday(this)
        }
    }
}
