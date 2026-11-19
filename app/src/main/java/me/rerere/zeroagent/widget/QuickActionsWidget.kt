package me.rerere.zeroagent.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import me.rerere.zeroagent.R
import me.rerere.zeroagent.RouteActivity
import kotlin.uuid.Uuid

/** No background model calls, cached secrets, or account data in the launcher process. */
class QuickActionsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)
            fun launch(button: Int, code: Int, configure: Intent.() -> Unit) {
                val intent = Intent(context, RouteActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    configure()
                }
                views.setOnClickPendingIntent(button, PendingIntent.getActivity(context, id * 10 + code,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            launch(R.id.widget_new_chat, 1) { putExtra("open_new_chat", true) }
            launch(R.id.widget_voice, 2) { putExtra("open_native_voice", true) }
            launch(R.id.widget_projects, 3) { putExtra("open_productivity", true) }
            launch(R.id.widget_visual, 4) { putExtra("open_visual_assist", true) }
            manager.updateAppWidget(id, views)
        }
    }
}
