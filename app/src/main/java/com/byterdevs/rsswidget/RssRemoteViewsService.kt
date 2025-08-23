package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService

class RssRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val context = applicationContext
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val url = intent.getStringExtra("EXTRA_URL")
        val customTitle = intent.getStringExtra("EXTRA_TITLE")
        val maxItems = intent.getIntExtra("EXTRA_MAX_ITEMS", 20)
        val showDescription = intent.getBooleanExtra("EXTRA_SHOW_DESCRIPTION", false)
        val descriptionLength = intent.getIntExtra("EXTRA_DESCRIPTION_LENGTH", -1)
        val transparency = intent.getFloatExtra("EXTRA_TRANSPARENCY", 100f)
        val showSource = intent.getBooleanExtra("EXTRA_SHOW_SOURCE", false)
        val dateFormat = intent.getStringExtra("EXTRA_DATE_FORMAT") ?: "relative"
        val factory = RssRemoteViewsFactory(context, url, maxItems, showDescription, transparency)
        factory.setHeader(customTitle)
        factory.setDescriptionLength(descriptionLength)
        factory.setAppWidgetId(appWidgetId)
        factory.setShowSource(showSource)
        factory.setDateFormat(dateFormat)
        return factory
    }
}