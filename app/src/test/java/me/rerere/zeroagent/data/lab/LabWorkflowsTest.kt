package me.rerere.zeroagent.data.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class LabWorkflowsTest {
    @Test
    fun recipeRendersChineseSlotsAndLeavesUnsetSlotsVisible() {
        assertEquals("主題：介面\n讀者：{{讀者}}", renderRecipe("主題：{{主題}}\n讀者：{{讀者}}", mapOf("主題" to "介面")))
    }

    @Test
    fun diffPreservesIndentationAndShowsBothVersions() {
        val result = labDiff("標題\n  舊內容\n尾端", "標題\n    新內容\n尾端")
        assertEquals(listOf(DiffLine(' ', "標題"), DiffLine('-', "  舊內容"), DiffLine('+', "    新內容"), DiffLine(' ', "尾端")), result)
    }

    @Test
    fun csvParsesEscapedQuotesAndMultilineFieldsAndReportsBadRows() {
        val text = "name,amount,ready\r\n\"hello,\"\"world\"\"\nsecond line\",12.50,true\r\nsecond,a,maybe"
        assertEquals(listOf("第 3 列的 amount 應為 number。", "第 3 列的 ready 應為 boolean。"), validateLabOutput("CSV", text, "amount:number,ready:boolean"))
        assertThrows(IllegalArgumentException::class.java) { validateLabOutput("CSV", "name\n\"unfinished", "name:string") }
    }

    @Test
    fun jsonChecksRealTypesAndMissingFields() {
        assertEquals(listOf("欄位 total 應為 number，實際為 string。", "缺少欄位 rows。"), validateLabOutput("JSON", """{"total":"12","valid":true}""", "total:number,valid:boolean,rows:array"))
        assertTrue(validateLabOutput("JSON", """{"total":12,"rows":[]}""", "total:number,rows:array").isEmpty())
    }

    @Test
    fun formulaAppliesPrecedenceVariablesAndUnaryOperators() {
        assertEquals(0, BigDecimal("8").compareTo(evaluateLabFormula("2 + 3 * (hour - -1)", mapOf("hour" to BigDecimal.ONE))))
        assertThrows(ArithmeticException::class.java) { evaluateLabFormula("7/(2-2)", emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { evaluateLabFormula("7%2", emptyMap()) }
    }

    @Test
    fun artifactCanRestorePreviousTextWithoutErasingVersionHistory() {
        val artifact = LabArtifact("原稿").revise("新版")
        assertEquals(listOf("原稿"), artifact.history)
        assertEquals(listOf("原稿", "新版"), artifact.revise(artifact.history.first()).history)
        assertEquals("原稿", artifact.revise(artifact.history.first()).content)
        assertEquals(artifact, artifact.revise("新版"))
    }

    @Test
    fun citationsKeepOnlyExactQuotesAndDiscardUnsupportedModelClaims() {
        val reply = """[
            {"claim":"這項已核准","source":"S2","quote":"要重新確認。"},
            {"source":"S1","quote":"下週三發表"},
            {"source":"S1","quote":"要重新確認。"},
            {"source":"S4","quote":"下週三發布。"},
            {"source":"S2","quote":" 要重新確認。"},
            {"source":"S2","quote":"要重新確認。"}
        ]"""
        assertEquals(listOf(GroundedEvidence("S2", "要重新確認。")),
            verifiedGroundedEvidence(reply, mapOf("S1" to "下週三發布。", "S2" to "這項需求要重新確認。")))
        assertTrue(verifiedGroundedEvidence("沒有找到", emptyMap()).isEmpty())
    }

    @Test
    fun excerptKeepsConditionsAndHighlightsExactText() {
        val text = "發布條件\n尚未核准正式上線。\n通過驗收後再決定。\n下一章"
        val quote = "核准正式上線"
        val excerpt = groundedExcerpt(text, quote)!!
        assertEquals("發布條件\n尚未核准正式上線。\n通過驗收後再決定。", excerpt.text)
        assertEquals(quote, excerpt.text.substring(excerpt.quoteOffset, excerpt.quoteOffset + quote.length))
        assertEquals(null, groundedExcerpt(text, "沒有這句"))
    }

    @Test
    fun referencesLocatePdfPagesSlidesMarkdownSectionsAndPlainTextLines() {
        val pdf = groundedDocumentPassages("manual.PDF", "---Page 1:\n第一頁\n---Page 2:\n第二頁\n\n[Truncated: showing 2 of 4 pages]")
        assertEquals(listOf("第 1 頁", "第 2 頁"), pdf.map { it.section })
        assertEquals("第 2 頁 · 第 4 行", pdf[1].location("第二頁"))
        assertEquals("第二頁", pdf[1].text.trim())
        val slides = groundedDocumentPassages("slides.pptx", "## Slide 4\n\n條件\n## Slide 5\n\n結果")
        assertEquals(listOf("第 4 張投影片", "第 5 張投影片"), slides.map { it.section })
        assertEquals("安裝 · 第 3 行", groundedDocumentPassages("README.md", "概覽\n# 安裝\n安裝步驟").last().location("安裝步驟"))
        assertEquals("第 2–3 行", groundedDocumentPassages("list.txt", "一\n二\n三").single().location("二\n三"))
    }

    @Test
    fun passagesOverlapAndPreserveUnicodeAtChunkBoundaries() {
        val text = "甲".repeat(1199) + "🐱" + "乙".repeat(1000)
        val passages = groundedDocumentPassages("example.txt", text)
        assertTrue(passages.any { "甲🐱乙" in it.text })
        assertTrue(passages.none { it.text.first().isLowSurrogate() || it.text.last().isHighSurrogate() })
    }

    @Test
    fun previewSetsOfflinePolicyBeforeUserMarkupAndEscapesRawElementClosers() {
        val page = isolatePreview("<main>你好</main>", "/* </style> */", "console.log('</script>');")
        assertTrue(page.indexOf("Content-Security-Policy") in 0 until page.indexOf("<main>"))
        for (directive in listOf("connect-src 'none'", "form-action 'none'", "frame-src 'none'", "object-src 'none'")) {
            assertTrue(page.contains(directive))
        }
        assertFalse(page.contains("/* </style> */"))
        assertFalse(page.contains("console.log('</script>');"))
    }
}
