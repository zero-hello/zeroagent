package me.rerere.zeroagent.data.productivity

import org.junit.Assert.*
import org.junit.Test

class ProjectRetrievalTest {
    @Test fun `excluded documents are never retrieved`() {
        val documents = listOf(KnowledgeDocument(name = "private.txt", text = "secret invoice", included = false),
            KnowledgeDocument(name = "public.txt", text = "invoice details"))
        val hits = ProjectRetrieval.search(documents, "invoice")
        assertEquals(listOf("public.txt"), hits.map { it.name })
    }

    @Test fun `Chinese questions retrieve actual named chunks without chat transcript nodes`() {
        val documents = listOf(KnowledgeDocument(name = "課程.txt", text = "下週課程討論線性代數與矩陣運算"),
            KnowledgeDocument(name = "行程.txt", text = "週末旅行的交通安排"))
        val hits = ProjectRetrieval.search(documents, "矩陣運算的課程")
        assertEquals("課程.txt", hits.first().name)
        assertTrue(hits.first().text.contains("矩陣"))
        assertEquals(1, hits.first().chunk)
    }

    @Test fun `project context cannot contain documents from another project`() {
        val first = ChatProject(name = "工作", documents = listOf(KnowledgeDocument(name = "work.txt", text = "invoice work")))
        val second = ChatProject(name = "私人", documents = listOf(KnowledgeDocument(name = "private.txt", text = "invoice private")))
        assertTrue(ProjectRetrieval.context(first, "invoice").contains("work.txt §1"))
        assertFalse(ProjectRetrieval.context(first, "invoice").contains("private.txt"))
        assertFalse(ProjectRetrieval.context(second, "invoice").contains("work.txt"))
    }
}
