package me.rerere.zeroagent.data.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class GenerationPlatformEffectsTest {
    @After fun reset() {
        AgentTurnTracker.reset()
        AutomationRecorder.reset()
        ScreenshotLedger.clear()
    }

    @Test fun `completion hooks preserve order and failure identity including cancellation`() = runBlocking {
        for (failure in listOf(null, IllegalStateException("failed"), CancellationException("cancelled"))) {
            val events = mutableListOf<String>()
            val owner = Any()
            val effects = object : GenerationPlatformEffects {
                override fun onTurnStart() { events += "start" }
                override fun onToolExecution(name: String, owner: Any, onPause: () -> Unit) = Unit
                override fun onTurnComplete(owner: Any) {
                    assertSame(owner, ownerForAssertion)
                    events += "complete"
                }
                val ownerForAssertion = owner
            }
            var caught: Throwable? = null
            try {
                flow {
                    events += "body"
                    emit(1)
                    if (failure != null) throw failure
                }.withPlatformEffects(effects, owner).collect { events += "value:$it" }
            } catch (error: Throwable) { caught = error }
            assertSame(failure, caught)
            assertEquals(listOf("start", "body", "value:1", "complete"), events)
        }
    }

    @Test fun `return policy preserves foreground safety and unknown information`() {
        fun decide(navigated: Boolean = true, automated: Boolean = true, destination: String? = "browser", foreground: String? = "browser") =
            autoReturnDecision(navigated, automated, destination, foreground, "self")
        assertEquals(AutoReturnDecision.NONE, decide(navigated = false))
        assertEquals(AutoReturnDecision.NONE, decide(automated = false))
        assertEquals(AutoReturnDecision.RETURN, decide())
        assertEquals(AutoReturnDecision.RETURN, decide(foreground = "self"))
        assertEquals(AutoReturnDecision.RETURN, decide(foreground = null))
        assertEquals(AutoReturnDecision.RETURN, decide(destination = null, foreground = "other"))
        assertEquals(AutoReturnDecision.USER_SWITCHED, decide(foreground = "other"))
    }

    @Test fun `cancelling the owning coroutine still completes effects once`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val owner = Any()
        val events = mutableListOf<String>()
        val effects = object : GenerationPlatformEffects {
            override fun onTurnStart() { events += "start" }
            override fun onToolExecution(name: String, owner: Any, onPause: () -> Unit) = Unit
            override fun onTurnComplete(owner: Any) { events += "complete" }
        }
        val job = launch {
            flow<Unit> { started.complete(Unit); awaitCancellation() }
                .withPlatformEffects(effects, owner).collect()
        }
        started.await()
        job.cancel()
        job.join()
        assertEquals(listOf("start", "complete"), events)
    }

    @Test fun `launch failure is reported and cleanup continues but JVM errors propagate`() {
        val failure = SecurityException("launch denied")
        val host = FakeHost().apply { launchFailure = failure }
        val effects = AndroidGenerationPlatformEffects(host)
        effects.onTurnStart()
        AgentTurnTracker.recordNavigatedAway("browser")
        AgentTurnTracker.recordAutomationAction()
        ScreenshotLedger.recordTransient { host.events += "delete"; true }
        effects.onTurnComplete(Any())
        assertSame(failure, host.reportedFailure)
        assertEquals(listOf("hide", "foreground", "launch", "reported", "delete"), host.events)
        val fatal = AssertionError("fatal")
        try {
            launchAutoReturn({ throw fatal }) { fail("must not swallow JVM errors") }
            fail("must propagate")
        } catch (caught: AssertionError) { assertSame(fatal, caught) }
    }

    @Test fun `missing foreground still returns home`() {
        val host = FakeHost().apply { foreground = null }
        val effects = AndroidGenerationPlatformEffects(host)
        effects.onTurnStart()
        AgentTurnTracker.recordNavigatedAway("browser")
        AgentTurnTracker.recordAutomationAction()
        effects.onTurnComplete(Any())
        assertEquals(listOf("hide", "foreground", "launch"), host.events)
    }

    @Test fun `start resets turn observations and phone overlay keeps cancellation owner`() {
        AgentTurnTracker.recordNavigatedAway("old")
        AgentTurnTracker.recordAutomationAction()
        AutomationRecorder.recordEntry("old", "launch_app")
        val host = FakeHost()
        val effects = AndroidGenerationPlatformEffects(host)
        effects.onTurnStart()
        assertFalse(AgentTurnTracker.didNavigateAway())
        assertFalse(AgentTurnTracker.didAutomate())
        assertTrue(AutomationRecorder.drain().isEmpty())
        val job = Job()
        var paused = 0
        val owner = AgentRunControl { assertFalse(job.isCancelled); paused++ }.also { it.bind(job) }
        effects.onToolExecution("memory_read", owner, owner::pause)
        assertTrue(host.events.isEmpty())
        effects.onToolExecution("tap", owner, owner::pause)
        assertSame(owner, host.owner)
        host.pause!!()
        host.pause!!()
        assertEquals(1, paused)
        assertTrue(job.isCancelled)
        effects.onTurnComplete(owner)
        assertSame(owner, host.owner)
        assertEquals(listOf("show:tap", "hide"), host.events)
    }

    @Test fun `completion hides before foreground lookup return and screenshot purge`() {
        val host = FakeHost()
        val effects = AndroidGenerationPlatformEffects(host)
        effects.onTurnStart()
        AgentTurnTracker.recordNavigatedAway("browser")
        AgentTurnTracker.recordAutomationAction()
        ScreenshotLedger.recordTransient { host.events += "delete"; true }
        effects.onTurnComplete(Any())
        assertEquals(listOf("hide", "foreground", "launch", "delete"), host.events)
        assertEquals(0, ScreenshotLedger.recordedCount())
    }

    @Test fun `user switching apps skips return and still clears transient screenshots`() {
        val host = FakeHost().apply { foreground = "other" }
        val effects = AndroidGenerationPlatformEffects(host)
        effects.onTurnStart()
        AgentTurnTracker.recordNavigatedAway("browser")
        AgentTurnTracker.recordAutomationAction()
        ScreenshotLedger.recordTransient { host.events += "delete"; true }
        effects.onTurnComplete(Any())
        assertEquals(listOf("hide", "foreground", "switched", "delete"), host.events)
    }

    @Test fun `navigation without automation keeps screenshots and does not query foreground`() {
        val host = FakeHost()
        val effects = AndroidGenerationPlatformEffects(host)
        effects.onTurnStart()
        AgentTurnTracker.recordNavigatedAway("browser")
        ScreenshotLedger.recordTransient { fail("must retain screenshot"); false }
        effects.onTurnComplete(Any())
        assertEquals(listOf("hide"), host.events)
        assertEquals(0, ScreenshotLedger.recordedCount())
    }

    private class FakeHost : GenerationPlatformHost {
        override val packageName = "self"
        val events = mutableListOf<String>()
        var foreground: String? = "browser"
        var owner: Any? = null
        var pause: (() -> Unit)? = null
        var launchFailure: Exception? = null
        var reportedFailure: Exception? = null
        override fun foregroundPackage(): String? { events += "foreground"; return foreground }
        override fun showTool(name: String, owner: Any, onPause: () -> Unit) {
            events += "show:$name"; this.owner = owner; pause = onPause
        }
        override fun hide(owner: Any) { events += "hide"; this.owner = owner }
        override fun notifyUserSwitched() { events += "switched" }
        override fun launchApp() {
            launchAutoReturn(
                { events += "launch"; launchFailure?.let { throw it } },
                { reportedFailure = it; events += "reported" },
            )
        }
    }
}
