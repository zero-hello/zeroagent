package me.rerere.zeroagent.data.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.zeroagent.data.db.dao.assembleMessagePayload

internal enum class LegacyMessageColumn(val table: String, val column: String) {
    CONVERSATION_MESSAGES("conversationentity", "messages"),
    CONVERSATION_NODES("conversationentity", "nodes"),
    NODE_MESSAGES("message_node", "messages"),
}

/** Fetch bytes in a bounded window during an existing migration transaction. */
internal fun SupportSQLiteDatabase.readLegacyMessage(column: LegacyMessageColumn, id: String): String {
    val bytes = "CAST(${column.column} AS BLOB)"
    val table = column.table
    val length = query("SELECT length($bytes) FROM $table WHERE id = ?", arrayOf(id)).use { cursor ->
        check(cursor.moveToFirst() && !cursor.isNull(0)) { "Missing legacy conversation payload" }
        cursor.getInt(0)
    }
    return assembleMessagePayload(length) { offset, count ->
        query("SELECT substr($bytes, ?, ?) FROM $table WHERE id = ?", arrayOf<Any>(offset, count, id)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getBlob(0) else null
        }
    }
}
