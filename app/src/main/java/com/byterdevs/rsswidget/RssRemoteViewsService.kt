package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService

class RssRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val context = applicationContext
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val factory = RssRemoteViewsFactory(context, appWidgetId)
        return factory
    }
}