package me.rerere.zeroagent.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.zeroagent.data.db.SQLiteConfiguration
import me.rerere.zeroagent.data.ssh.SshHostCredentials

/** The Room upgrade transaction commits the credentials and the version together. */
internal class EncryptSshCredentialsMigration(
    private val credentials: SshHostCredentials = SshHostCredentials(),
    private val markCheckpointPending: () -> Unit = {},
) : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        markCheckpointPending()
        SQLiteConfiguration.enableSecureDelete(db)
        val encrypted = mutableListOf<Array<Any?>>()
        db.query(
            "SELECT name, password, privateKey, passphrase FROM ssh_hosts " +
                "WHERE password IS NOT NULL OR privateKey IS NOT NULL OR passphrase IS NOT NULL"
        ).use { rows ->
            while (rows.moveToNext()) {
                val name = rows.getString(0)
                fun protect(column: Int, field: String) = credentials.encrypt(
                    name, field, if (rows.isNull(column)) null else rows.getString(column),
                )
                encrypted += arrayOf(protect(1, "password"), protect(2, "privateKey"), protect(3, "passphrase"), name)
            }
        }
        for (args in encrypted) {
            db.execSQL("UPDATE ssh_hosts SET password = ?, privateKey = ?, passphrase = ? WHERE name = ?", args)
        }
    }
}
