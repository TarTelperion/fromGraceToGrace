package org.recovery.steady.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.ZoneId

class RecoveryDatabase(context: Context) :
    SQLiteOpenHelper(context, "steady.db", null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE recovery_state (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                started_at INTEGER NOT NULL,
                original_zone TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE lapse_record (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                prior_start INTEGER NOT NULL,
                occurred_at INTEGER NOT NULL,
                note TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE saved_resource (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                source_url TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun currentState(): RecoveryState {
        readableDatabase.query(
            "recovery_state",
            arrayOf("started_at", "original_zone"),
            "id = 1",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return RecoveryState(cursor.getLong(0), cursor.getString(1))
            }
        }

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault().id
        writableDatabase.insertOrThrow(
            "recovery_state",
            null,
            ContentValues().apply {
                put("id", 1)
                put("started_at", now)
                put("original_zone", zone)
            },
        )
        return RecoveryState(now, zone)
    }

    fun recordLapse(note: String, occurredAtMillis: Long = System.currentTimeMillis()) {
        val state = currentState()
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertOrThrow(
                "lapse_record",
                null,
                ContentValues().apply {
                    put("prior_start", state.startedAtMillis)
                    put("occurred_at", occurredAtMillis)
                    put("note", note.trim())
                },
            )
            writableDatabase.update(
                "recovery_state",
                ContentValues().apply {
                    put("started_at", occurredAtMillis)
                    put("original_zone", ZoneId.systemDefault().id)
                },
                "id = 1",
                null,
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun updateCurrentStart(startedAtMillis: Long) {
        writableDatabase.update(
            "recovery_state",
            ContentValues().apply {
                put("started_at", startedAtMillis.coerceAtMost(System.currentTimeMillis()))
                put("original_zone", ZoneId.systemDefault().id)
            },
            "id = 1",
            null,
        )
    }

    fun deleteLapse(id: Long) {
        val record = readableDatabase.query(
            "lapse_record",
            arrayOf("prior_start", "occurred_at"),
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getLong(1) else null
        } ?: return

        writableDatabase.beginTransaction()
        try {
            val current = currentState()
            if (current.startedAtMillis == record.second) {
                writableDatabase.update(
                    "recovery_state",
                    ContentValues().apply {
                        put("started_at", record.first)
                        put("original_zone", ZoneId.systemDefault().id)
                    },
                    "id = 1",
                    null,
                )
            }
            writableDatabase.delete("lapse_record", "id = ?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun lapses(): List<LapseRecord> {
        return readableDatabase.query(
            "lapse_record",
            arrayOf("id", "prior_start", "occurred_at", "note"),
            null,
            null,
            null,
            null,
            "occurred_at DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LapseRecord(
                            id = cursor.getLong(0),
                            priorStartMillis = cursor.getLong(1),
                            occurredAtMillis = cursor.getLong(2),
                            note = cursor.getString(3),
                        ),
                    )
                }
            }
        }
    }

    fun saveSharedResource(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val url = URL_REGEX.find(trimmed)?.value
        writableDatabase.insertOrThrow(
            "saved_resource",
            null,
            ContentValues().apply {
                put("title", "Saved passage")
                put("body", trimmed)
                put("source_url", url)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun resources(): List<SavedResource> {
        return readableDatabase.query(
            "saved_resource",
            arrayOf("id", "title", "body", "source_url", "created_at"),
            null,
            null,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SavedResource(
                            id = cursor.getLong(0),
                            title = cursor.getString(1),
                            body = cursor.getString(2),
                            sourceUrl = cursor.getString(3),
                            createdAtMillis = cursor.getLong(4),
                        ),
                    )
                }
            }
        }
    }

    companion object {
        private const val DATABASE_VERSION = 1
        private val URL_REGEX = Regex("https?://\\S+")
    }
}
