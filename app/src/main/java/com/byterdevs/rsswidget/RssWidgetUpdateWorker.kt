package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.byterdevs.rsswidget.room.RssDatabase
import com.byterdevs.rsswidget.room.RssItemEntity
import kotlinx.coroutines.runBlocking
import androidx.core.text.HtmlCompat
import com.byterdevs.rsswidget.room.RssItemDao
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import java.net.HttpURLConnection
import java.net.URL

class RssWidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val appWidgetId = inputData.getInt("appWidgetId", AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return Result.failure()
        }

        val hardRefresh = inputData.getBoolean("hardRefresh", false)

        val prefs = applicationContext.getWidgetPrefs(appWidgetId)
        val rssUrl = prefs.url ?: return Result.failure()

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val db = RssDatabase.getInstance(applicationContext)
        val dao = db.rssItemDao()

        if (hardRefresh) {
            Log.d("RssWidgetUpdateWorker", "Refresh request received")
            runBlocking { dao.clearItemsForWidget(appWidgetId) }
            RssWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
        }

        updateRssFeed(appWidgetId, dao, rssUrl, prefs)
        RssWidgetProvider.updateAppWidget(applicationContext, appWidgetManager, appWidgetId)
        return Result.success()
    }

    fun updateRssFeed(appWidgetId: Int, dao: RssItemDao, rssUrl: String, prefs: WidgetPrefs) = runBlocking {
        val feedUrl = URL(rssUrl)
        val input = SyndFeedInput()
        val connection = feedUrl.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:48.0) Gecko/48.0 Firefox/48.0")
        connection.connectTimeout = 10000
        connection.readTimeout = 15000

        try {
            connection.connect()
            val feed = input.build(XmlReader(connection.inputStream))
            val entities = feed.entries.take(prefs.maxItems).map { entry ->
                val title = entry.title ?: "No Title"
                val link = entry.link ?: ""
                val rawDescription = entry.description?.value ?: ""
                val plainDescription = HtmlCompat.fromHtml(rawDescription, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace("\n", " ").trim()
                val description = if (prefs.descriptionLength > 0 && plainDescription.length > prefs.descriptionLength)
                    plainDescription.take(prefs.descriptionLength) + "..."
                else plainDescription
                val source = if (prefs.showSource) {
                    try {
                        val host = URL(link).host
                        if (host.startsWith("www.")) host.substring(4) else host
                    } catch (e: Exception) { "" }
                } else ""
                RssItemEntity(
                    appWidgetId = appWidgetId,
                    title = title,
                    description = description,
                    link = link,
                    date = entry.publishedDate?.time,
                    source = source
                )
            }

            dao.clearItemsForWidget(appWidgetId)
            dao.insertAll(entities)
            Log.i("RssWidgetUpdateWorker", "Loaded ${entities.size} articles for widget $appWidgetId")
        } catch (e: Exception) {
            Log.e("RssWidgetUpdateWorker", e.toString())
        } finally {
            connection.disconnect()
        }
    }
}
