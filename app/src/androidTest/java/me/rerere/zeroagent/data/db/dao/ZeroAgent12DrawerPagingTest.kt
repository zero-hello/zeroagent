package me.rerere.zeroagent.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import kotlinx.coroutines.runBlocking
import me.rerere.zeroagent.data.db.AppDatabase
import me.rerere.zeroagent.data.db.entity.ConversationEntity
import me.rerere.zeroagent.data.repository.LightConversationEntity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ZeroAgent12DrawerPagingTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ConversationDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).openHelperFactory(RequerySQLiteOpenHelperFactory()).build()
        dao = database.conversationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun defaultUnfiledValueAppearsInDrawerAndHistory() = runBlocking {
        dao.insert(conversation("unfiled"))
        dao.insert(conversation("filed", folderId = "folder"))

        assertEquals(listOf("unfiled"), loadAllIds(dao.getUnfiledPaging()))
        assertEquals(2, dao.countAll())
        assertEquals(listOf("filed"), loadAllIds(dao.getConversationsOfFolderPaging("folder")))
    }

    @Test
    fun unfiledPagingIncludesOtherAssistantsAndKeepsPinnedDateOrderAcrossPages() = runBlocking {
        val entries = (0 until 65).map { index ->
            conversation("chat-$index", assistantId = if (index % 2 == 0) "a" else "b")
                .copy(isPinned = index == 5, updateAt = index.toLong())
        }
        entries.forEach { dao.insert(it) }
        dao.insert(conversation("filed-newest", folderId = "folder").copy(updateAt = 1_000))

        val expected = entries.sortedWith(
            compareByDescending<ConversationEntity> { it.isPinned }.thenByDescending { it.updateAt }
        ).map { it.id }
        val actual = loadAllIds(dao.getUnfiledPaging())
        assertEquals(expected, actual)
        assertEquals(65, actual.distinct().size)
        assertEquals(
            expected.filter { id -> entries.first { it.id == id }.assistantId == "a" },
            loadAllIds(dao.getUnfiledConversationsOfAssistantPaging("a")),
        )
    }

    @Test
    fun removingFolderReturnsConversationToDefaultDrawerQuery() = runBlocking {
        dao.insert(conversation("chat", folderId = "folder"))
        assertTrue(loadAllIds(dao.getUnfiledPaging()).isEmpty())

        dao.clearFolder("folder")

        assertEquals(listOf("chat"), loadAllIds(dao.getUnfiledPaging()))
        assertTrue(loadAllIds(dao.getConversationsOfFolderPaging("folder")).isEmpty())
    }

    private fun conversation(
        id: String,
        assistantId: String = "a",
        folderId: String = "",
    ) = ConversationEntity(
        id = id, assistantId = assistantId, title = id, nodes = "[]",
        createAt = 0, updateAt = 0, chatSuggestions = "[]", isPinned = false,
        folderId = folderId,
    )

    private suspend fun loadAllIds(source: PagingSource<Int, LightConversationEntity>): List<String> {
        val ids = mutableListOf<String>()
        try {
            var params: PagingSource.LoadParams<Int> = PagingSource.LoadParams.Refresh(
                key = null, loadSize = 40, placeholdersEnabled = false,
            )
            do {
                val result = source.load(params)
                check(result is PagingSource.LoadResult.Page) { "Unexpected paging result: $result" }
                ids += result.data.map { it.id }
                val next = result.nextKey ?: break
                params = PagingSource.LoadParams.Append(next, loadSize = 20, placeholdersEnabled = false)
            } while (true)
        } finally {
            source.invalidate()
        }
        return ids
    }
}
