package me.rerere.zeroagent.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

/**
 * Repairs old/upstream backup schemas before Room opens them. Shared modern columns are
 * detected before replaying historical migrations that would otherwise add them twice.
 * Complete historical imports are normalized to the exported v31 identity as a bridge;
 * Room then executes Migration_31_32, removes retired data and writes its own v32 identity.
 * Missing fork-only tables and indices are created idempotently. Already-v32 and newer
 * files are left untouched. No destructive fallback or guessed current identity is used.
 */
object ImportedDatabaseReconciler {

    private const val TAG = "DbReconciler"
    private const val DB_NAME = "rikka_hub"

    /**
     * Deliberately reconcile old imports to the known v31 bridge, then let Room execute
     * Migration_31_32 and install its generated v32 identity. Never fabricate a new hash.
     * Already-v32 (or newer) backups remain untouched.
     */
    internal const val BRIDGE_VERSION = 31
    internal const val BRIDGE_IDENTITY_HASH = "61a9c9769b0c9f68743007339a58e420"

    /**
     * Columns that a restored file must already have for its shared schema to be considered
     * byte-for-byte equal to the fork's current schema. They are exactly the columns the
     * fork's 24->25 / 25->26 / 26->27 auto-migrations add (custom_system_prompt at 25,
     * workspace_cwd at 26, folder_id at 27), so a file carrying all three would collide on
     * every one of those replays. Upstream 2.4.x carries all three; a genuine fork file below
     * v27 carries only a prefix, so it still migrates normally.
     */
    private val MODERN_COLUMN_SENTINELS = listOf("custom_system_prompt", "workspace_cwd", "folder_id")

    private const val CONTEXT_COMPACTION_DDL =
        "CREATE TABLE IF NOT EXISTS `conversation_compaction` (`conversation_id` TEXT NOT NULL, `summary` TEXT NOT NULL, `tail_start_node_id` TEXT, `source_end_node_id` TEXT NOT NULL, `summary_model_id` TEXT NOT NULL, `is_auto` INTEGER NOT NULL, `source_token_estimate` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`conversation_id`), FOREIGN KEY(`conversation_id`) REFERENCES `ConversationEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    /**
     * v31 adds this column to `workspaces` via [me.rerere.zeroagent.data.db.migrations.Migration_30_31].
     * That migration never runs on the "already current" path below, so it has to be backfilled
     * here too (issue #105), same reasoning as `chat_model_id` further down. DDL byte-identical
     * to the migration's.
     */
    private const val SHELL_COMPATIBILITY_MODE_DDL =
        "ALTER TABLE `workspaces` ADD COLUMN `shell_compatibility_mode` INTEGER NOT NULL DEFAULT 0"

    /**
     * Fork-only tables absent from an upstream backup, with their exact v25 create + index
     * statements. Every statement is IF NOT EXISTS so running it against a genuine agent
     * backup (where the tables already exist) is a no-op.
     */
    private val FORK_ONLY_DDL: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `scheduled_jobs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `prompt` TEXT, `assistantId` TEXT NOT NULL, `scheduleType` TEXT NOT NULL, `atUnixMs` INTEGER, `intervalSeconds` INTEGER, `enabled` INTEGER NOT NULL, `createdAtMs` INTEGER NOT NULL, `lastRunAtMs` INTEGER, `nextRunAtMs` INTEGER, `mode` TEXT NOT NULL DEFAULT 'llm', `actionsJson` TEXT, `cronExpression` TEXT, `timezone` TEXT, `startAtUnixMs` INTEGER, `endAtUnixMs` INTEGER, `maxRuns` INTEGER, `runsSoFar` INTEGER NOT NULL DEFAULT 0, `catchup` TEXT NOT NULL DEFAULT 'fire_once', `description` TEXT, `tags` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `scheduled_job_runs` (`id` TEXT NOT NULL, `jobId` TEXT NOT NULL, `mode` TEXT NOT NULL, `scheduledAtMs` INTEGER NOT NULL, `startedAtMs` INTEGER NOT NULL, `finishedAtMs` INTEGER, `outcome` TEXT NOT NULL, `conversationId` TEXT, `errorMessage` TEXT, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `ssh_hosts` (`name` TEXT NOT NULL, `host` TEXT NOT NULL, `port` INTEGER NOT NULL, `user` TEXT NOT NULL, `password` TEXT, `privateKey` TEXT, `passphrase` TEXT, `createdAtMs` INTEGER NOT NULL, PRIMARY KEY(`name`))",
        "CREATE TABLE IF NOT EXISTS `workflows` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `enabled` INTEGER NOT NULL DEFAULT 1, `definitionJson` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, `lastRunAtMs` INTEGER, `lastRunStatus` TEXT, `lastRunError` TEXT, `runsTodayCount` INTEGER NOT NULL DEFAULT 0, `runsTodayDate` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `workflow_runs` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `workflowId` TEXT NOT NULL, `firedAtMs` INTEGER NOT NULL, `status` TEXT NOT NULL, `durationMs` INTEGER NOT NULL, `errorMessage` TEXT)",
        "CREATE INDEX IF NOT EXISTS `index_workflow_runs_workflowId_firedAtMs` ON `workflow_runs` (`workflowId`, `firedAtMs`)",
        "CREATE TABLE IF NOT EXISTS `agent_runs` (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `domain_id` TEXT NOT NULL, `parent_run_id` TEXT, `status` TEXT NOT NULL, `created_at_ms` INTEGER NOT NULL, `updated_at_ms` INTEGER NOT NULL, `started_at_ms` INTEGER, `finished_at_ms` INTEGER, `last_error` TEXT, `metadata_json` TEXT, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `idx_runs_status` ON `agent_runs` (`status`)",
        "CREATE INDEX IF NOT EXISTS `idx_runs_kind_dom` ON `agent_runs` (`kind`, `domain_id`)",
        "CREATE INDEX IF NOT EXISTS `idx_runs_parent` ON `agent_runs` (`parent_run_id`)",
        "CREATE INDEX IF NOT EXISTS `idx_runs_updated_at` ON `agent_runs` (`updated_at_ms`)",
    )

    /**
     * Indices the fork's 27->28 auto-migration adds on tables that already exist before that
     * step (`ConversationEntity`, `MemoryEntity`) or that [FORK_ONLY_DDL] just created fresh
     * (`scheduled_jobs`, `scheduled_job_runs`). That migration is a pure schema diff compiled
     * from app/schemas/.../27.json and 28.json, so it never runs on the "already current" path
     * below, which stamps the file straight to [BRIDGE_VERSION]: the indices would otherwise
     * be silently missing (issue #60, `Found: indices = {}` on `ConversationEntity`). Every
     * statement is `IF NOT EXISTS`, so running this against a database that already has the
     * indices - including a genuine backup already on v30 - is a safe no-op that touches no data.
     */
    internal val BACKFILL_INDEX_DDL: List<String> = listOf(
        "CREATE INDEX IF NOT EXISTS `index_ConversationEntity_assistant_id_is_pinned_update_at` ON `ConversationEntity` (`assistant_id`, `is_pinned`, `update_at`)",
        "CREATE INDEX IF NOT EXISTS `index_ConversationEntity_is_pinned_update_at` ON `ConversationEntity` (`is_pinned`, `update_at`)",
        "CREATE INDEX IF NOT EXISTS `index_MemoryEntity_assistant_id` ON `MemoryEntity` (`assistant_id`)",
        "CREATE INDEX IF NOT EXISTS `index_scheduled_jobs_enabled` ON `scheduled_jobs` (`enabled`)",
        "CREATE INDEX IF NOT EXISTS `index_scheduled_job_runs_jobId_startedAtMs` ON `scheduled_job_runs` (`jobId`, `startedAtMs`)",
        "CREATE INDEX IF NOT EXISTS `index_scheduled_job_runs_jobId_outcome` ON `scheduled_job_runs` (`jobId`, `outcome`)",
    )

    /**
     * Call after a restore has written the database file, and only when the restore actually
     * included the database. Safe to call when the file is a genuine agent backup (every
     * statement is idempotent) or when the file does not exist (no-op).
     */
    fun reconcile(context: Context) {
        reconcileDatabaseFile(context.getDatabasePath(DB_NAME))
    }

    /**
     * Testable core of [reconcile]: operate on the raw db file at [dbFile] directly, so a test
     * can exercise it against a temp file instead of the app's live `rikka_hub` database.
     */
    internal fun reconcileDatabaseFile(dbFile: File) {
        if (!dbFile.exists()) {
            Log.i(TAG, "reconcile: no database file at ${dbFile.absolutePath}, skipping")
            return
        }
        try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { db ->
                val version = db.version // PRAGMA user_version
                if (version > BRIDGE_VERSION) {
                    Log.w(TAG, "reconcile: backup db version $version is newer than $BRIDGE_VERSION; leaving untouched")
                    return
                }

                // A file whose shared schema already matches the fork's current schema must not
                // be migrated: Room would replay the fork's 24->27 auto-migrations and re-ADD
                // columns the file already has, crashing with "duplicate column name" (an
                // upstream 2.4.x backup stamps user_version 24 but carries every modern column;
                // see issues #10, #11). Detect that case by the sentinel columns those very
                // migrations add.
                val alreadyCurrent = MODERN_COLUMN_SENTINELS.all {
                    hasColumn(db, "ConversationEntity", it)
                }

                db.beginTransaction()
                try {
                    FORK_ONLY_DDL.forEach(db::execSQL)
                    BACKFILL_INDEX_DDL.forEach(db::execSQL)

                    if (version == BRIDGE_VERSION || alreadyCurrent) {
                        // v29 adds this table and v30 adds the chat_model_id column below,
                        // both through Room auto-migrations. Imported upstream databases and
                        // genuine v27/v28 fork databases are stamped straight to
                        // BRIDGE_VERSION here, so create/add them before installing the
                        // identity for BRIDGE_VERSION.
                        db.execSQL(CONTEXT_COMPACTION_DDL)
                        if (!hasColumn(db, "ConversationEntity", "chat_model_id")) {
                            db.execSQL(
                                "ALTER TABLE `ConversationEntity` ADD COLUMN `chat_model_id` TEXT NOT NULL DEFAULT ''"
                            )
                        }
                        // A restored file without a `workspaces` table (pre-2.3 upstream) has
                        // nothing to add this column to; running the ALTER would fail and roll
                        // back the whole transaction, so only attempt it when the table exists.
                        if (tableExists(db, "workspaces") &&
                            !hasColumn(db, "workspaces", "shell_compatibility_mode")
                        ) {
                            db.execSQL(SHELL_COMPATIBILITY_MODE_DDL)
                        }
                        // No migration should run: the file is either already stamped at the
                        // fork's version, or it is an upstream file whose shared schema already
                        // matches it. Point Room's identity row and user_version at the fork so
                        // the integrity check passes now that every fork-only table is present.
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)"
                        )
                        db.execSQL(
                            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
                            arrayOf(BRIDGE_IDENTITY_HASH),
                        )
                        if (version != BRIDGE_VERSION) {
                            // PRAGMA user_version is transactional (SQLiteOpenHelper sets it the
                            // same way inside its own upgrade transaction), so this commits or
                            // rolls back atomically with the DDL and identity row above.
                            db.version = BRIDGE_VERSION
                        }
                    }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                Log.i(TAG, "reconcile: reconciled imported db (version=$version, alreadyCurrent=$alreadyCurrent)")
            }
        } catch (t: Throwable) {
            // Never let reconciliation break the restore. Worst case is the pre-existing
            // behaviour (a crash on next open); the user's rows are still on disk.
            Log.w(TAG, "reconcile: failed to reconcile imported db", t)
        }
    }

    /** True if [table] exists and has a column named [column]. Best-effort; false on error. */
    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean {
        return try {
            db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                var found = false
                if (nameIndex >= 0) {
                    while (!found && cursor.moveToNext()) {
                        found = cursor.getString(nameIndex) == column
                    }
                }
                found
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hasColumn: failed to inspect $table.$column", t)
            false
        }
    }

    /** True if [table] exists. Best-effort; false on error. */
    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        return try {
            db.rawQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table),
            ).use { cursor -> cursor.moveToFirst() }
        } catch (t: Throwable) {
            Log.w(TAG, "tableExists: failed to check $table", t)
            false
        }
    }
}
