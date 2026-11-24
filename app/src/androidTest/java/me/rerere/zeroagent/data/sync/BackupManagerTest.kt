package me.rerere.zeroagent.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.rerere.zeroagent.data.datastore.SettingsStore
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.db.AppDatabaseFactory
import me.rerere.zeroagent.data.db.ChatPayloadStore
import me.rerere.zeroagent.data.db.dao.readMessagePayload
import me.rerere.zeroagent.data.db.entity.ConversationEntity
import me.rerere.zeroagent.data.db.entity.MessageNodeEntity
import me.rerere.zeroagent.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private lateinit var directory: File
    private lateinit var context: Context
    private lateinit var liveDatabase: AppDatabase
    private lateinit var manager: BackupManager

    @Before fun setUp() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        directory = Files.createTempDirectory(app.cacheDir.toPath(), "backup-manager-test-").toFile()
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(directory, "no-backup").apply { mkdirs() }
            override fun getDatabasePath(name: String): File = if (File(name).isAbsolute) File(name)
                else File(directory, "databases/$name").also { it.parentFile!!.mkdirs() }
        }
        liveDatabase = AppDatabaseFactory.create(context)
        liveDatabase.openHelper.writableDatabase.execSQL("CREATE TABLE backup_probe (text TEXT)")
        liveDatabase.openHelper.writableDatabase.execSQL("INSERT INTO backup_probe VALUES ('live')")
        manager = BackupManager(context, liveDatabase, GlobalContext.get().get<SettingsStore>(), JsonInstant)
    }

    @After fun tearDown() {
        liveDatabase.close()
        directory.deleteRecursively()
    }

    @Test fun oldArchiveIgnoresShmAndLeavesLiveDatabaseUntouchedUntilStartup() = runBlocking {
        val source = File(directory, "source")
        val archive = File(directory, "legacy.zip")
        withRoom(source) { room ->
            val db = room.openHelper.writableDatabase
            db.execSQL("CREATE TABLE backup_probe (text TEXT)")
            DatabaseBackup.checkpoint(db)
            db.query("PRAGMA wal_autocheckpoint=0").use { assertTrue(it.moveToFirst()) }
            db.execSQL("INSERT INTO backup_probe VALUES ('archived')")
            ZipOutputStream(archive.outputStream()).use { zip ->
                addFile(zip, DatabaseBackup.ARCHIVE_DATABASE, source)
                addFile(zip, DatabaseBackup.WAL, File(source.path + "-wal"))
                zip.putNextEntry(ZipEntry(DatabaseBackup.SHM))
                zip.write("deliberately invalid SHM".toByteArray())
                zip.closeEntry()
            }
        }
        manager.stageRestore(archive, listOf(BackupItem.DATABASE))
        assertEquals("live", probe(liveDatabase))
        val staged = File(context.noBackupFilesDir, "backup-restore/pending/payload/database/rikka_hub")
        assertTrue(staged.isFile)
        assertFalse(File(staged.path + "-wal").exists())
        assertFalse(File(staged.path + "-shm").exists())
        liveDatabase.close()
        BackupManager.applyPendingRestore(context, JsonInstant)
        liveDatabase = AppDatabaseFactory.create(context)
        assertEquals("archived", probe(liveDatabase))
        ZipFile(archive).use { assertTrue(it.getEntry(DatabaseBackup.SHM) != null) }
    }

    @Test fun newArchiveContainsStandaloneDatabaseAndNoWalOrShm() = runBlocking {
        val archive = manager.createBackup(listOf(BackupItem.DATABASE))
        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("settings.json") != null)
            assertTrue(zip.getEntry(DatabaseBackup.ARCHIVE_DATABASE) != null)
            assertEquals(null, zip.getEntry(DatabaseBackup.WAL))
            assertEquals(null, zip.getEntry(DatabaseBackup.SHM))
        }
        manager.stageRestore(archive, listOf(BackupItem.DATABASE))
        assertEquals("live", probe(liveDatabase))
    }

    @Test fun largeChatPayloadIsIncludedInADatabaseOnlyBackup() = runBlocking {
        val content = """[{"role":"assistant","parts":[{"type":"text","text":"${"界🌱".repeat(350_000)}"}]}]"""
        val store = ChatPayloadStore(context, liveDatabase)
        val dao = liveDatabase.messageNodeDao()
        liveDatabase.withTransaction {
            liveDatabase.conversationDao().insert(
                ConversationEntity("room", "assistant", "Title", "[]", 0, 0, "[]", false),
            )
            dao.insert(store.encode(MessageNodeEntity("node", "room", 0, content, 0)))
            val node = requireNotNull(dao.getBoundedNode("node"))
            assertTrue(node.payloadSha256 != null)
            assertEquals(content, store.load(node))
        }
        val archive = manager.createBackup(listOf(BackupItem.DATABASE))
        val standalone = File(directory, "standalone")
        ZipFile(archive).use { zip ->
            zip.getInputStream(zip.getEntry(DatabaseBackup.ARCHIVE_DATABASE)).use { input ->
                standalone.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val room = AppDatabaseFactory.create(context, standalone.absolutePath)
        try {
            room.withTransaction {
                val portableDao = room.messageNodeDao()
                val node = requireNotNull(portableDao.getBoundedNode("node"))
                assertEquals(null, node.payloadSha256)
                assertEquals(content, portableDao.readMessagePayload(node))
            }
        } finally {
            room.close()
        }
        manager.stageRestore(archive, listOf(BackupItem.DATABASE))
    }

    @Test fun unsupportedSchemaIsRejectedBeforePublishing() = runBlocking {
        val future = File(directory, "future")
        withRoom(future) {
            it.openHelper.writableDatabase.version = 9999
        }
        val archive = File(directory, "future.zip")
        ZipOutputStream(archive.outputStream()).use { addFile(it, DatabaseBackup.ARCHIVE_DATABASE, future) }
        var failed = false
        try {
            manager.stageRestore(archive, listOf(BackupItem.DATABASE))
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
        assertFalse(File(context.noBackupFilesDir, "backup-restore/pending").exists())
        assertEquals("live", probe(liveDatabase))
    }

    private fun probe(database: AppDatabase): String = database.openHelper.readableDatabase
        .query("SELECT text FROM backup_probe").use { check(it.moveToFirst()); it.getString(0) }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun <T> withRoom(file: File, block: (AppDatabase) -> T): T {
        val room = AppDatabaseFactory.create(context, file.path)
        return try {
            block(room)
        } finally {
            room.close()
        }
    }
}
