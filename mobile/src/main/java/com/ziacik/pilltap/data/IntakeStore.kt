package com.ziacik.pilltap.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.ZoneId

data class IntakeRecord(val id: Long, val takenAt: Long, val source: String)

class IntakeStore(context: Context) : SQLiteOpenHelper(context, "pilltap.db", null, 1) {
	override fun onCreate(db: SQLiteDatabase) {
		db.execSQL("CREATE TABLE intake (id INTEGER PRIMARY KEY AUTOINCREMENT, taken_at INTEGER NOT NULL, source TEXT NOT NULL)")
		db.execSQL("CREATE INDEX intake_taken_at_idx ON intake(taken_at)")
	}
	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

	fun recordIfMissing(takenAt: Long = System.currentTimeMillis(), source: String): Boolean {
		if (recordForDay(epochToDate(takenAt)) != null) return false
		writableDatabase.insertOrThrow("intake", null, ContentValues().apply {
			put("taken_at", takenAt)
			put("source", source)
		})
		return true
	}
	fun today(): IntakeRecord? = recordForDay(LocalDate.now())
	fun hasTakenToday(): Boolean = today() != null
	fun history(limit: Int = 120): List<IntakeRecord> {
		val result = mutableListOf<IntakeRecord>()
		readableDatabase.query("intake", arrayOf("id","taken_at","source"), null,null,null,null,"taken_at DESC",limit.toString()).use { c ->
			while (c.moveToNext()) result += IntakeRecord(c.getLong(0), c.getLong(1), c.getString(2))
		}
		return result
	}
	private fun recordForDay(date: LocalDate): IntakeRecord? {
		val zone = ZoneId.systemDefault()
		val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
		val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
		readableDatabase.query("intake", arrayOf("id","taken_at","source"), "taken_at >= ? AND taken_at < ?", arrayOf(start.toString(), end.toString()), null,null,"taken_at ASC","1").use { c ->
			if (!c.moveToFirst()) return null
			return IntakeRecord(c.getLong(0), c.getLong(1), c.getString(2))
		}
	}
	private fun epochToDate(epochMillis: Long): LocalDate =
		java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}
