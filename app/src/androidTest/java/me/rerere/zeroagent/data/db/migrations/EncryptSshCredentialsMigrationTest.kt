package me.rerere.zeroagent.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.db.entity.SshHostEntity
import me.rerere.zeroagent.data.ssh.SshHostCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptSshCredentialsMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun encryptsLegacyCredentialsAndPreservesTheHost() {
        val original = SshHostEntity(
            name = "fixture", host = "test.invalid", user = "user", password = "test-password",
            privateKey = "test-private-key", passphrase = "test-passphrase", createdAtMs = 1,
        )
        val name = "migration-32-33-ssh-test"
        helper.createDatabase(name, 32).use { db ->
            db.execSQL(
                "INSERT INTO ssh_hosts (name, host, port, user, password, privateKey, passphrase, createdAtMs) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(original.name, original.host, original.port, original.user, original.password,
                    original.privateKey, original.passphrase, original.createdAtMs),
            )
        }
        helper.runMigrationsAndValidate(name, 33, true, EncryptSshCredentialsMigration()).use { db ->
            db.query("SELECT host, password, privateKey, passphrase FROM ssh_hosts WHERE name = 'fixture'").use { row ->
                assertTrue(row.moveToFirst())
                val stored = original.copy(host = row.getString(0), password = row.getString(1),
                    privateKey = row.getString(2), passphrase = row.getString(3))
                assertNotEquals(original.password, stored.password)
                assertNotEquals(original.privateKey, stored.privateKey)
                assertNotEquals(original.passphrase, stored.passphrase)
                assertEquals(original, SshHostCredentials().decrypt(stored))
            }
        }
    }
}
