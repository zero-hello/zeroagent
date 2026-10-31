package me.rerere.zeroagent.ui.components.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardGroupTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun defaultTrailingControlMovesBelowDescriptionAtLargeFontSize() {
        compose.setContent {
            LargeFontSettings {
                CardGroup(Modifier.width(272.dp)) {
                    item(
                        headlineContent = { Text("Automatic conversation archive") },
                        supportingContent = {
                            Text("Choose when idle conversations move into the archive.", Modifier.testTag("description"))
                        },
                        trailingContent = {
                            TextButton(onClick = {}, modifier = Modifier.testTag("control")) { Text("Change") }
                        },
                    )
                }
            }
        }

        captureEvidence("stacked", "description", "control")
        // ListItem merges text semantics. Inspect its actual layout children for geometry checks.
        compose.onNodeWithTag("description", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("control", useUnmergedTree = true).assertIsDisplayed().assertIsEnabled()
        val description = compose.onNodeWithTag("description", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val control = compose.onNodeWithTag("control", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("The control must not take width away from the description", control.top >= description.bottom)
    }

    @Test
    fun decorativeTrailingIconCanKeepItsInlinePositionAtLargeFontSize() {
        compose.setContent {
            LargeFontSettings {
                CardGroup(Modifier.width(272.dp)) {
                    item(
                        stackTrailing = false,
                        headlineContent = { Text("Theme", Modifier.testTag("headline")) },
                        trailingContent = { Box(Modifier.size(24.dp).testTag("arrow")) },
                    )
                }
            }
        }

        captureEvidence("inline", "headline", "arrow")
        compose.onNodeWithTag("headline", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("arrow", useUnmergedTree = true).assertIsDisplayed()
        val headline = compose.onNodeWithTag("headline", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrow = compose.onNodeWithTag("arrow", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("An opted-out trailing icon stays to the right of the headline", arrow.left >= headline.right)
        assertTrue("The inline icon stays on the headline row", arrow.top < headline.bottom && arrow.bottom > headline.top)
    }

    @Composable
    private fun LargeFontSettings(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
            MaterialTheme(content = content)
        }
    }

    private fun captureEvidence(name: String, vararg tags: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val evidence = File(instrumentation.targetContext.externalCacheDir, "cardgroup-evidence").apply { mkdirs() }
        val report = buildString {
            appendLine("Merged tree:")
            appendLine(compose.onRoot().printToString())
            appendLine("Unmerged tree:")
            appendLine(compose.onRoot(useUnmergedTree = true).printToString())
            tags.forEach { tag ->
                val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
                appendLine("$tag clipped=${node.fetchSemanticsNode().boundsInRoot} unclipped=${node.getUnclippedBoundsInRoot()}")
            }
        }
        File(evidence, "$name.txt").writeText(report)
        report.lineSequence().forEach { Log.i("CardGroupEvidence", it) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(evidence, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
