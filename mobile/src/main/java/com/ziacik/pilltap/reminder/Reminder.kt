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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ziacik.pilltap.MainActivity
import com.ziacik.pilltap.data.IntakeStore
import com.ziacik.pilltap.data.Prefs
import com.ziacik.pilltap.wear.WearSync
import java.time.ZonedDateTime

object ReminderScheduler {
	const val CHANNEL_ID = "pilltap_reminders"
	const val NOTIFICATION_ID = 1001
	const val ACTION_REMIND = "com.ziacik.pilltap.REMIND"
	const val ACTION_MARK_TAKEN = "com.ziacik.pilltap.MARK_TAKEN"

	fun ensureChannel(context: Context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		context.getSystemService(NotificationManager::class.java).createNotificationChannel(
			NotificationChannel(CHANNEL_ID, "Pripomienky lieku", NotificationManager.IMPORTANCE_HIGH)
		)
	}
	fun schedule(context: Context) {
		val prefs = Prefs(context)
		val now = ZonedDateTime.now()
		var target = now.withHour(prefs.reminderHour).withMinute(prefs.reminderMinute).withSecond(0).withNano(0)
		target = when {
			IntakeStore(context).hasTakenToday() -> target.plusDays(1)
			now.isBefore(target) -> target
			else -> now.plusSeconds(10)
		}
		setAlarm(context, target)
	}
	fun cancelNotification(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
	fun notifyIfNeeded(context: Context) {
		if (IntakeStore(context).hasTakenToday()) { schedule(context); return }
		ensureChannel(context)
		val open = PendingIntent.getActivity(context,10,Intent(context, MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		val taken = PendingIntent.getBroadcast(context,11,Intent(context, ReminderReceiver::class.java).setAction(ACTION_MARK_TAKEN),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		val n = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.ic_dialog_info)
			.setContentTitle("PillTap")
			.setContentText("Dnes ešte nemáš zaznamenané užitie lieku.")
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setCategory(NotificationCompat.CATEGORY_REMINDER)
			.setAutoCancel(true)
			.setContentIntent(open)
			.addAction(0, "Užil som", taken)
			.build()
		val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
			ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
		if (allowed) NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
		val prefs = Prefs(context)
		setAlarm(context, ZonedDateTime.now().plusDays(1).withHour(prefs.reminderHour).withMinute(prefs.reminderMinute).withSecond(0).withNano(0))
	}
	private fun setAlarm(context: Context, target: ZonedDateTime) {
		val am = context.getSystemService(AlarmManager::class.java)
		val pi = PendingIntent.getBroadcast(context,1,Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		val at = target.toInstant().toEpochMilli()
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
		else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
	}
}
class ReminderReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		if (intent.action == ReminderScheduler.ACTION_MARK_TAKEN) {
			IntakeStore(context).recordIfMissing(source = "notification")
			ReminderScheduler.cancelNotification(context)
			ReminderScheduler.schedule(context)
			WearSync.publishToday(context)
		} else ReminderScheduler.notifyIfNeeded(context)
	}
}
class BootReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		ReminderScheduler.ensureChannel(context)
		ReminderScheduler.schedule(context)
		WearSync.publishToday(context)
	}
}
