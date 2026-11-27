package me.rerere.zeroagent.ui.pages.chat

import androidx.activity.ComponentActivity
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationDrawerTransitionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun phoneKeepsNavigationHostMountedAcrossDrawerChanges() = verifyStableHost(isBigScreen = false)

    @Test
    fun tabletKeepsNavigationHostMountedAcrossDrawerChanges() = verifyStableHost(isBigScreen = true)

    private fun verifyStableHost(isBigScreen: Boolean) {
        val drawerEnabled = mutableStateOf(true)
        var contentMounts = 0
        compose.setContent {
            MaterialTheme {
                ChatDrawerLayout(
                    isBigScreen = isBigScreen,
                    drawerState = rememberDrawerState(DrawerValue.Closed),
                    drawerWidth = 120.dp,
                    enabled = drawerEnabled.value,
                    drawerContent = { Text("Conversation list") },
                ) {
                    DisposableEffect(Unit) {
                        contentMounts++
                        onDispose { }
                    }
                    Text("Navigation content")
                }
            }
        }

        compose.runOnIdle { drawerEnabled.value = false }
        compose.onNodeWithText("Navigation content").assertIsDisplayed()
        compose.runOnIdle { drawerEnabled.value = true }
        compose.onNodeWithText("Navigation content").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, contentMounts) }
    }
}
