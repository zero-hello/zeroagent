package me.rerere.zeroagent.service

import android.content.Context
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import me.rerere.zeroagent.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.zeroagent.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.zeroagent.RouteActivity
import me.rerere.zeroagent.utils.cancelNotification
import me.rerere.zeroagent.utils.sendNotification
import me.rerere.zeroagent.R
import me.rerere.zeroagent.data.model.Conversation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.uuid.Uuid

/** Notification presentation; conversation state stays in the service. */
internal class ChatNotifications(
    private val context: Context,
    private val snapshot: (Uuid) -> Conversation,
) {
    /**
     * Per-conversation notification id / PendingIntent request-code allocator.
     *
     * conversationId.hashCode() (the previous scheme) can collide across different
     * conversationIds, which would let one conversation's live-update notification refresh
     * clobber another's, or let a stale PendingIntent's FLAG_UPDATE_CURRENT silently repoint
     * at the wrong conversation. This registry hands out a distinct, stable sequence number
     * per conversationId instead: stable so repeated calls keep landing on the same
     * notification/PendingIntent (update-in-place, no stacking), collision-free since two
     * different conversationIds can never share a slot.
     */
    private val conversationNotificationSequence = ConcurrentHashMap<Uuid, Int>()
    private val nextConversationNotificationSequence = AtomicInteger(0)

    private fun notificationSequenceFor(conversationId: Uuid): Int =
        conversationNotificationSequence.computeIfAbsent(conversationId) {
            nextConversationNotificationSequence.incrementAndGet()
        }

    // ---- 通知 ----

    fun sendGenerationDoneNotification(conversationId: Uuid, senderName: String) {
        // 先取消 Live Update 通知
        cancelLiveUpdateNotification(conversationId)

        val conversation = snapshot(conversationId)
        context.sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 1
        ) {
            title = senderName
            content = conversation.currentMessages.lastOrNull()?.toText()?.take(50)?.trim() ?: ""
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = getPendingIntent(context, conversationId)
        }
    }

    fun sendToolApprovalRequiredNotification(
        conversationId: Uuid,
        senderName: String,
        pendingTool: UIMessagePart.Tool,
    ) {
        val toolName = pendingTool.toolName
            .removePrefix("mcp__")
            .substringAfter("__", missingDelimiterValue = pendingTool.toolName.removePrefix("mcp__"))
        context.sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 1,
        ) {
            title = senderName
            content = "${context.getString(R.string.setting_mcp_page_needs_approval)}: $toolName"
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = getPendingIntent(context, conversationId)
        }
    }

    private fun getLiveUpdateNotificationId(conversationId: Uuid): Int {
        // +10000 keeps this space clear of the other fixed notification ids this app uses
        // (generation-done = 1; WebServerService FGS = 2001; MediaPlaybackService FGS = 7001;
        // foreground services use ids below 10000).
        return notificationSequenceFor(conversationId) + 10000
    }

    fun sendLiveUpdateNotification(
        conversationId: Uuid,
        messages: List<UIMessage>,
        senderName: String
    ) {
        val lastMessage = messages.lastOrNull() ?: return
        val parts = lastMessage.parts

        // 确定当前状态
        val (chipText, statusText, contentText) = determineNotificationContent(parts)

        context.sendNotification(
            channelId = CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            notificationId = getLiveUpdateNotificationId(conversationId)
        ) {
            title = senderName
            content = contentText
            subText = statusText
            ongoing = true
            onlyAlertOnce = true
            category = NotificationCompat.CATEGORY_PROGRESS
            useBigTextStyle = true
            contentIntent = getPendingIntent(context, conversationId)
            requestPromotedOngoing = true
            shortCriticalText = chipText
        }
    }

    private fun determineNotificationContent(parts: List<UIMessagePart>): Triple<String, String, String> {
        // 检查最近的 part 来确定状态
        val lastReasoning = parts.filterIsInstance<UIMessagePart.Reasoning>().lastOrNull()
        val lastTool = parts.filterIsInstance<UIMessagePart.Tool>().lastOrNull()
        val lastText = parts.filterIsInstance<UIMessagePart.Text>().lastOrNull()

        return when {
            // 正在执行工具
            lastTool != null && !lastTool.isExecuted -> {
                // MCP tools are exposed as `mcp__<serverSlug>_<serverName>__<toolName>`; strip
                // both the prefix and the server segment so the notification shows the bare tool
                // name. Non-MCP tool names (no `mcp__` prefix) fall through unchanged via the
                // missingDelimiterValue, instead of being truncated at an embedded `__`.
                val toolName = lastTool.toolName
                    .removePrefix("mcp__")
                    .substringAfter("__", missingDelimiterValue = lastTool.toolName.removePrefix("mcp__"))
                Triple(
                    context.getString(R.string.notification_live_update_chip_tool),
                    context.getString(R.string.notification_live_update_tool, toolName),
                    lastTool.input.take(100)
                )
            }
            // 正在思考（Reasoning 未结束）
            lastReasoning != null && lastReasoning.finishedAt == null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_thinking),
                    context.getString(R.string.notification_live_update_thinking),
                    lastReasoning.reasoning.takeLast(200)
                )
            }
            // 正在写回复
            lastText != null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_writing),
                    lastText.text.takeLast(200)
                )
            }
            // 默认状态
            else -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_title),
                    ""
                )
            }
        }
    }

    fun cancelLiveUpdateNotification(conversationId: Uuid) {
        context.cancelNotification(getLiveUpdateNotificationId(conversationId))
    }

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            // +1_000_000 keeps this request-code space clear of NotificationTool.kt's own
            // per-conversation sequence (also small ints starting at 1): both target the same
            // RouteActivity with no action/data/categories set, so those intents are
            // Intent.filterEquals-equal aside from extras, meaning the request code is the only
            // thing that tells two PendingIntents apart. Sharing a small-int range would risk a
            // notification-tool PendingIntent for one conversation overwriting a chat
            // PendingIntent for a different one via FLAG_UPDATE_CURRENT.
            notificationSequenceFor(conversationId) + 1_000_000,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

}
