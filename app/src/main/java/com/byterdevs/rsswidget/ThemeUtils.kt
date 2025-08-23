package com.byterdevs.rsswidget

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils

object ThemeUtils {
    fun getThemedContextForWidget(context: Context): Context {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        val newConfig = Configuration(context.resources.configuration)
        newConfig.uiMode = if (isNightMode) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        val configurationContext = context.applicationContext.createConfigurationContext(newConfig)
        return ContextThemeWrapper(configurationContext, com.google.android.material.R.style.Theme_Material3_DynamicColors_DayNight)
    }

    fun setBgTransparency(context: Context, views: RemoteViews, viewName: Int, transparency: Float): RemoteViews {
        val alpha = (transparency / 100 * 255).toInt()
        val configurationContext = getThemedContextForWidget(context)
        val colorBg = configurationContext.getColorResCompat(android.R.attr.colorBackground)
        views.setInt(viewName, "setBackgroundColor", ColorUtils.setAlphaComponent(colorBg, alpha))
        return views
    }
}