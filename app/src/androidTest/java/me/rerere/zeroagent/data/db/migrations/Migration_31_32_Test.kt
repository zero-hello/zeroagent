package me.rerere.zeroagent.data.db.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.rerere.zeroagent.data.db.AppDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration_31_32_Test {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test fun removesRetiredDataAndPreservesOtherTablesAndActions() {
        val name = "migration-31-32-test"
        helper.createDatabase(name, 31).apply {
            execSQL("INSERT INTO telegram_chats VALUES (1, 'conversation', 1, 1)")
            execSQL("INSERT INTO ConversationEntity (id, title, nodes, create_at, update_at) VALUES ('conversation', 'keep', '[]', 1, 1)")
            execSQL("INSERT INTO ssh_hosts (name, host, port, user, createdAtMs) VALUES ('keep', 'example.com', 22, 'user', 1)")
            execSQL("INSERT INTO agent_runs (id, kind, domain_id, status, created_at_ms, updated_at_ms) VALUES ('tg', 'telegram', 'c', 'succeeded', 1, 1)")
            execSQL("INSERT INTO agent_runs (id, kind, domain_id, parent_run_id, status, created_at_ms, updated_at_ms) VALUES ('child', 'subagent', 'c', 'tg', 'succeeded', 1, 1)")
            listOf("retired" to "telegram_send_message", "keep" to "post_notification").forEach { (id, tool) ->
                execSQL("INSERT INTO scheduled_jobs (id, name, assistantId, scheduleType, enabled, createdAtMs, mode, actionsJson, nextRunAtMs) VALUES (?, ?, 'profile', 'once', 1, 1, 'direct', ?, 100)",
                    arrayOf(id, id, """[{"tool":"$tool","args":{"token":"fixture-only"}}]"""))
                execSQL("INSERT INTO workflows (id, name, enabled, definitionJson, createdAtMs, updatedAtMs) VALUES (?, ?, 1, ?, 1, 1)",
                    arrayOf(id, id, """{"name":"$id","enabled":true,"trigger":{"type":"manual"},"actions":[{"tool":"$tool","args":{"token":"fixture-only"}}]}"""))
            }
            close()
        }
        helper.runMigrationsAndValidate(name, 32, true, Migration_31_32).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE name='telegram_chats'").use { assertFalse(it.moveToFirst()) }
            db.query("SELECT title FROM ConversationEntity WHERE id='conversation'").use { assertTrue(it.moveToFirst()); assertEquals("keep", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM ssh_hosts").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.query("SELECT parent_run_id FROM agent_runs WHERE id='child'").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
            db.query("SELECT COUNT(*) FROM agent_runs WHERE kind='telegram'").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("SELECT enabled, nextRunAtMs, actionsJson FROM scheduled_jobs WHERE id='retired'").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertTrue(it.isNull(1)); assertFalse(it.getString(2).contains("fixture-only"))
            }
            db.query("SELECT enabled, actionsJson FROM scheduled_jobs WHERE id='keep'").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)); assertTrue(it.getString(1).contains("fixture-only"))
            }
            db.query("SELECT enabled, definitionJson FROM workflows WHERE id='retired'").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)); assertFalse(it.getString(1).contains("fixture-only"))
                assertTrue(it.getString(1).contains("manual"))
            }
            db.query("SELECT enabled, definitionJson FROM workflows WHERE id='keep'").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)); assertTrue(it.getString(1).contains("fixture-only"))
            }
        }
    }
}
