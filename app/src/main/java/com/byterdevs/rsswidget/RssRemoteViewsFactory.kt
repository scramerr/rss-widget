package com.byterdevs.rsswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Parcelable
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import com.byterdevs.rsswidget.ThemeUtils.getThemedContextForWidget
import com.byterdevs.rsswidget.ThemeUtils.setBgTransparency
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import kotlinx.parcelize.Parcelize
import org.ocpsoft.prettytime.PrettyTime
import java.net.HttpURLConnection
import java.net.URL
import android.text.format.DateFormat
import java.util.Calendar


@ColorInt
fun Context.getColorResCompat(@AttrRes id: Int): Int {
    val resolvedAttr = TypedValue()
    this.theme.resolveAttribute(id, resolvedAttr, true)
    val colorRes = resolvedAttr.run { if (resourceId != 0) resourceId else data }
    return ContextCompat.getColor(this, colorRes)
}

class RssRemoteViewsFactory(
    private val context: Context,
    private val rssUrl: String?,
    private val maxItems: Int = 20,
    private val showDescription: Boolean = false,
    private val transparency: Float = 100f
) : RemoteViewsService.RemoteViewsFactory {
    private var items = mutableListOf<RssItem>()
    private var customTitle: String? = null
    private var trimDescription: Boolean = false
    private var descriptionTrimLength: Int = 100
    private var showTitle: Boolean = false
    private var appWidgetId: Int = -1
    private var error: Boolean = false
    private var showSource: Boolean = false
    private var dateFormat: String = "relative"
    private var dimReadItems: Boolean = true

    companion object {
        private val refreshLock = Any()
        @Volatile private var isRefreshing = false
    }
    override fun onCreate() {

    }

    fun setShowSource(show: Boolean) { showSource = show }
    fun setDateFormat(format: String) { dateFormat = format }
    fun setDimReadItems(value: Boolean) { dimReadItems = value }

    fun getSourceFromUrl(url: String): String {
        return try {
            val host = URL(url).host
            if (host.startsWith("www.")) host.substring(4) else host
        } catch (e: Exception) {
            ""
        }
    }

    fun formatAsTodayOrFullDate(date: java.util.Date): String {
        return if (Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == date.date) {
            "Today, " + DateFormat.format("h:mm a", date).toString()
        } else {
            DateFormat.format("MMM d, yyyy h:mm a", date).toString()
        }
    }

    fun formatDate(date: java.util.Date?): String {
        if (date == null) return ""
        return if (dateFormat == "absolute") {
            formatAsTodayOrFullDate(date)
        } else {
            PrettyTime().format(date)
        }
    }

    fun loadRSS(url: String): List<RssItem> {
        val items = mutableListOf<RssItem>()
        val feedUrl = URL(url)
        val input = SyndFeedInput()
        val connection = feedUrl.openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:48.0) Gecko/48.0 Firefox/48.0")
        connection.connectTimeout = 10000 // 10 seconds
        connection.readTimeout = 15000 // 15 seconds
        connection.connect()

        val feed = input.build(XmlReader(connection.inputStream))
        for (entry in feed.entries.take(maxItems)) {
            val title = entry.title ?: "No Title"
            val link = entry.link ?: ""
            val rawDescription = entry.description?.value ?: ""
            val plainDescription = HtmlCompat.fromHtml(rawDescription, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().replace("\n", " ").trim()
            val description = if (trimDescription && plainDescription.length > descriptionTrimLength)
                plainDescription.take(descriptionTrimLength) + "..."
                else plainDescription

            val pubDate = formatDate(entry.publishedDate)
            val source = if (showSource) getSourceFromUrl(link) else ""
            items.add(RssItem(title, description, link, pubDate, source))
        }
        return items
    }

    override fun onDataSetChanged() {
        synchronized(refreshLock) {
            if (isRefreshing) {
                Log.d("RssRemoteViewsFactory", "Refresh already in progress, ignoring this request.")
                return
            }
            isRefreshing = true
        }

        // Check network availability
        fun isNetworkAvailable(): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        error = false

        if(rssUrl == null) {
            error = true
            if (items.isEmpty()) {
                items.add(RssItem("Failed to load RSS feed", "Verify the URL and add the widget again.", "", ""))
            }
            isRefreshing = false
            return
        }

        if (!isNetworkAvailable()) {
            error = true
            Log.e("RssRemoteViewsFactory", "Network unavailable, not clearing items.")
            if (items.isEmpty()) {
                items.add(RssItem("No internet connection", "Connect to the internet and refresh.", "", ""))
            }
            isRefreshing = false
            return
        }

        items.clear()

        try {
            val url = rssUrl
            val loadedItems = loadRSS(url)
            items.addAll(loadedItems)
            Log.d("RssRemoteViewsFactory", "Data loaded successfully. Item count: ${items.size}")
        } catch (e: Exception) {
            Log.e("RssRemoteViewsFactory", "Failed to load RSS feed", e)
            items.clear()
            error = true
            items.add(RssItem("Failed to load RSS feed", "Verify the URL and add the widget again.", "", ""))
        } finally {
            isRefreshing = false
        }
    }
    
    fun setHeader(title: String?) {
        customTitle = title
        showTitle = !title.isNullOrEmpty()
    }

    fun setDescriptionLength(length: Int) {
        if (length > 0) {
            trimDescription = true
            descriptionTrimLength = length
        }
    }

    fun setAppWidgetId(id: Int) { appWidgetId = id }

    override fun getCount(): Int {
        return items.size + if (showTitle) 1 else 0
    }

    override fun getViewTypeCount(): Int {
        // We have a header and a list item, so two view types.
        return 2
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (showTitle && position == 0) {
            val headerViews = RemoteViews(context.packageName, R.layout.widget_rss_header)
            headerViews.setTextViewText(R.id.widget_title, customTitle)
            return headerViews
        }
        val itemIndex = if (showTitle) position - 1 else position
        if (itemIndex >= items.size) {
            return loadingView
        }
        val item = items[itemIndex]
        val views = RemoteViews(context.packageName, R.layout.widget_rss_item)
        views.setTextViewText(R.id.item_title, item.title)
        if((showDescription || error) && item.description.isNotEmpty()) {
            views.setViewVisibility(R.id.item_description, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_description, item.description)
        } else {
            views.setViewVisibility(R.id.item_description, android.view.View.GONE)
        }
        views.setTextViewText(R.id.item_date, item.pubDate)
        if (showSource && item.source.isNotEmpty()) {
            views.setViewVisibility(R.id.item_source, android.view.View.VISIBLE)
            views.setTextViewText(R.id.item_source, item.source)
        } else {
            views.setViewVisibility(R.id.item_source, android.view.View.GONE)
        }

        if(dimReadItems) {
            markItemRead(views, item)
        }

        val fillInIntent = Intent()
        fillInIntent.data = item.link.toUri()
        fillInIntent.putExtra("EXTRA_LINK", item.link)
        fillInIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        views.setOnClickFillInIntent(R.id.item_title, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_description, fillInIntent)
        views.setOnClickFillInIntent(R.id.item_date, fillInIntent)
        views.setOnClickFillInIntent(R.id.widget_rss_item, fillInIntent)
        return views
    }

    fun markItemRead(views: RemoteViews, item: RssItem) {
        val configurationContext = getThemedContextForWidget(context)
        val colorSecondary = configurationContext.getColorResCompat(android.R.attr.colorSecondary)
        val colorTextSecondary = configurationContext.getColorResCompat(android.R.attr.textColorSecondary)
        val colorTitle = configurationContext.getColorResCompat(android.R.attr.colorForeground)
        val colorDesc = configurationContext.getColorResCompat(android.R.attr.textColorPrimary)

        // Dim read items
        val isRead = ReadItemsStore.isRead(context, appWidgetId, item.link)
        if (isRead) {
            views.setTextColor(R.id.item_title, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
            views.setTextColor(R.id.item_description, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
            views.setTextColor(R.id.item_date, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
            views.setTextColor(R.id.item_source, context.getColor(com.google.android.material.R.color.material_dynamic_neutral50))
        } else {
            views.setTextColor(R.id.item_title, colorTitle)
            views.setTextColor(R.id.item_description, colorDesc)
            views.setTextColor(R.id.item_date, colorSecondary)
            views.setTextColor(R.id.item_source, colorTextSecondary)
        }
    }

    override fun getLoadingView(): RemoteViews {
        return setBgTransparency(context, RemoteViews(context.packageName, R.layout.widget_rss_loading), R.id.widget_rss_loading, transparency)
    }

    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() { items.clear() }

    @Parcelize
    data class RssItem(
        val title: String,
        val description: String,
        val link: String,
        val pubDate: String,
        val source: String = ""
    ): Parcelable
}
