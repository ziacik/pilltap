package com.ziacik.pilltap.data

import android.content.Context

class Prefs(context: Context) {
	private val prefs = context.getSharedPreferences("pilltap", Context.MODE_PRIVATE)
	var reminderHour: Int
		get() = prefs.getInt("reminder_hour", 20)
		set(value) = prefs.edit().putInt("reminder_hour", value).apply()
	var reminderMinute: Int
		get() = prefs.getInt("reminder_minute", 0)
		set(value) = prefs.edit().putInt("reminder_minute", value).apply()
}
