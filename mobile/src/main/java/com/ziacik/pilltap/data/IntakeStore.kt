package com.ziacik.pilltap.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Medicine(val id: Long, val name: String, val active: Boolean)
data class DoseSlot(val id: Long, val medicineId: Long, val medicineName: String, val minuteOfDay: Int)
data class IntakeRecord(
    val id: Long,
    val takenAt: Long,
    val source: String,
    val medicineName: String,
    val slotId: Long?,
    val scheduledMinute: Int?,
    val doseDate: String,
)
data class TodayDose(val slot: DoseSlot, val record: IntakeRecord?)

class IntakeStore(context: Context) : SQLiteOpenHelper(context, "pilltap.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE medicines (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1)")
        db.execSQL("CREATE TABLE dose_slots (id INTEGER PRIMARY KEY AUTOINCREMENT, medicine_id INTEGER NOT NULL, minute_of_day INTEGER NOT NULL, UNIQUE(medicine_id, minute_of_day))")
        db.execSQL("CREATE TABLE intake (id INTEGER PRIMARY KEY AUTOINCREMENT, taken_at INTEGER NOT NULL, source TEXT NOT NULL, medicine_name TEXT NOT NULL, slot_id INTEGER, scheduled_minute INTEGER, dose_date TEXT NOT NULL)")
        db.execSQL("CREATE INDEX intake_taken_at_idx ON intake(taken_at)")
        db.execSQL("CREATE UNIQUE INDEX intake_slot_day_idx ON intake(slot_id, dose_date) WHERE slot_id IS NOT NULL")
        db.execSQL("CREATE TABLE reminders_sent (slot_id INTEGER NOT NULL, dose_date TEXT NOT NULL, PRIMARY KEY(slot_id, dose_date))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE medicines (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, active INTEGER NOT NULL DEFAULT 1)")
            db.execSQL("CREATE TABLE dose_slots (id INTEGER PRIMARY KEY AUTOINCREMENT, medicine_id INTEGER NOT NULL, minute_of_day INTEGER NOT NULL, UNIQUE(medicine_id, minute_of_day))")
            db.execSQL("ALTER TABLE intake ADD COLUMN medicine_name TEXT NOT NULL DEFAULT 'Pôvodný záznam'")
            db.execSQL("ALTER TABLE intake ADD COLUMN slot_id INTEGER")
            db.execSQL("ALTER TABLE intake ADD COLUMN scheduled_minute INTEGER")
            db.execSQL("ALTER TABLE intake ADD COLUMN dose_date TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE intake SET dose_date = date(taken_at / 1000, 'unixepoch', 'localtime')")
            db.execSQL("CREATE UNIQUE INDEX intake_slot_day_idx ON intake(slot_id, dose_date) WHERE slot_id IS NOT NULL")
            db.execSQL("CREATE TABLE reminders_sent (slot_id INTEGER NOT NULL, dose_date TEXT NOT NULL, PRIMARY KEY(slot_id, dose_date))")
        }
    }

    fun medicines(): List<Medicine> {
        val result = mutableListOf<Medicine>()
        readableDatabase.query("medicines", arrayOf("id", "name", "active"), null, null, null, null, "name COLLATE NOCASE").use { c ->
            while (c.moveToNext()) result += Medicine(c.getLong(0), c.getString(1), c.getInt(2) == 1)
        }
        return result
    }

    fun slots(medicineId: Long): List<DoseSlot> = readSlots("s.medicine_id = ?", arrayOf(medicineId.toString()))
    fun activeSlots(): List<DoseSlot> = readSlots("m.active = 1", null)
    fun activeSlot(slotId: Long): DoseSlot? = readSlots("m.active = 1 AND s.id = ?", arrayOf(slotId.toString())).firstOrNull()

    private fun readSlots(selection: String, args: Array<String>?): List<DoseSlot> {
        val result = mutableListOf<DoseSlot>()
        readableDatabase.rawQuery(
            "SELECT s.id, s.medicine_id, m.name, s.minute_of_day FROM dose_slots s JOIN medicines m ON m.id = s.medicine_id WHERE $selection ORDER BY s.minute_of_day, m.name",
            args,
        ).use { c ->
            while (c.moveToNext()) result += DoseSlot(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3))
        }
        return result
    }

    fun saveMedicine(id: Long?, name: String, times: List<Int>): Long {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Zadaj názov lieku." }
        val minutes = times.distinct().sorted()
        require(minutes.isNotEmpty() && minutes.all { it in 0..1439 }) { "Zadaj aspoň jeden platný čas." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val medicineId = if (id == null) {
                db.insertOrThrow("medicines", null, ContentValues().apply {
                    put("name", cleanName)
                    put("active", 1)
                })
            } else {
                val changed = db.update("medicines", ContentValues().apply { put("name", cleanName) }, "id = ?", arrayOf(id.toString()))
                require(changed == 1) { "Liek neexistuje." }
                id
            }
            val existing = slots(medicineId)
            for (old in existing.filter { it.minuteOfDay !in minutes }) {
                db.delete("dose_slots", "id = ?", arrayOf(old.id.toString()))
            }
            for (minute in minutes.filter { time -> existing.none { it.minuteOfDay == time } }) {
                db.insertOrThrow("dose_slots", null, ContentValues().apply {
                    put("medicine_id", medicineId)
                    put("minute_of_day", minute)
                })
            }
            db.setTransactionSuccessful()
            return medicineId
        } finally {
            db.endTransaction()
        }
    }

    fun setActive(id: Long, active: Boolean) {
        writableDatabase.update("medicines", ContentValues().apply { put("active", if (active) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    fun todayDoses(): List<TodayDose> {
        val date = LocalDate.now().toString()
        return activeSlots().map { TodayDose(it, recordForSlot(it.id, date)) }
    }

    fun recordDose(slotId: Long, source: String, takenAt: Long = System.currentTimeMillis()): Boolean {
        val slot = activeSlot(slotId) ?: return false
        val date = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val inserted = writableDatabase.insertWithOnConflict("intake", null, ContentValues().apply {
            put("taken_at", takenAt)
            put("source", source)
            put("medicine_name", slot.medicineName)
            put("slot_id", slotId)
            put("scheduled_minute", slot.minuteOfDay)
            put("dose_date", date)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        return inserted != -1L
    }

    fun hasTaken(slotId: Long, date: LocalDate = LocalDate.now()): Boolean = recordForSlot(slotId, date.toString()) != null

    private fun recordForSlot(slotId: Long, date: String): IntakeRecord? {
        readableDatabase.query("intake", RECORD_COLUMNS, "slot_id = ? AND dose_date = ?", arrayOf(slotId.toString(), date), null, null, null, "1").use { c ->
            return if (c.moveToFirst()) recordFrom(c) else null
        }
    }

    fun deleteIntake(id: Long): Boolean = writableDatabase.delete("intake", "id = ?", arrayOf(id.toString())) > 0

    fun history(limit: Int = 200): List<IntakeRecord> {
        val result = mutableListOf<IntakeRecord>()
        readableDatabase.query("intake", RECORD_COLUMNS, null, null, null, null, "taken_at DESC", limit.toString()).use { c ->
            while (c.moveToNext()) result += recordFrom(c)
        }
        return result
    }

    fun wasReminded(slotId: Long, date: LocalDate): Boolean {
        readableDatabase.query("reminders_sent", arrayOf("slot_id"), "slot_id = ? AND dose_date = ?", arrayOf(slotId.toString(), date.toString()), null, null, null, "1").use { c ->
            return c.moveToFirst()
        }
    }

    fun markReminded(slotId: Long, date: LocalDate) {
        writableDatabase.insertWithOnConflict("reminders_sent", null, ContentValues().apply {
            put("slot_id", slotId)
            put("dose_date", date.toString())
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun recordFrom(c: android.database.Cursor): IntakeRecord = IntakeRecord(
        id = c.getLong(0),
        takenAt = c.getLong(1),
        source = c.getString(2),
        medicineName = c.getString(3),
        slotId = if (c.isNull(4)) null else c.getLong(4),
        scheduledMinute = if (c.isNull(5)) null else c.getInt(5),
        doseDate = c.getString(6),
    )

    companion object {
        private val RECORD_COLUMNS = arrayOf("id", "taken_at", "source", "medicine_name", "slot_id", "scheduled_minute", "dose_date")
    }
}
