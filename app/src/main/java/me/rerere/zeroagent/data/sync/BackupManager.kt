package me.rerere.zeroagent.data.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.zeroagent.data.datastore.Settings
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.datastore.migration.SettingsJsonMigrator
import me.rerere.zeroagent.data.db.AppDatabaseFactory
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.db.ImportedDatabaseReconciler
import me.rerere.zeroagent.data.db.SQLiteConfiguration
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Shared archive format and restore lifecycle for local, WebDAV and S3 backups. */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val settingsStore: SettingsStore,
    private val json: Json,
) {
    private val restoreMutex = Mutex()

    suspend fun createBackup(items: Collection<BackupItem>): File = withContext(Dispatchers.IO) {
        val selected = BackupItem.normalize(items).toSet()
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val archive = File.createTempFile("backup_${timestamp}_", ".zip", context.cacheDir)
        val staging = Files.createTempDirectory(context.cacheDir.toPath(), "backup-").toFile()
        try {
            ZipOutputStream(FileOutputStream(archive)).use { zip ->
                if (BackupItem.CONFIG in selected) {
                    val settings = settingsStore.settingsFlowRaw.first()
                    zip.putNextEntry(ZipEntry("settings.json"))
                    zip.write(stripBackupCredentials(json.parseToJsonElement(json.encodeToString(settings))).toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                if (BackupItem.DATABASE in selected) {
                    val snapshot = File(staging, SQLiteConfiguration.DATABASE_NAME)
                    DatabaseBackup.createSnapshot(database.openHelper.writableDatabase, snapshot)
                    addFile(zip, snapshot, DatabaseBackup.ARCHIVE_DATABASE)
                    val preferenceDirectory = File(context.applicationInfo.dataDir, BackupLayout.PORTABLE_PREFERENCES_DIR)
                    val lockMarkers = File(preferenceDirectory, BackupLayout.CHAT_PRIVACY_FILE)
                    if (lockMarkers.isFile) addFile(zip, lockMarkers, "${BackupLayout.PORTABLE_PREFERENCES_DIR}/${lockMarkers.name}")
                }
                if (BackupItem.CONFIG in selected) {
                    val projectData = me.rerere.zeroagent.data.productivity.ProjectStore.get(context).backupJson()
                    zip.putNextEntry(ZipEntry("productivity/state.json"))
                    zip.write(projectData.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    val portablePreferences = File(context.applicationInfo.dataDir, BackupLayout.PORTABLE_PREFERENCES_DIR)
                    for (name in BackupLayout.portablePreferenceFiles - BackupLayout.CHAT_PRIVACY_FILE) {
                        val file = File(portablePreferences, name)
                        if (!file.isFile) continue
                        currentCoroutineContext().ensureActive()
                        addFile(zip, file, "${BackupLayout.PORTABLE_PREFERENCES_DIR}/$name")
                    }
                    for (name in BackupLayout.configStoreFiles) {
                        val file = File(context.filesDir, "${BackupLayout.DATASTORE_DIR}/$name")
                        if (!file.isFile) continue
                        currentCoroutineContext().ensureActive()
                        addFile(zip, file, "${BackupLayout.DATASTORE_DIR}/$name")
                    }
                }
                for ((item, folder) in BackupLayout.subtreeFolders) {
                    if (item !in selected) continue
                    val directory = File(context.filesDir, folder)
                    addTree(zip, directory, folder)
                }
                if (BackupItem.WORKSPACES in selected) {
                    addWorkspaces(zip)
                }
            }
            archive
        } catch (e: Throwable) {
            archive.delete()
            throw e
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Archives every file under [directory] as `<prefix>/<relative path>`. */
    private suspend fun addTree(zip: ZipOutputStream, directory: File, prefix: String) {
        for (file in directory.walkTopDown()) {
            if (!file.isFile) continue
            currentCoroutineContext().ensureActive()
            val relative = file.relativeTo(directory).invariantSeparatorsPath
            PendingRestore.resolveInside(directory, relative)
            addFile(zip, file, "$prefix/$relative")
        }
    }

    /**
     * Workspace data, deliberately excluding the proot rootfs (`workspaces/<root>/linux`, often
     * hundreds of MB and reproducible by reinstalling) and scratch dirs (`tmp`). Two areas are
     * captured: the per-workspace `files/` tree, and the on-device agent's `~`.
     */
    private suspend fun addWorkspaces(zip: ZipOutputStream) {
        val roots = File(context.filesDir, BackupLayout.WORKSPACES_ROOT)
        for (root in roots.listFiles().orEmpty()) {
            if (!root.isDirectory) continue
            val filesDir = File(root, BackupLayout.WORKSPACE_FILES_DIR)
            addTree(
                zip = zip,
                directory = filesDir,
                prefix = "${BackupLayout.WORKSPACES_ROOT}/${root.name}/${BackupLayout.WORKSPACE_FILES_DIR}",
            )
        }
        addTree(
            zip = zip,
            directory = File(context.filesDir, BackupLayout.AGENT_WORKSPACE_DIR),
            prefix = BackupLayout.AGENT_WORKSPACE_DIR,
        )
    }

    suspend fun stageRestore(archive: File, items: Collection<BackupItem>) =
        withContext(Dispatchers.IO) {
            val selected = BackupItem.normalize(items).toSet()
            restoreMutex.withLock {
                val restore = pendingRestore(context)
                val staging = restore.createStagingDirectory()
                try {
                    val payload = File(staging, "payload")
                    val stagedDatabase = File(payload, "database/${SQLiteConfiguration.DATABASE_NAME}")
                    val stagedWal = File(stagedDatabase.path + "-wal")
                    val seen = mutableSetOf<String>()
                    var restoredEntries = 0
                    var extractedBytes = 0L
                    val extractionBudget = (staging.usableSpace - 64L * 1024 * 1024).coerceAtLeast(0L)
                    val buffer = ByteArray(64 * 1024)
                    ZipFile(archive).use { zip ->
                        for (entry in zip.entries()) {
                            currentCoroutineContext().ensureActive()
                            if (entry.isDirectory) continue
                            val target = when (entry.name) {
                                "settings.json" -> if (BackupItem.CONFIG in selected) File(staging, "settings.json") else null
                                DatabaseBackup.ARCHIVE_DATABASE ->
                                    if (BackupItem.DATABASE in selected) stagedDatabase else null

                                DatabaseBackup.WAL ->
                                    if (BackupItem.DATABASE in selected) stagedWal else null

                                DatabaseBackup.SHM -> null // Rebuilt by SQLite; never restore shared-memory state.
                                else -> {
                                    val item = BackupLayout.itemForEntry(entry.name)
                                    if (item != null && item in selected) {
                                        val isPreference = entry.name.startsWith("${BackupLayout.PORTABLE_PREFERENCES_DIR}/")
                                        val targetDirectory = if (isPreference) payload else File(payload, "files")
                                        PendingRestore.resolveInside(targetDirectory, entry.name)
                                    } else null
                                }
                            } ?: continue
                            require(seen.add(entry.name)) { "Duplicate backup entry: ${entry.name}" }
                            check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) {
                                "Cannot create backup staging directory"
                            }
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(target).use { output ->
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        extractedBytes += count
                                        require(extractedBytes <= extractionBudget) { "備份解壓後超出可用儲存空間。" }
                                        output.write(buffer, 0, count)
                                    }
                                    output.fd.sync()
                                }
                            }
                            restoredEntries++
                        }
                    }
                    require(restoredEntries > 0) { "No selected data found in the backup" }
                    require(!stagedWal.exists() || stagedDatabase.exists()) { "Backup WAL has no matching database" }
                    val privacyMarkers = File(payload, "${BackupLayout.PORTABLE_PREFERENCES_DIR}/${BackupLayout.CHAT_PRIVACY_FILE}")
                    require(!privacyMarkers.exists() || stagedDatabase.exists()) { "隱私標記必須與聊天資料庫一同還原。" }
                    if (stagedDatabase.exists()) {
                        DatabaseBackup.normalize(context, stagedDatabase)
                        // A backup exported from upstream ZeroAgent lacks the fork-only tables and
                        // carries upstream's schema version; reconcile the staged copy before Room
                        // opens it so the import neither fails validation nor crashes on first launch.
                        ImportedDatabaseReconciler.reconcileDatabaseFile(stagedDatabase)
                        // Reject unsupported schemas before publishing; run supported old migrations on the copy.
                        val room = AppDatabaseFactory.create(context, stagedDatabase.absolutePath)
                        try {
                            DatabaseBackup.checkpoint(room.openHelper.writableDatabase)
                        } finally {
                            room.close()
                        }
                        DatabaseBackup.removeSidecars(stagedDatabase)
                    }

                    val settingsFile = File(staging, "settings.json")
                    if (settingsFile.exists()) {
                        val settings = json.decodeFromString<Settings>(SettingsJsonMigrator.migrate(settingsFile.readText()))
                        require(!settings.init) { "Backup contains uninitialized settings" }
                        // Persist the migrated value once, including generated IDs, for restart/retry consistency.
                        PendingRestore.writeDurably(settingsFile, json.encodeToString(settings))
                    }
                    currentCoroutineContext().ensureActive()
                    restore.publish(staging)
                } finally {
                    staging.deleteRecursively()
                }
            }
        }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    companion object {
        private fun pendingRestore(context: Context) = PendingRestore(
            root = File(context.noBackupFilesDir, "backup-restore"),
            databaseFile = context.getDatabasePath(SQLiteConfiguration.DATABASE_NAME),
            filesDir = context.filesDir,
            sharedPreferencesDir = File(context.applicationInfo.dataDir, BackupLayout.PORTABLE_PREFERENCES_DIR),
        )

        /** Must finish before Koin, Room, SettingsStore or any background consumers are initialized. */
        suspend fun applyPendingRestore(context: Context, json: Json): Boolean = withContext(Dispatchers.IO) {
            pendingRestore(context).apply { settingsJson ->
                SettingsStore.restoreBeforeInitialization(context, json.decodeFromString<Settings>(settingsJson))
            }.also { restored ->
                if (restored) me.rerere.zeroagent.data.telegram.purgeRetiredTelegramFiles(context.filesDir)
            }
        }
    }
}
