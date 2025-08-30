package com.byterdevs.rsswidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textview.MaterialTextView
import androidx.core.content.edit
import com.google.android.material.materialswitch.MaterialSwitch

class RssWidgetConfigureActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val urlInput: TextInputEditText get() = findViewById(R.id.edit_rss_url)
    private val addButton: MaterialButton get() = findViewById(R.id.button_add)
    private val titleInputLayout: TextInputLayout get() = findViewById(R.id.title_input_layout)
    private val titleEdit: TextInputEditText get() = findViewById(R.id.edit_widget_title)

    private val slider: Slider get() = findViewById(R.id.slider_max_items)
    private val labelMaxItems: MaterialTextView get() = findViewById(R.id.label_max_items)

    private val switchDescription: MaterialSwitch get() = findViewById(R.id.switch_description)
    private val switchTrimDescription: MaterialSwitch get() = findViewById(R.id.switch_trim_description)
    private val sliderTrimDescription: Slider get() = findViewById(R.id.slider_trim_description)
    private val transparencySlider: Slider get() = findViewById(R.id.slider_transparency)
    private val labelTransparency: MaterialTextView get() = findViewById(R.id.label_transparency)
    private val sampleButtonsContainer: LinearLayout get() = findViewById(R.id.sample_buttons_container)
    private val switchSource: MaterialSwitch get() = findViewById(R.id.switch_source)
    private val toggleButtonGroup: com.google.android.material.button.MaterialButtonToggleGroup
        get() = findViewById(
            R.id.toggle_button_group
        )
    private val updateIntervalSpinner: android.widget.Spinner get() = findViewById(R.id.spinner_update_interval)

    private val urlSamples = listOf(
        Pair("Reddit", "https://www.reddit.com/r/news/.rss"),
        Pair("Hacker News", "https://hnrss.org/frontpage?link=comments"),
        Pair("BBC", "https://feeds.bbci.co.uk/news/rss.xml"),
        Pair("NY Times", "https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml"),
        Pair("Guardian", "https://www.theguardian.com/world/rss"),
    )
    private val intervalValues = listOf(0, 15, 30, 60, 180, 360, 720) // minutes, 0 = manual


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_rss_widget_configure)

        // Find the widget id from the intent.
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val inflater = LayoutInflater.from(this)

        urlSamples.forEach { (label, url) ->
            val btn = inflater.inflate(
                R.layout.item_sample_rss_button,
                sampleButtonsContainer,
                false
            ) as MaterialButton
            btn.text = label
            btn.setOnClickListener { urlInput.setText(url) }
            btn.setLines(2)
            btn.maxLines = 2
            btn.setStrokeColorResource(android.R.color.darker_gray)
            btn.strokeWidth = resources.getDimensionPixelSize(R.dimen.sample_button_stroke_width)
            sampleButtonsContainer.addView(btn)
        }

        var maxItems = slider.value.toInt()
        var transparency = transparencySlider.value
        slider.addOnChangeListener { _, value, _ ->
            maxItems = value.toInt()
            labelMaxItems.text = getString(R.string.max_items_to_display, maxItems)
        }
        transparencySlider.addOnChangeListener { _, value, _ ->
            transparency = value
            labelTransparency.text = getString(R.string.widget_transparency_100)
        }

        switchDescription.setOnCheckedChangeListener { _, isChecked ->
            switchTrimDescription.visibility =
                if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            sliderTrimDescription.visibility =
                if (isChecked && switchTrimDescription.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        var descriptionLength =
            if (switchTrimDescription.isChecked) sliderTrimDescription.value.toInt() else -1
        switchTrimDescription.setOnCheckedChangeListener { _, isChecked ->
            sliderTrimDescription.visibility =
                if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            if (!isChecked) {
                descriptionLength = -1
                switchTrimDescription.text = getString(R.string.trim_description)
            } else {
                switchTrimDescription.text =
                    getString(R.string.trim_description_length, sliderTrimDescription.value.toInt())
                descriptionLength = sliderTrimDescription.value.toInt()
            }
        }

        sliderTrimDescription.addOnChangeListener { _, value, _ ->
            switchTrimDescription.text = getString(R.string.trim_description_length, value.toInt())
            descriptionLength = value.toInt()
        }


        val intervalOptions = listOf(
            getString(R.string.update_15min),
            getString(R.string.update_30min),
            getString(R.string.update_1hr),
            getString(R.string.update_3hr),
            getString(R.string.update_6hr),
            getString(R.string.update_12hr),
            getString(R.string.update_manual),

        )
        val intervalAdapter =
            android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, intervalOptions)
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        updateIntervalSpinner.adapter = intervalAdapter
        updateIntervalSpinner.setSelection(1)
        updateIntervalSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    val isManual = intervalValues[position] == 0
                    // Optionally disable auto-update logic here
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }

        addButton.setOnClickListener {
            val url = urlInput.text?.toString()?.trim() ?: ""
            val customTitle = titleEdit.text?.toString()?.trim()
            if (url.isNotEmpty()) {
                val showDescription = switchDescription.isChecked
                val showSource = switchSource.isChecked
                val dateFormat =
                    if (toggleButtonGroup.checkedButtonId == toggleButtonGroup.getChildAt(0).id) "relative" else "absolute"
                val updateInterval = intervalValues[updateIntervalSpinner.selectedItemPosition]
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit {
                    putString(PREF_PREFIX_KEY + appWidgetId, url)
                        .putString(PREF_PREFIX_KEY + "title_" + appWidgetId, customTitle)
                        .putInt(PREF_PREFIX_KEY + "max_" + appWidgetId, maxItems)
                        .putInt(
                            PREF_PREFIX_KEY + "description_length_" + appWidgetId,
                            descriptionLength
                        )
                        .putBoolean(PREF_PREFIX_KEY + "description_" + appWidgetId, showDescription)
                        .putFloat(PREF_PREFIX_KEY + "transparency_" + appWidgetId, transparency)
                        .putBoolean(PREF_PREFIX_KEY + "source_" + appWidgetId, showSource)
                        .putString(PREF_PREFIX_KEY + "date_format_" + appWidgetId, dateFormat)
                        .putInt(PREF_PREFIX_KEY + "update_interval_" + appWidgetId, updateInterval)
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
                urlInput.error = getString(R.string.rss_feed_url)
            }
        }
        restoreConfig()
    }

    private fun restoreConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        val savedTitle = prefs.getString(PREF_PREFIX_KEY + "title_" + appWidgetId, null)
        val savedMaxItems = prefs.getInt(PREF_PREFIX_KEY + "max_" + appWidgetId, 20)
        val savedShowDescription =
            prefs.getBoolean(PREF_PREFIX_KEY + "description_" + appWidgetId, false)
        val savedDescriptionLength =
            prefs.getInt(PREF_PREFIX_KEY + "description_length_" + appWidgetId, -1)
        val savedTransparency =
            prefs.getFloat(PREF_PREFIX_KEY + "transparency_" + appWidgetId, 100f)
        val savedShowSource = prefs.getBoolean(PREF_PREFIX_KEY + "source_" + appWidgetId, false)
        val savedDateFormat =
            prefs.getString(PREF_PREFIX_KEY + "date_format_" + appWidgetId, "relative")
        val savedUpdateInterval =
            prefs.getInt(PREF_PREFIX_KEY + "update_interval_" + appWidgetId, 0)

        if (!savedUrl.isNullOrEmpty()) {
            urlInput.setText(savedUrl)
        }
        if (!savedTitle.isNullOrEmpty()) {
            titleEdit.setText(savedTitle)
        }
        slider.value = savedMaxItems.toFloat()
        labelMaxItems.text = getString(R.string.max_items_to_display, savedMaxItems)
        switchDescription.isChecked = savedShowDescription
        if (switchDescription.isChecked) {
            switchTrimDescription.visibility = android.view.View.VISIBLE
        }

        if (savedDescriptionLength > 0) {
            switchTrimDescription.isChecked = true
            sliderTrimDescription.visibility = android.view.View.VISIBLE
            sliderTrimDescription.value = savedDescriptionLength.toFloat()
            switchTrimDescription.text =
                getString(R.string.trim_description_length, sliderTrimDescription.value.toInt())
        }

        transparencySlider.value = savedTransparency
        labelTransparency.text = getString(R.string.widget_transparency_100)
        switchSource.isChecked = savedShowSource
        // Set toggle button selection
        val relativeBtnId = toggleButtonGroup.getChildAt(0).id
        val absoluteBtnId = toggleButtonGroup.getChildAt(1).id
        toggleButtonGroup.check(if (savedDateFormat == "absolute") absoluteBtnId else relativeBtnId)

        val intervalIdx = intervalValues.indexOf(savedUpdateInterval).takeIf { it >= 0 } ?: 0
        updateIntervalSpinner.setSelection(intervalIdx)
    }

    companion object {
        const val PREFS_NAME = "com.byterdevs.rsswidget.RssWidgetProvider"
        const val PREF_PREFIX_KEY = "rss_url_"
        fun loadRssUrlPref(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + appWidgetId, null)
        }

        fun loadTitlePref(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + "title_" + appWidgetId, null)
        }

        fun loadMaxItemsPref(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_PREFIX_KEY + "max_" + appWidgetId, 20)
        }

        fun loadDescriptionPref(context: Context, appWidgetId: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(PREF_PREFIX_KEY + "description_" + appWidgetId, false)
        }

        fun loadDescriptionLenPref(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(PREF_PREFIX_KEY + "description_length_" + appWidgetId, -1)
        }

        fun loadTransparencyPref(context: Context, appWidgetId: Int): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getFloat(PREF_PREFIX_KEY + "transparency_" + appWidgetId, 100f)
        }

        fun loadShowSourcePref(context: Context, appWidgetId: Int): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(PREF_PREFIX_KEY + "source_" + appWidgetId, false)
        }

        fun loadDateFormatPref(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(PREF_PREFIX_KEY + "date_format_" + appWidgetId, "relative")
                ?: "relative"
        }

        fun loadUpdateIntervalPref(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(PREF_PREFIX_KEY + "update_interval_" + appWidgetId, 0)
        }
    }
}
