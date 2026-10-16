package com.pocketledger.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val person: String,
    val title: String,
    val cents: Long,
    val paid: Long = 0,
    val receivable: Boolean,
    val category: String,
    val date: String = LocalDate.now().toString(),
    val note: String = ""
) { val remaining: Long get() = cents - paid }

fun parseMoney(input: String): Long? = try {
    val value = input.trim().toBigDecimal()
    if (value <= BigDecimal.ZERO || value.scale() > 2) null
    else value.movePointRight(2).longValueExact().takeIf { it <= 999_999_999_99L }
} catch (_: Exception) { null }

fun splitMoney(cents: Long, people: Int): Long = BigDecimal(cents)
    .divide(BigDecimal(people), 0, RoundingMode.HALF_UP).longValueExact()

class LedgerDb(context: Context) : SQLiteOpenHelper(context, "ledger.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE entries (
            id TEXT PRIMARY KEY, person TEXT NOT NULL, title TEXT NOT NULL,
            cents INTEGER NOT NULL CHECK(cents > 0), paid INTEGER NOT NULL CHECK(paid >= 0 AND paid <= cents),
            receivable INTEGER NOT NULL, category TEXT NOT NULL, date TEXT NOT NULL, note TEXT NOT NULL
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun all(): List<Entry> = readableDatabase.rawQuery("SELECT * FROM entries ORDER BY date DESC, rowid DESC", null).use { c ->
        buildList {
            while (c.moveToNext()) add(Entry(c.getString(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4), c.getInt(5) == 1, c.getString(6), c.getString(7), c.getString(8)))
        }
    }
    private fun values(e: Entry) = ContentValues().apply {
        put("id", e.id); put("person", e.person); put("title", e.title); put("cents", e.cents)
        put("paid", e.paid); put("receivable", if (e.receivable) 1 else 0)
        put("category", e.category); put("date", e.date); put("note", e.note)
    }
    fun save(e: Entry) { writableDatabase.insertWithOnConflict("entries", null, values(e), SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) } }
    fun delete(e: Entry) { writableDatabase.delete("entries", "id = ?", arrayOf(e.id)) }
    fun export(currency: String): String = JSONObject().apply {
        put("version", 1); put("currency", currency)
        put("entries", JSONArray().apply { all().forEach { e -> put(JSONObject().apply {
            put("id", e.id); put("person", e.person); put("title", e.title); put("cents", e.cents)
            put("paid", e.paid); put("receivable", e.receivable); put("category", e.category); put("date", e.date); put("note", e.note)
        }) } })
    }.toString(2)
    // Validate the entire backup before touching the database; restore merges by stable ID.
    fun restore(json: String): String {
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Unsupported backup version" }
        val currency = root.getString("currency")
        require(currency in listOf("INR", "USD", "EUR", "GBP")) { "Invalid currency" }
        val array = root.getJSONArray("entries")
        require(array.length() <= 100_000) { "Backup is too large" }
        val entries = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Entry(o.getString("id"), o.getString("person"), o.getString("title"), o.getLong("cents"), o.getLong("paid"), o.getBoolean("receivable"), o.getString("category"), o.getString("date"), o.getString("note")).also { e ->
                UUID.fromString(e.id); LocalDate.parse(e.date)
                require(e.person.isNotBlank() && e.person.length <= 80 && e.title.isNotBlank() && e.title.length <= 120)
                require(e.cents in 1..999_999_999_99L && e.paid in 0..e.cents && e.note.length <= 2000 && e.category.length <= 40)
            }
        }
        val db = writableDatabase
        db.beginTransaction()
        try { entries.forEach(::save); db.setTransactionSuccessful() } finally { db.endTransaction() }
        return currency
    }
}
