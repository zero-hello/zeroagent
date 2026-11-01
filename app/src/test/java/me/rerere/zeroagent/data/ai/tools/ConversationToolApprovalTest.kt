package me.rerere.zeroagent.data.ai.tools

import kotlinx.coroutines.runBlocking
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/** Exercises the same approval gate used for initial calls, resumed siblings and execution. */
class ConversationToolApprovalTest {
    @Test
    fun `a final eligibility lookup cannot publish an approval after a mode change`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var lookups = 0
            val gate = gate(id, canReview = {
                lookups++
                if (lookups == 2) ConversationAccess.set(id, ConversationAccessMode.ASK)
                true
            })
            assertEquals(ToolApprovalState.Pending, gate.resolve(tool()).approvalState)
        }
    }

    @Test
    fun `a revoked review cannot still be displayed as approved while waiting for a human`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            val gate = gate(id)
            val reviewed = gate.resolve(tool())
            ConversationAccess.set(id, ConversationAccessMode.ASK)
            val revoked = gate.recheck(reviewed)
            assertEquals(ToolApprovalState.Pending, revoked.approvalState)
            assertTrue(revoked.metadata?.get(TOOL_REVIEW_METADATA).toString().contains("changed"))
        }
    }

    @Test
    fun `full access does not display a persisted review as a new independent check`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            val gate = gate(id, isToolAutoApproved = { true })
            val reviewed = gate.resolve(tool())
            ConversationAccess.set(id, ConversationAccessMode.FULL, highRiskConfirmed = true)
            val fullAccess = gate.recheck(reviewed)
            assertEquals(ToolApprovalState.Auto, fullAccess.approvalState)
            assertFalse(TOOL_REVIEW_METADATA in fullAccess.metadata.orEmpty())
        }
    }

    private fun tool(
        id: String = "call-1",
        name: String = "get_battery_info",
        input: String = "{}",
        state: ToolApprovalState = ToolApprovalState.Auto,
    ) = UIMessagePart.Tool(toolCallId = id, toolName = name, input = input, approvalState = state)

    private fun gate(
        id: Uuid?,
        needsApproval: (UIMessagePart.Tool) -> Boolean = { false },
        isToolAutoApproved: suspend (String) -> Boolean = { false },
        canReview: suspend (String) -> Boolean = { true },
        review: suspend (UIMessagePart.Tool) -> ToolReviewResult = { ToolReviewResult(true, "Requested action.") },
    ) = ConversationToolApproval(id, needsApproval, isToolAutoApproved, canReview, review)

    private suspend fun inMode(mode: ConversationAccessMode, block: suspend (Uuid) -> Unit) {
        val id = Uuid.random()
        try {
            assertTrue(ConversationAccess.set(id, mode, highRiskConfirmed = true))
            block(id)
        } finally {
            ConversationAccess.set(id, ConversationAccessMode.ASK)
            ToolApprovalAllowList.clearChat(id)
        }
    }

    @Test
    fun `assisted reviews an ordinary tool even when its definition needs no approval`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, review = { reviews++; ToolReviewResult(true, "Reads requested state.") })
            assertEquals(ToolApprovalState.Auto, gate.resolve(tool()).approvalState)
            assertEquals(1, reviews)
        }
    }

    @Test
    fun `reviewer ask leaves the proposed call pending for human approval`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            val gate = gate(id, review = { ToolReviewResult(false, "The requested write needs confirmation.") })
            assertEquals(ToolApprovalState.Pending, gate.resolve(tool(name = "write_text_file")).approvalState)
        }
    }

    @Test
    fun `unavailable reviewer never authorizes the proposed call`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            val gate = gate(id, review = { ToolReviewResult(false, "") })
            assertEquals(ToolApprovalState.Pending, gate.resolve(tool()).approvalState)
        }
    }

    @Test
    fun `a blacklist denial cannot be bypassed by static approval or by a reviewer`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, isToolAutoApproved = { true }, canReview = { false }, review = {
                reviews++
                ToolReviewResult(true, "Approved.")
            })
            assertEquals(ToolApprovalState.Pending, gate.resolve(tool()).approvalState)
            assertEquals(0, reviews)
        }
    }

    @Test
    fun `assisted never accepts a static approval instead of an independent review`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, isToolAutoApproved = { true }, review = {
                reviews++
                ToolReviewResult(false, "Needs human confirmation.")
            })
            assertEquals(ToolApprovalState.Pending, gate.resolve(tool()).approvalState)
            assertEquals(1, reviews)
        }
    }

    @Test
    fun `root hard floor and opaque MCP calls never reach the reviewer`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, needsApproval = { true }, isToolAutoApproved = { true }, review = {
                reviews++
                ToolReviewResult(true, "Approved.")
            })
            for (name in ToolApprovalDefaults.NO_ALWAYS_ALLOW + "mcp__server__arbitrary") {
                assertEquals(name, ToolApprovalState.Pending, gate.resolve(tool(name = name)).approvalState)
            }
            assertEquals(0, reviews)
        }
    }

    @Test
    fun `ask user remains available to its interactive executor rather than being reviewed`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, review = { reviews++; ToolReviewResult(true, "Approved.") })
            assertEquals(ToolApprovalState.Auto, gate.resolve(tool(name = "ask_user")).approvalState)
            assertEquals(0, reviews)
        }
    }

    @Test
    fun `ask mode uses its human approval channel without delegating to the reviewer`() = runBlocking {
        inMode(ConversationAccessMode.ASK) { id ->
            var reviews = 0
            val gate = gate(id, review = { reviews++; ToolReviewResult(true, "Approved.") })
            assertEquals(ToolApprovalState.Pending, gate.resolve(tool()).approvalState)
            assertEquals(0, reviews)
        }
    }

    @Test
    fun `full access retains automatic execution without independent review`() = runBlocking {
        inMode(ConversationAccessMode.FULL) { id ->
            var reviews = 0
            val gate = gate(id, isToolAutoApproved = { true }, review = {
                reviews++
                ToolReviewResult(false, "Unused reviewer.")
            })
            assertEquals(ToolApprovalState.Auto, gate.resolve(tool()).approvalState)
            assertEquals(0, reviews)
        }
    }

    @Test
    fun `explicit human decisions and already executed tools remain unchanged`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, review = { reviews++; ToolReviewResult(true, "Approved.") })
            listOf(
                tool(state = ToolApprovalState.Approved),
                tool(state = ToolApprovalState.Denied("No")),
                tool(state = ToolApprovalState.Answered("yes")),
                tool(state = ToolApprovalState.Pending),
                tool().copy(output = listOf(UIMessagePart.Text("already executed"))),
            ).forEach { original ->
                assertSame(original, gate.resolve(original))
                assertSame(original, gate.recheck(original))
            }
            assertEquals(0, reviews)
        }
    }

    @Test
    fun `one exact call can reuse its review receipt immediately before execution`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, review = { reviews++; ToolReviewResult(true, "Requested action.") })
            val resolved = gate.resolve(tool())
            assertEquals(ToolApprovalState.Auto, gate.recheck(resolved).approvalState)
            assertEquals(1, reviews)
        }
    }

    @Test
    fun `changed arguments invalidate an earlier review receipt`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, review = {
                reviews++
                ToolReviewResult(reviews == 1, "Only the first payload was authorized.")
            })
            gate.resolve(tool(name = "read_file", input = """{"path":"/project/README.md"}"""))
            val changed = tool(name = "read_file", input = """{"path":"/private/secrets.txt"}""")
            assertEquals(ToolApprovalState.Pending, gate.recheck(changed).approvalState)
            assertEquals(2, reviews)
        }
    }

    @Test
    fun `a different call ID cannot reuse another calls receipt`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, review = {
                reviews++
                ToolReviewResult(reviews == 1, "Review each proposed call independently.")
            })
            gate.resolve(tool(id = "first"))
            assertEquals(ToolApprovalState.Pending, gate.recheck(tool(id = "second")).approvalState)
            assertEquals(2, reviews)
        }
    }

    @Test
    fun `a different tool name cannot reuse another tools receipt`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, review = {
                reviews++
                ToolReviewResult(reviews == 1, "Only the first tool was authorized.")
            })
            gate.resolve(tool(name = "get_battery_info"))
            assertEquals(ToolApprovalState.Pending, gate.recheck(tool(name = "write_text_file")).approvalState)
            assertEquals(2, reviews)
        }
    }

    @Test
    fun `resume with a new gate independently reviews an unexecuted auto sibling`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val reviewer: suspend (UIMessagePart.Tool) -> ToolReviewResult = {
                reviews++
                ToolReviewResult(reviews == 1, "Review must be fresh after resume.")
            }
            val original = tool()
            val initiallyResolved = gate(id, review = reviewer).resolve(original)
            assertEquals(ToolApprovalState.Auto, initiallyResolved.approvalState)
            assertEquals(ToolApprovalState.Pending, gate(id, review = reviewer).resolve(initiallyResolved).approvalState)
            assertEquals(2, reviews)
        }
    }

    @Test
    fun `review receipts cannot cross conversations even when the tool payload is identical`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { first ->
            inMode(ConversationAccessMode.ASSISTED) { second ->
                val resolved = gate(first).resolve(tool())
                var secondReviews = 0
                val secondGate = gate(second, review = {
                    secondReviews++
                    ToolReviewResult(false, "This conversation did not authorize the call.")
                })
                assertEquals(ToolApprovalState.Pending, secondGate.recheck(resolved).approvalState)
                assertEquals(1, secondReviews)
            }
        }
    }

    @Test
    fun `downgrading full access to assisted requires review of an earlier auto sibling`() = runBlocking {
        inMode(ConversationAccessMode.FULL) { id ->
            var reviews = 0
            val gate = gate(id, isToolAutoApproved = { true }, review = {
                reviews++
                ToolReviewResult(false, "The user changed the authorization mode.")
            })
            val resolved = gate.resolve(tool())
            assertEquals(ToolApprovalState.Auto, resolved.approvalState)
            ConversationAccess.set(id, ConversationAccessMode.ASSISTED)
            assertEquals(ToolApprovalState.Pending, gate.recheck(resolved).approvalState)
            assertEquals(1, reviews)
        }
    }

    @Test
    fun `switching away and back invalidates a receipt even when the final mode is assisted`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var reviews = 0
            val gate = gate(id, review = {
                reviews++
                ToolReviewResult(reviews == 1, "The old access epoch is no longer authorized.")
            })
            val resolved = gate.resolve(tool())
            ConversationAccess.set(id, ConversationAccessMode.ASK)
            ConversationAccess.set(id, ConversationAccessMode.ASSISTED)
            assertEquals(ToolApprovalState.Pending, gate.recheck(resolved).approvalState)
            assertEquals(2, reviews)
        }
    }

    @Test
    fun `a blacklist change invalidates an existing receipt immediately before execution`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var eligible = true
            var reviews = 0
            val gate = gate(id, canReview = { eligible }, review = {
                reviews++
                ToolReviewResult(true, "Requested action.")
            })
            val resolved = gate.resolve(tool())
            eligible = false
            assertEquals(ToolApprovalState.Pending, gate.recheck(resolved).approvalState)
            assertEquals(1, reviews)
        }
    }

    @Test
    fun `a mode change during review prevents its stale allow verdict from executing`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            val gate = gate(id, review = {
                ConversationAccess.set(id, ConversationAccessMode.ASK)
                ToolReviewResult(true, "The late verdict must not override the users change.")
            })
            assertEquals(ToolApprovalState.Pending, gate.resolve(tool()).approvalState)
        }
    }

    @Test
    fun `a mode change during static approval lookup invalidates its late full access result`() = runBlocking {
        inMode(ConversationAccessMode.FULL) { id ->
            val gate = gate(id, isToolAutoApproved = {
                ConversationAccess.set(id, ConversationAccessMode.ASK)
                true
            })

            assertEquals(ToolApprovalState.Pending, gate.resolve(tool()).approvalState)
            assertEquals(ConversationAccessMode.ASK, ConversationAccess.mode(id))
        }
    }

    @Test
    fun `a mode change during receipt eligibility lookup invalidates execution of the old receipt`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            var eligibilityLookups = 0
            var reviews = 0
            val gate = gate(id, canReview = {
                eligibilityLookups++
                if (eligibilityLookups == 3) ConversationAccess.set(id, ConversationAccessMode.ASK)
                true
            }, review = {
                reviews++
                ToolReviewResult(true, "The initial action was authorized.")
            })
            val resolved = gate.resolve(tool())
            assertEquals(ToolApprovalState.Auto, resolved.approvalState)

            assertEquals(ToolApprovalState.Pending, gate.recheck(resolved).approvalState)
            assertEquals(ConversationAccessMode.ASK, ConversationAccess.mode(id))
            assertEquals(1, reviews)
        }
    }

    @Test
    fun `hardline commands are denied before static approval or independent review`() = runBlocking {
        inMode(ConversationAccessMode.FULL) { id ->
            var reviews = 0
            val gate = gate(id, isToolAutoApproved = { true }, review = {
                reviews++
                ToolReviewResult(true, "Must not be reached.")
            })
            val blocked = tool(name = "termux_run_command", input = """{"command":"rm -rf /"}""")
            assertTrue(gate.resolve(blocked).approvalState is ToolApprovalState.Denied)
            assertEquals(0, reviews)
        }
    }

    @Test
    fun `mode snapshots change on a round trip even when the final mode matches`() = runBlocking {
        inMode(ConversationAccessMode.ASK) { id ->
            val before = ConversationAccess.snapshot(id)
            ConversationAccess.set(id, ConversationAccessMode.ASSISTED)
            ConversationAccess.set(id, ConversationAccessMode.ASK)
            val after = ConversationAccess.snapshot(id)
            assertEquals(before.mode, after.mode)
            assertTrue(after.revision > before.revision)
        }
    }

    @Test
    fun `a delayed chat grant cannot be restored after a mode round trip`() = runBlocking {
        inMode(ConversationAccessMode.ASK) { id ->
            val priorApproval = ConversationAccess.snapshot(id)
            ConversationAccess.set(id, ConversationAccessMode.ASSISTED)
            ConversationAccess.set(id, ConversationAccessMode.ASK)

            ConversationAccess.grantIfUnchanged(id, priorApproval, "tap")

            assertFalse(ToolApprovalAllowList.isAllowedForChat(id, "tap"))
        }
    }

    @Test
    fun `a current assisted snapshot still cannot create a reusable chat grant`() = runBlocking {
        inMode(ConversationAccessMode.ASSISTED) { id ->
            ConversationAccess.grantIfUnchanged(id, ConversationAccess.snapshot(id), "tap")
            assertFalse(ToolApprovalAllowList.isAllowedForChat(id, "tap"))
        }
    }

    @Test
    fun `a current ask snapshot can retain an explicit human chat grant`() = runBlocking {
        inMode(ConversationAccessMode.ASK) { id ->
            ConversationAccess.grantIfUnchanged(id, ConversationAccess.snapshot(id), "tap")
            assertTrue(ToolApprovalAllowList.isAllowedForChat(id, "tap"))
        }
    }

    @Test
    fun `reselecting a mode preserves its revision and an active explicit approval`() = runBlocking {
        inMode(ConversationAccessMode.ASK) { id ->
            val before = ConversationAccess.snapshot(id)
            ConversationAccess.set(id, ConversationAccessMode.ASK)
            assertEquals(before, ConversationAccess.snapshot(id))
            ConversationAccess.grantIfUnchanged(id, before, "tap")
            assertTrue(ToolApprovalAllowList.isAllowedForChat(id, "tap"))
        }
    }
}
