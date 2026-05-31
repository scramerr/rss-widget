package com.byterdevs.rsswidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.byterdevs.rsswidget.ThemeUtils.getThemedContextForWidget
import com.byterdevs.rsswidget.ThemeUtils.getColorResCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class RssWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == "com.byterdevs.rsswidget.ACTION_REFRESH") {

            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {

                val workRequest = OneTimeWorkRequestBuilder<RssWidgetUpdateWorker>()
                    .setInputData(
                        Data.Builder()
                            .putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            .putBoolean("hardRefresh", true)
                            .build()
                    )
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
            }
        } else if (intent.action == "com.byterdevs.rsswidget.ACTION_THEME_TOGGLE") {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val prefs = context.getWidgetPrefs(appWidgetId)
                val newTheme = when (prefs.themeMode) {
                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                    ThemeMode.LIGHT -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.SYSTEM
                }
                context.setWidgetPrefs(appWidgetId, prefs.copy(themeMode = newTheme))
                updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
            }
        }
    }

    companion object {

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {

            val prefs = context.getWidgetPrefs(appWidgetId)

            val views = RemoteViews(context.packageName, R.layout.widget_rss)

            val themedContext = getThemedContextForWidget(context, prefs.themeMode)
            val colorForeground = themedContext.getColorResCompat(android.R.attr.colorForeground)

            // Background transparency
            ThemeUtils.setBgTransparency(
                context,
                views,
                R.id.widget_rss,
                prefs.transparency,
                prefs.themeMode
            )

            // ----------------------------
            // STATIC UI
            // ----------------------------
            val hasCustomTitle = !prefs.customTitle.isNullOrEmpty()
            views.setTextViewText(R.id.widget_title, if (hasCustomTitle) prefs.customTitle else context.getString(R.string.app_label))
            views.setTextColor(R.id.widget_title, colorForeground)
            views.setTextColor(R.id.empty_text, colorForeground)
            
            // Set divider color with alpha
            val dividerColor = (colorForeground and 0x00FFFFFF) or 0x33000000 // ~20% alpha
            views.setInt(R.id.header_divider, "setBackgroundColor", dividerColor)

            views.setInt(R.id.btn_refresh, "setColorFilter", colorForeground)
            views.setInt(R.id.btn_theme_toggle, "setColorFilter", colorForeground)
            views.setInt(R.id.btn_settings, "setColorFilter", colorForeground)

            views.setViewVisibility(
                R.id.btn_refresh,
                if (prefs.showRefreshButton) View.VISIBLE else View.GONE
            )

            // ----------------------------
            // FIXED REMOTE ADAPTER (IMPORTANT)
            // ----------------------------
            val intent = Intent(context, RssRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

                // CRITICAL: prevents widget list caching bugs
                data = Uri.parse("rss://widget/$appWidgetId/${System.currentTimeMillis()}")
            }

            views.setRemoteAdapter(R.id.widget_list, intent)
            views.setEmptyView(R.id.widget_list, R.id.empty_text)

            // ----------------------------
            // CLICK HANDLING
            // ----------------------------
            val clickIntent = Intent(context, BrowserLauncherActivity::class.java)

            val clickPendingIntent = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            views.setPendingIntentTemplate(R.id.widget_list, clickPendingIntent)

            // ----------------------------
            // REFRESH BUTTON
            // ----------------------------
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

            // ----------------------------
            // THEME TOGGLE
            // ----------------------------
            val themeIntent = Intent(context, RssWidgetProvider::class.java).apply {
                action = "com.byterdevs.rsswidget.ACTION_THEME_TOGGLE"
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val themePendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId + 10000, // Unique requestCode
                themeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_theme_toggle, themePendingIntent)

            // ----------------------------
            // SETTINGS BUTTON
            // ----------------------------
            val settingsIntent = Intent(context, RssWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val settingsPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId + 20000,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_settings, settingsPendingIntent)

            // ----------------------------
            // APPLY UPDATE
            // ----------------------------
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // FORCE LIST REFRESH
            appWidgetManager.notifyAppWidgetViewDataChanged(
                appWidgetId,
                R.id.widget_list
            )

            enqueuePeriodicUpdate(context, appWidgetId)
        }

        private fun enqueuePeriodicUpdate(context: Context, appWidgetId: Int) {

            val prefs = context.getWidgetPrefs(appWidgetId)

            if (prefs.updateInterval == 0) return

            val workRequest = PeriodicWorkRequestBuilder<RssWidgetUpdateWorker>(
                prefs.updateInterval.toLong(),
                TimeUnit.MINUTES
            )
                .setInputData(
                    Data.Builder()
                        .putInt("appWidgetId", appWidgetId)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "rss_widget_update_$appWidgetId",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}