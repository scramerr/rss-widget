package com.byterdevs.rsswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.util.Log
import androidx.core.net.toUri
import com.byterdevs.rsswidget.ThemeUtils.setBgTransparency
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

class RssWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("RssWidgetProvider", "onReceive triggered with action: ${intent.action}")

        if (intent.action == "com.byterdevs.rsswidget.ACTION_REFRESH") {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            Log.d("RssWidgetProvider", "appWidgetId: $appWidgetId")
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val transparency = RssWidgetConfigureActivity.loadTransparencyPref(context, appWidgetId)
                val views = setBgTransparency(context, RemoteViews(context.packageName, R.layout.widget_rss_loading), R.id.widget_rss_loading, transparency)
                appWidgetManager.updateAppWidget(appWidgetId, views)
                onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
            }
        }
    }

    companion object {
        // Add this function to update the widget with the selected URL
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val url = RssWidgetConfigureActivity.loadRssUrlPref(context, appWidgetId)
            val customTitle = RssWidgetConfigureActivity.loadTitlePref(context, appWidgetId)
            val maxItems = RssWidgetConfigureActivity.loadMaxItemsPref(context, appWidgetId)
            val showDescription = RssWidgetConfigureActivity.loadDescriptionPref(context, appWidgetId)
            val descriptionLen = RssWidgetConfigureActivity.loadDescriptionLenPref(context, appWidgetId)
            val transparency = RssWidgetConfigureActivity.loadTransparencyPref(context, appWidgetId)
            val showSource = RssWidgetConfigureActivity.loadShowSourcePref(context, appWidgetId)
            val dateFormat = RssWidgetConfigureActivity.loadDateFormatPref(context, appWidgetId)
            val dimReadItems = RssWidgetConfigureActivity.loadDimReadItemsPref(context, appWidgetId)

            val views = setBgTransparency(context, RemoteViews(context.packageName, R.layout.widget_rss), R.id.widget_rss, transparency)

            val intent = Intent(context, RssRemoteViewsService::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            intent.putExtra("EXTRA_URL", url)
            intent.putExtra("EXTRA_MAX_ITEMS", maxItems)
            intent.putExtra("EXTRA_SHOW_DESCRIPTION", showDescription)
            intent.putExtra("EXTRA_DESCRIPTION_LENGTH", descriptionLen)
            intent.putExtra("EXTRA_TITLE", customTitle)
            intent.putExtra("EXTRA_TRANSPARENCY", transparency)
            intent.putExtra("EXTRA_SHOW_SOURCE", showSource)
            intent.putExtra("EXTRA_DATE_FORMAT", dateFormat)
            intent.putExtra("EXTRA_DIM_READ_ITEMS", dimReadItems)
            intent.data = intent.toUri(Intent.URI_INTENT_SCHEME).toUri()
            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.empty_text)

            // Set up click and refresh intents
            val clickIntent = Intent(context, BrowserLauncherActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, pendingIntent)

            val refreshIntent = Intent(context, RssWidgetProvider::class.java).apply {
                action = "com.byterdevs.rsswidget.ACTION_REFRESH"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_refresh, refreshPendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)

            enqueuePeriodicUpdate(context, appWidgetId)
        }

        private fun enqueuePeriodicUpdate(context: Context, appWidgetId: Int) {
            val updateInterval = RssWidgetConfigureActivity.loadUpdateIntervalPref(context, appWidgetId)
            if (updateInterval == 0) {
                return
            }

            val workRequest = PeriodicWorkRequestBuilder<RssWidgetUpdateWorker>(updateInterval.toLong(), TimeUnit.MINUTES)
                .addTag("rss_widget_update_$appWidgetId")
                .setInputData(androidx.work.Data.Builder().putInt("appWidgetId", appWidgetId).build())
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "rss_widget_update_$appWidgetId",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

}
