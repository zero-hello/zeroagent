package me.rerere.zeroagent.data.sync

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabase
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration
import me.rerere.zeroagent.data.db.SQLiteConfiguration
import java.io.File

internal object DatabaseBackup {
    const val ARCHIVE_DATABASE = "rikka_hub.db"
    const val WAL = "rikka_hub-wal"
    const val SHM = "rikka_hub-shm"

    /** VACUUM INTO includes committed WAL contents in a consistent, standalone snapshot. */
    fun createSnapshot(database: SupportSQLiteDatabase, destination: File) {
        check(!destination.exists()) { "Backup snapshot already exists" }
        database.execSQL("VACUUM main INTO ?", arrayOf(destination.absolutePath))
    }

    /** Keep portable backups self contained even on a device with no payload files. */
    fun inlineChatPayloads(context: Context, databaseFile: File, read: (String) -> String) {
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(databaseFile.absolutePath, SQLiteDatabase.OPEN_READWRITE),
        )
        SQLiteDatabase.openDatabase(configuration, null) { error("Backup snapshot is corrupt") }.use { snapshot ->
            snapshot.beginTransaction()
            try {
                var last = ""
                while (true) {
                    val batch = snapshot.query(
                        "SELECT id, payload_sha256 FROM message_node WHERE payload_sha256 IS NOT NULL AND id > ? ORDER BY id LIMIT 64",
                        arrayOf(last),
                    ).use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                        }
                    }
                    if (batch.isEmpty()) break
                    for ((id, digest) in batch) {
                        snapshot.execSQL(
                            "UPDATE message_node SET messages = ?, payload_sha256 = NULL WHERE id = ?",
                            arrayOf(read(digest), id),
                        )
                    }
                    last = batch.last().first
                }
                snapshot.setTransactionSuccessful()
            } finally {
                snapshot.endTransaction()
            }
            checkpoint(snapshot)
        }
        removeSidecars(databaseFile)
    }

    /** Preserve the saved hosts in portable archives without exporting device-bound credentials. */
    fun removeSshCredentials(context: Context, databaseFile: File) {
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(databaseFile.absolutePath, SQLiteDatabase.OPEN_READWRITE)
        )
        SQLiteDatabase.openDatabase(configuration, null) {
            error("Backup snapshot is corrupt")
        }.use { snapshot ->
            snapshot.execSQL("PRAGMA secure_delete=ON")
            snapshot.execSQL("UPDATE ssh_hosts SET password = NULL, privateKey = NULL, passphrase = NULL")
            checkpoint(snapshot)
        }
        removeSidecars(databaseFile)
    }

    /** Only opens the staged copy. The caller must place its matching WAL beside it first. */
    fun normalize(context: Context, databaseFile: File) {
        require(databaseFile.isFile && databaseFile.length() > 0) { "Backup database is missing or empty" }
        val configuration = SQLiteConfiguration.configure(
            context,
            SQLiteDatabaseConfiguration(databaseFile.absolutePath, SQLiteDatabase.OPEN_READWRITE)
        )
        SQLiteDatabase.openDatabase(configuration, null) {
            error("Backup database is corrupt")
        }.use { database ->
            checkpoint(database)
            database.query("PRAGMA integrity_check").use { cursor ->
                check(cursor.moveToFirst() && cursor.getString(0) == "ok" && !cursor.moveToNext()) {
                    "Backup database failed its integrity check"
                }
            }
        }
        removeSidecars(databaseFile)
    }

    fun checkpoint(database: SupportSQLiteDatabase) {
        database.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) {
                "Could not checkpoint the backup database"
            }
        }
    }

    fun removeSidecars(databaseFile: File) {
        // All connections must be closed and committed WAL data checkpointed before removal.
        check(File(databaseFile.path + "-wal").length() == 0L) { "Backup WAL was not fully checkpointed" }
        for (suffix in listOf("-wal", "-shm")) {
            val sidecar = File(databaseFile.path + suffix)
            check(!sidecar.exists() || sidecar.delete()) { "Could not remove staged database sidecar" }
        }
    }
}
