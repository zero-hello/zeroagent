package me.rerere.zeroagent.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room owns the transaction and validates v32 after this targeted retirement. */
val Migration_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `telegram_chats`")
        db.execSQL("UPDATE `agent_runs` SET `parent_run_id` = NULL WHERE `parent_run_id` IN (SELECT `id` FROM `agent_runs` WHERE `kind` = 'telegram')")
        db.execSQL("DELETE FROM `agent_runs` WHERE `kind` = 'telegram'")
        retireRows(db, "scheduled_jobs", "actionsJson", "nextRunAtMs = NULL, ")
        retireRows(db, "workflows", "definitionJson", "")
    }

    private fun retireRows(db: SupportSQLiteDatabase, table: String, column: String, extra: String) {
        val updates = mutableListOf<Pair<String, String>>()
        db.query("SELECT `id`, `$column` FROM `$table` WHERE `$column` IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) {
                val retired = retireTelegramActions(cursor.getString(1)) ?: continue
                updates += cursor.getString(0) to retired
            }
        }
        updates.forEach { (id, payload) ->
            db.execSQL("UPDATE `$table` SET ${extra}`enabled` = 0, `$column` = ? WHERE `id` = ?", arrayOf(payload, id))
        }
    }
}
