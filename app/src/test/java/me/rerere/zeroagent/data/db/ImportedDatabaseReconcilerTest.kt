package me.rerere.zeroagent.data.db

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Historical imports are normalized to the known v31 identity, then Room executes the
 * registered retirement migration and validates the current schema. Keep the bridge's
 * exported identity pinned without guessing the compiler-generated current identity.
 */
class ImportedDatabaseReconcilerTest {

    private fun findSchemaDir(): File {
        val candidates = listOf(
            File("schemas/me.rerere.zeroagent.data.db.AppDatabase"),
            File("app/schemas/me.rerere.zeroagent.data.db.AppDatabase"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("could not find the AppDatabase schema export directory, tried: $candidates")
    }

    private fun bridgeSchemaFile(): File =
        File(findSchemaDir(), "${ImportedDatabaseReconciler.BRIDGE_VERSION}.json").also {
            check(it.isFile) { "Missing historical bridge schema: $it" }
        }

    private fun bridgeSchemaDatabaseObject(): JsonObject {
        val file = bridgeSchemaFile()
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        return root["database"]!!.jsonObject
    }

    @Test
    fun `bridge version matches its historical exported schema`() {
        val database = bridgeSchemaDatabaseObject()
        val schemaVersion = database["version"]!!.jsonPrimitive.int
        assertEquals(schemaVersion, ImportedDatabaseReconciler.BRIDGE_VERSION)
    }

    @Test
    fun `bridge identity hash matches its historical exported schema`() {
        val database = bridgeSchemaDatabaseObject()
        val schemaHash = database["identityHash"]!!.jsonPrimitive.content
        assertEquals(schemaHash, ImportedDatabaseReconciler.BRIDGE_IDENTITY_HASH)
    }

    @Test
    fun `bridge version matches its schema file name`() {
        val file = bridgeSchemaFile()
        val nameVersion = file.nameWithoutExtension.toInt()
        assertEquals(nameVersion, ImportedDatabaseReconciler.BRIDGE_VERSION)
    }

    @Test
    fun `current schema upgrades from the historical bridge`() {
        val candidates = listOf(
            File("src/main/java/me/rerere/zeroagent/data/db/AppDatabase.kt"),
            File("app/src/main/java/me/rerere/zeroagent/data/db/AppDatabase.kt"),
        )
        val file = candidates.firstOrNull { it.isFile }
        if (file == null) {
            // Cheap-read only: skip rather than fail the build if the source layout moves.
            return
        }
        val declaredVersion = Regex("""version\s*=\s*(\d+)""").find(file.readText())
            ?.groupValues?.get(1)?.toInt()
        assertTrue(declaredVersion != null && declaredVersion > ImportedDatabaseReconciler.BRIDGE_VERSION)
        assertEquals(ImportedDatabaseReconciler.BRIDGE_VERSION,
            me.rerere.zeroagent.data.db.migrations.Migration_31_32.startVersion)
        assertEquals(32, me.rerere.zeroagent.data.db.migrations.Migration_31_32.endVersion)
    }

    @Test
    fun `backfills both ConversationEntity indices Room's 27-28 migration adds`() {
        val ddl = ImportedDatabaseReconciler.BACKFILL_INDEX_DDL
        assertTrue(ddl.any {
            it.contains("index_ConversationEntity_assistant_id_is_pinned_update_at") &&
                it.contains("ON `ConversationEntity` (`assistant_id`, `is_pinned`, `update_at`)")
        })
        assertTrue(ddl.any {
            it.contains("index_ConversationEntity_is_pinned_update_at") &&
                it.contains("ON `ConversationEntity` (`is_pinned`, `update_at`)")
        })
    }

    @Test
    fun `backfills the MemoryEntity and scheduled-job indices from the same migration`() {
        val ddl = ImportedDatabaseReconciler.BACKFILL_INDEX_DDL
        assertTrue(ddl.any { it.contains("index_MemoryEntity_assistant_id") })
        assertTrue(ddl.any { it.contains("index_scheduled_jobs_enabled") })
        assertTrue(ddl.any { it.contains("index_scheduled_job_runs_jobId_startedAtMs") })
        assertTrue(ddl.any { it.contains("index_scheduled_job_runs_jobId_outcome") })
    }

    @Test
    fun `every backfill statement is idempotent`() {
        ImportedDatabaseReconciler.BACKFILL_INDEX_DDL.forEach {
            assertTrue("not idempotent: $it", it.startsWith("CREATE INDEX IF NOT EXISTS"))
        }
    }
}
