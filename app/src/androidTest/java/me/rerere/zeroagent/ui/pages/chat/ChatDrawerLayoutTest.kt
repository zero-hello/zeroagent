package me.rerere.zeroagent.ui.pages.chat

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatDrawerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val bigScreen = mutableStateOf(false)
    private lateinit var drawerState: DrawerState
    private var contentBackPresses = 0

    @Test
    fun modalDrawerOpensAndBackClosesItWithoutLeavingChat() {
        showChat()
        compose.onNodeWithText("Conversation list").assertIsNotDisplayed()
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("Conversation list").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Conversation list").assertIsNotDisplayed()
        compose.onNodeWithText("Chat content").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, contentBackPresses) }
    }

    @Test
    fun permanentDrawerShowsTheSameChatAndConversationList() {
        bigScreen.value = true
        showChat()
        compose.onNodeWithText("Conversation list").assertIsDisplayed()
        compose.onNodeWithText("Chat content").assertIsDisplayed()
    }

    @Test
    fun switchingToPermanentClosesModalStateBeforeReturningToPhone() {
        showChat()
        compose.onNodeWithText("Open drawer").performClick()
        compose.onNodeWithText("Conversation list").assertIsDisplayed()
        compose.runOnIdle { bigScreen.value = true }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(drawerState.isOpen) }
        compose.onNodeWithText("Conversation list").assertIsDisplayed()
        compose.runOnIdle { bigScreen.value = false }
        compose.onNodeWithText("Conversation list").assertIsNotDisplayed()
        compose.onNodeWithText("Chat content").assertIsDisplayed()
    }

    private fun showChat() {
        compose.setContent {
            MaterialTheme {
                drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                ChatDrawerLayout(
                    isBigScreen = bigScreen.value,
                    drawerState = drawerState,
                    drawerContent = { Text("Conversation list", Modifier.width(120.dp)) },
                ) {
                    BackHandler { contentBackPresses++ }
                    Column {
                        Text("Chat content")
                        Button(onClick = { scope.launch { drawerState.open() } }) {
                            Text("Open drawer")
                        }
                    }
                }
            }
        }
    }
}
