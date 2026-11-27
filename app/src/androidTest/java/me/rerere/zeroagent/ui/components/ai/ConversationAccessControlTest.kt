package me.rerere.zeroagent.ui.components.ai

import android.graphics.Bitmap
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.ai.tools.ConversationAccess
import me.rerere.zeroagent.data.ai.tools.ConversationAccessMode
import me.rerere.zeroagent.data.ai.tools.TOOL_REVIEW_METADATA
import me.rerere.zeroagent.ui.components.message.ToolReviewSummary
import me.rerere.zeroagent.ui.components.message.ChatMessageToolStep
import me.rerere.zeroagent.ui.components.ui.ChainOfThought
import me.rerere.zeroagent.ui.designsystem.ZeroTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ConversationAccessControlTest {
    @Test fun assistedModeHidesReusableChatGrantsWhileKeeping48dpHumanApproval() {
        val allowChatScope = mutableStateOf(true)
        val scopes = mutableListOf<me.rerere.zeroagent.service.ChatService.ApprovalScope>()
        var density = 1f
        val tool = UIMessagePart.Tool("pending-call", "test_tool", "{}", approvalState = ToolApprovalState.Pending)
        compose.setContent {
            density = LocalDensity.current.density
            Theme { ChainOfThought(steps = listOf(tool), forceExpanded = true) {
                ChatMessageToolStep(tool = it, allowChatScope = allowChatScope.value,
                    onToolApproval = { _, _, _, scope, _ -> scopes += scope })
            } }
        }
        compose.onNodeWithText(text(R.string.tool_approval_review)).performClick()
        compose.onNodeWithText(text(R.string.access_chat_grant)).assertIsDisplayed()
        compose.runOnIdle { allowChatScope.value = false }
        compose.onNodeWithText(text(R.string.access_chat_grant)).assertDoesNotExist()
        val approve = compose.onNodeWithText(text(R.string.chat_message_tool_approve)).performScrollTo()
        val bounds = approve.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width >= 48f * density - 1f && bounds.height >= 48f * density - 1f)
        approve.performClick()
        compose.runOnIdle { assertEquals(listOf(me.rerere.zeroagent.service.ChatService.ApprovalScope.Once), scopes) }
    }

    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun text(id: Int) = instrumentation.targetContext.getString(id)

    @Composable
    private fun Theme(dark: Boolean = false, content: @Composable () -> Unit) {
        ZeroTheme.Provide(isDark = dark) {
            Surface(Modifier.fillMaxSize()) { content() }
        }
    }

    @Test fun currentModeIsSelectedAndAssistedCommitsOnlyItsOwnChoice() {
        val choices = mutableListOf<ConversationAccessMode>()
        compose.setContent { Theme { ConversationAccessPicker(ConversationAccessMode.ASK, "Current model", choices::add, {}) } }
        compose.onNodeWithTag("access_option_ASK").assertIsSelected()
        compose.onNodeWithTag("access_option_ASSISTED").assertIsNotSelected().performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(ConversationAccessMode.ASSISTED), choices) }
    }

    @Test fun selectingTheCurrentLevelDismissesWithoutAChange() {
        var dismissed = false
        var changed = false
        compose.setContent { Theme { ConversationAccessPicker(ConversationAccessMode.ASSISTED, "Model", { changed = true }, { dismissed = true }) } }
        compose.onNodeWithTag("access_option_ASSISTED").assertIsSelected().performScrollTo().performClick()
        compose.runOnIdle { assertTrue(dismissed); assertFalse(changed) }
    }

    @Test fun fullAccessRequiresTheExplicitRiskAcknowledgement() {
        val choices = mutableListOf<ConversationAccessMode>()
        compose.setContent { Theme { ConversationAccessPicker(ConversationAccessMode.ASK, "Model", choices::add, {}) } }
        compose.onNodeWithTag("access_option_FULL").performScrollTo().performClick()
        compose.onNodeWithTag("access_confirm_full").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(choices.isEmpty()) }
        compose.onNodeWithTag("access_accept_risk").performScrollTo().performClick()
        compose.onNodeWithTag("access_confirm_full").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(ConversationAccessMode.FULL), choices) }
    }

    @Test fun dismissingTheRiskDialogDoesNotGrantFullAccessAndResetsAcknowledgement() {
        var changed = false
        compose.setContent { Theme { ConversationAccessPicker(ConversationAccessMode.ASK, "Model", { changed = true }, {}) } }
        compose.onNodeWithTag("access_option_FULL").performScrollTo().performClick()
        compose.onNodeWithTag("access_accept_risk").performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        compose.onNodeWithTag("access_option_FULL").performScrollTo().performClick()
        compose.onNodeWithTag("access_confirm_full").assertIsNotEnabled()
        compose.runOnIdle { assertFalse(changed) }
    }

    @Test fun largeRtlTextKeepsEveryOptionReachableWith48dpTargets() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                Theme { ConversationAccessPicker(ConversationAccessMode.ASSISTED, "Very long model name / " .repeat(6), {}, {}) }
            }
        }
        ConversationAccessMode.entries.forEach { mode ->
            val node = compose.onNodeWithTag("access_option_${mode.name}")
            node.performScrollTo().assertIsDisplayed().assertHasClickAction()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width >= 48f * density)
            assertTrue(bounds.height >= 48f * density)
        }
    }

    @Test fun actualConversationControlUpdatesTheSessionModeAndButton() {
        val id = Uuid.random()
        try {
            compose.setContent { Theme { Column { ConversationAccessControl(id, modelName = "Current model") } } }
            compose.onNodeWithTag("access_control").performClick()
            compose.onNodeWithTag("access_option_ASSISTED").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(ConversationAccessMode.ASSISTED, ConversationAccess.mode(id)) }
            compose.onNodeWithTag("access_control").assertTextContains(text(R.string.zeroagent8_access_assisted))
            compose.onNodeWithTag("access_options").assertDoesNotExist()
        } finally { ConversationAccess.set(id, ConversationAccessMode.ASK) }
    }

    @Test fun reviewSummaryShowsAConcreteReasonAndLocalizesUnavailableChecks() {
        fun tool(status: String, reason: String) = UIMessagePart.Tool("review-$status", "read_file", "{}", metadata = buildJsonObject {
            put(TOOL_REVIEW_METADATA, buildJsonObject { put("status", status); put("reason", reason) })
        })
        compose.setContent { Theme { Column {
            ToolReviewSummary(tool("allow", "Read the requested README only."))
            ToolReviewSummary(tool("unavailable", ""))
        } } }
        compose.onNodeWithText(text(R.string.access_review_allowed)).assertIsDisplayed()
        compose.onNodeWithText("Read the requested README only.").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.access_review_unavailable)).assertIsDisplayed()
    }

    /** Real screenshots for the isolated emulator's window, locale and theme matrix. */
    @Test fun capturePermissionPanel() {
        val arguments = InstrumentationRegistry.getArguments()
        val name = arguments.getString("permissionScreenshot") ?: return
        require(name.matches(Regex("[a-zA-Z0-9_-]+")))
        val locale = arguments.getString("permissionLocale") ?: "zh-TW"
        val dark = arguments.getString("permissionDark") == "true"
        var expectedTitle = ""
        compose.setContent {
            val base = LocalContext.current
            val configuration = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList.forLanguageTags(locale)) }
            val localized = base.createConfigurationContext(configuration)
            expectedTitle = localized.getString(R.string.access_choose_title)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
                LocalResources provides localized.resources,
                LocalLayoutDirection provides if (locale == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Theme(dark) { ConversationAccessPicker(ConversationAccessMode.ASSISTED, "GPT-6.1 Sol", {}, {}) }
            }
        }
        compose.onNodeWithTag("access_option_ASSISTED").assertIsSelected()
        compose.onNodeWithText(expectedTitle).assertIsDisplayed()
        compose.waitForIdle()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "permission-review").apply { mkdirs() }
        // PixelCopy waits for the sheet window to render, unlike a global screenshot during entry.
        val image = compose.onNodeWithTag("access_options").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        image.recycle()
    }
}
