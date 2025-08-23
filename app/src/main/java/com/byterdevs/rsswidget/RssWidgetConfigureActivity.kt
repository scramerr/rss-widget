package com.byterdevs.rsswidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import androidx.core.content.edit

class RssWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val urlInput: TextInputEditText get() = findViewById(R.id.edit_rss_url)
    private val addButton: MaterialButton get() = findViewById(R.id.button_add)
    private val switchTitle: SwitchMaterial get() = findViewById(R.id.switch_title)
    private val titleInputLayout: TextInputLayout get() = findViewById(R.id.title_input_layout)
    private val titleEdit: TextInputEditText get() = findViewById(R.id.edit_widget_title)

    private val slider: Slider get() = findViewById(R.id.slider_max_items)
    private val labelMaxItems: MaterialTextView get() = findViewById(R.id.label_max_items)

    private val switchDescription: SwitchMaterial get() = findViewById(R.id.switch_description)
    private val switchTrimDescription: SwitchMaterial get() = findViewById(R.id.switch_trim_description)
    private val sliderTrimDescription: Slider get() = findViewById(R.id.slider_trim_description)
    private val transparencySlider: Slider get() = findViewById(R.id.slider_transparency)
    private val labelTransparency: MaterialTextView get() = findViewById(R.id.label_transparency)
    private val sampleButtonsContainer: LinearLayout get() = findViewById(R.id.sample_buttons_container)
    private val switchSource: SwitchMaterial get() = findViewById(R.id.switch_source)
    private val toggleButtonGroup: com.google.android.material.button.MaterialButtonToggleGroup get() = findViewById(R.id.toggle_button_group)

    private val urlSamples = listOf(
        Pair("Reddit", "https://www.reddit.com/r/news/.rss"),
        Pair("Hacker News", "https://hnrss.org/frontpage?link=comments"),
        Pair("BBC", "https://feeds.bbci.co.uk/news/rss.xml"),
        Pair("NY Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
        Pair("Guardian", "https://www.theguardian.com/world/rss"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_rss_widget_configure)

        // Find the widget id from the intent.
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val inflater = LayoutInflater.from(this)

        urlSamples.forEach { (label, url) ->
            val btn = inflater.inflate(R.layout.item_sample_rss_button, sampleButtonsContainer, false) as MaterialButton
            btn.text = label
            btn.setOnClickListener { urlInput.setText(url) }
            btn.setLines(2)
            btn.maxLines = 2
            btn.setStrokeColorResource(android.R.color.darker_gray)
            btn.strokeWidth = resources.getDimensionPixelSize(R.dimen.sample_button_stroke_width)
            sampleButtonsContainer.addView(btn)
        }

        restoreConfig()
        var maxItems = slider.value.toInt()
        var transparency = transparencySlider.value
        slider.addOnChangeListener { _, value, _ ->
            maxItems = value.toInt()
            labelMaxItems.text = "Max items to display: $maxItems"
        }
        transparencySlider.addOnChangeListener { _, value, _ ->
            transparency = value
            labelTransparency.text = "Widget background opacity: ${value.toInt()}%"
        }

        switchTitle.setOnCheckedChangeListener { _, isChecked ->
            titleInputLayout.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        switchDescription.setOnCheckedChangeListener { _, isChecked ->
            switchTrimDescription.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        var descriptionLength = if (switchTrimDescription.isChecked) sliderTrimDescription.value.toInt() else -1
        switchTrimDescription.setOnCheckedChangeListener { _, isChecked ->
            sliderTrimDescription.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            if (!isChecked) {
                descriptionLength = -1
                switchTrimDescription.text  = "Trim description?"
            } else {
                switchTrimDescription.text  = "Trim description: ${sliderTrimDescription.value.toInt()} chars"
            }
        }
        sliderTrimDescription.addOnChangeListener { _, value, _ ->
            switchTrimDescription.text  = "Trim description: ${value.toInt()} chars"
            descriptionLength = value.toInt()
        }

        addButton.setOnClickListener {
            val url = urlInput.text?.toString()?.trim() ?: ""
            val customTitle = if (switchTitle.isChecked) titleEdit.text?.toString()?.trim() else null
            if (url.isNotEmpty()) {
                val showDescription  = switchDescription.isChecked
                val showSource = switchSource.isChecked
                val dateFormat = if (toggleButtonGroup.checkedButtonId == toggleButtonGroup.getChildAt(0).id) "relative" else "absolute"
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit {
                    putString(PREF_PREFIX_KEY + appWidgetId, url)
                    .putString(PREF_PREFIX_KEY + "title_" + appWidgetId, customTitle)
                    .putInt(PREF_PREFIX_KEY + "max_" + appWidgetId, maxItems)
                    .putInt(PREF_PREFIX_KEY + "description_length_" + appWidgetId, descriptionLength)
                    .putBoolean(PREF_PREFIX_KEY + "description_" + appWidgetId, showDescription)
                    .putFloat(PREF_PREFIX_KEY + "transparency_" + appWidgetId, transparency)
                    .putBoolean(PREF_PREFIX_KEY + "source_" + appWidgetId, showSource)
                    .putString(PREF_PREFIX_KEY + "date_format_" + appWidgetId, dateFormat)
                }

                val context = this@RssWidgetConfigureActivity
                val appWidgetManager = AppWidgetManager.getInstance(context)
                RssWidgetProvider.updateAppWidget(
                    context, appWidgetManager, appWidgetId
                )

                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
                finish()
            } else {
                urlInput.error = "Please enter a valid RSS feed URL"
            }
        }
    }

    private fun restoreConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        val savedTitle = prefs.getString(PREF_PREFIX_KEY + "title_" + appWidgetId, null)
        val savedMaxItems = prefs.getInt(PREF_PREFIX_KEY + "max_" + appWidgetId, 20)
        val savedShowDescription = prefs.getBoolean(PREF_PREFIX_KEY + "description_" + appWidgetId, false)
        val savedDescriptionLength = prefs.getInt(PREF_PREFIX_KEY + "description_length_" + appWidgetId, -1)
        val savedTransparency = prefs.getFloat(PREF_PREFIX_KEY + "transparency_" + appWidgetId, 100f)
        val savedShowSource = prefs.getBoolean(PREF_PREFIX_KEY + "source_" + appWidgetId, false)
        val savedDateFormat = prefs.getString(PREF_PREFIX_KEY + "date_format_" + appWidgetId, "relative")

        if (!savedUrl.isNullOrEmpty()) {
            urlInput.setText(savedUrl)
        }
        switchTitle.isChecked = !savedTitle.isNullOrEmpty()
        titleInputLayout.visibility = if (switchTitle.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        if (!savedTitle.isNullOrEmpty()) {
            titleEdit.setText(savedTitle)
        }
        slider.value = savedMaxItems.toFloat()
        labelMaxItems.text = "Max items to display: $savedMaxItems"
        switchDescription.isChecked = savedShowDescription
        if (savedDescriptionLength > 0) {
            switchTrimDescription.isChecked = true
            switchTrimDescription.visibility = android.view.View.VISIBLE
            sliderTrimDescription.visibility = android.view.View.VISIBLE
            sliderTrimDescription.value = savedDescriptionLength.toFloat()
            switchTrimDescription.text = "Trim description: ${sliderTrimDescription.value.toInt()} chars"
        }
        transparencySlider.value = savedTransparency
        labelTransparency.text = "Widget background opacity: ${savedTransparency.toInt()}%"
        switchSource.isChecked = savedShowSource
        // Set toggle button selection
        val relativeBtnId = toggleButtonGroup.getChildAt(0).id
        val absoluteBtnId = toggleButtonGroup.getChildAt(1).id
        toggleButtonGroup.check(if (savedDateFormat == "absolute") absoluteBtnId else relativeBtnId)
    }

    companion object {
        const val PREFS_NAME = "com.byterdevs.rsswidget.RssWidgetProvider"
        const val PREF_PREFIX_KEY = "rss_url_"
        fun loadRssUrlPref(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        }
        fun loadTitlePref(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + "title_" + appWidgetId, null)
        }
        fun loadMaxItemsPref(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(PREF_PREFIX_KEY + "max_" + appWidgetId, 20)
        }
        fun loadDescriptionPref(context: Context, appWidgetId: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_PREFIX_KEY + "description_" + appWidgetId, false)
        }

        fun loadDescriptionLenPref(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(PREF_PREFIX_KEY + "description_length_" + appWidgetId, -1)
        }

        fun loadTransparencyPref(context: Context, appWidgetId: Int): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getFloat(PREF_PREFIX_KEY + "transparency_" + appWidgetId, 100f)
        }

        fun loadShowSourcePref(context: Context, appWidgetId: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_PREFIX_KEY + "source_" + appWidgetId, false)
        }
        fun loadDateFormatPref(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + "date_format_" + appWidgetId, "relative") ?: "relative"
        }
    }
}
