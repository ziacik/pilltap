package com.ziacik.pilltap.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ziacik.pilltap.MainActivity
import com.ziacik.pilltap.data.DoseSlot
import com.ziacik.pilltap.data.IntakeStore
import com.ziacik.pilltap.wear.WearSync
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

object ReminderScheduler {
    private const val CHANNEL_ID = "pilltap_reminders"
    const val ACTION_REMIND = "com.ziacik.pilltap.REMIND"
    const val ACTION_MARK_TAKEN = "com.ziacik.pilltap.MARK_TAKEN"
    const val EXTRA_SLOT_ID = "slotId"
    const val EXTRA_DATE = "doseDate"

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pripomienky liekov", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    fun schedule(context: Context) {
        val store = IntakeStore(context)
        val now = ZonedDateTime.now()
        for (slot in store.activeSlots()) {
            val today = now.toLocalDate()
            val nextDay = store.hasTaken(slot.id, today) || store.wasReminded(slot.id, today)
            val intendedDate = if (nextDay) today.plusDays(1) else today
            val planned = dayAt(intendedDate, slot.minuteOfDay)
            val target = if (!nextDay && !planned.isAfter(now)) now.plusSeconds(8) else planned
            val alarm = context.getSystemService(AlarmManager::class.java)
            val intent = alarmIntent(context, slot.id, intendedDate.toString())
            val at = target.toInstant().toEpochMilli()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        }
    }

    fun cancelSlots(context: Context, slots: List<DoseSlot>) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        for (slot in slots) {
            alarm.cancel(alarmIntent(context, slot.id))
            cancelNotification(context, slot.id)
        }
    }

    fun cancelNotification(context: Context, slotId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(slotId))
    }

    fun onReminder(context: Context, slotId: Long, intendedDate: String?) {
        if (intendedDate != LocalDate.now().toString()) {
            schedule(context)
            return
        }
        val store = IntakeStore(context)
        val slot = store.activeSlot(slotId) ?: return
        val today = LocalDate.now()
        if (!store.hasTaken(slotId, today) && !store.wasReminded(slotId, today)) {
            store.markReminded(slotId, today)
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val taken = PendingIntent.getBroadcast(
                context, slotId.toInt(),
                Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_MARK_TAKEN
                    data = Uri.parse("pilltap://taken/$slotId")
                    putExtra(EXTRA_SLOT_ID, slotId)
                    putExtra(EXTRA_DATE, today.toString())
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val clock = String.format(Locale.ROOT, "%02d:%02d", slot.minuteOfDay / 60, slot.minuteOfDay % 60)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(slot.medicineName)
                .setContentText("Čas užiť dávku ($clock).")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(open)
                .addAction(0, "Užil som", taken)
                .build()
            val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (permitted) NotificationManagerCompat.from(context).notify(notificationId(slotId), notification)
        }
        schedule(context)
        WearSync.publishToday(context)
    }

    private fun dayAt(date: LocalDate, minutes: Int): ZonedDateTime =
        date.atTime(minutes / 60, minutes % 60).atZone(ZoneId.systemDefault())

    private fun alarmIntent(context: Context, slotId: Long, intendedDate: String? = null): PendingIntent =
        PendingIntent.getBroadcast(
            context, slotId.toInt(),
            Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMIND
                data = Uri.parse("pilltap://remind/$slotId")
                putExtra(EXTRA_SLOT_ID, slotId)
                if (intendedDate != null) putExtra(EXTRA_DATE, intendedDate)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun notificationId(slotId: Long): Int = slotId.toInt()
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slotId = intent.getLongExtra(ReminderScheduler.EXTRA_SLOT_ID, -1L)
        if (slotId < 0) return
        when (intent.action) {
            ReminderScheduler.ACTION_REMIND -> ReminderScheduler.onReminder(context, slotId, intent.getStringExtra(ReminderScheduler.EXTRA_DATE))
            ReminderScheduler.ACTION_MARK_TAKEN -> {
                if (intent.getStringExtra(ReminderScheduler.EXTRA_DATE) != LocalDate.now().toString()) {
                    ReminderScheduler.cancelNotification(context, slotId)
                    ReminderScheduler.schedule(context)
                    return
                }
                if (IntakeStore(context).recordDose(slotId, "notification")) {
                    ReminderScheduler.cancelNotification(context, slotId)
                }
                ReminderScheduler.schedule(context)
                WearSync.publishToday(context)
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) return
        ReminderScheduler.ensureChannel(context)
        ReminderScheduler.schedule(context)
        WearSync.publishToday(context)
    }
}
