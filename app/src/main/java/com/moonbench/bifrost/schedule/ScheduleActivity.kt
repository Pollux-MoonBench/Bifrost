package com.moonbench.bifrost.schedule

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.time.MonthDay
import java.util.UUID

/**
 * The schedule screen: a list of rules, in priority order, plus the editor for
 * one rule.
 *
 * Built programmatically like the plugin store, for the same reason — the main
 * settings layout is already very large, and a screen that owns its own views
 * can change without disturbing it.
 */
class ScheduleActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("bifrost_prefs", Context.MODE_PRIVATE) }

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyLabel: TextView
    private var rules: MutableList<ScheduleRule> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Schedule"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        rules = ScheduleStore.load(prefs).toMutableList()
        setContentView(buildRoot())
        renderRules()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ---- scaffold --------------------------------------------------------

    private fun buildRoot(): View {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Schedule"
            textSize = 22f
            setPadding(0, 0, 0, dp(4))
        })
        root.addView(TextView(this).apply {
            text = "Play a preset, or switch the LEDs off, between set hours — " +
                "optionally only during part of the year. A rule limited to dates " +
                "wins over an all-year rule; otherwise the first matching rule wins. " +
                "While the schedule is on, any hour no rule covers is dark."
            textSize = 13f
            alpha = 0.7f
            setPadding(0, 0, 0, dp(12))
        })

        root.addView(CheckBox(this).apply {
            text = "Enable the schedule"
            isChecked = ScheduleStore.isEnabled(prefs)
            setOnCheckedChangeListener { _, checked ->
                ScheduleStore.setEnabled(prefs, checked)
                // Applying immediately, rather than at the next boundary, so the
                // switch does something visible right away.
                ScheduleApplier.apply(this@ScheduleActivity)
            }
        })

        root.addView(Button(this).apply {
            text = "Add a rule"
            setOnClickListener { showRuleEditor(null) }
        })

        emptyLabel = TextView(this).apply {
            text = "No rules yet."
            textSize = 13f
            alpha = 0.7f
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(emptyLabel)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(listContainer)

        return scroll
    }

    // ---- rule list -------------------------------------------------------

    private fun renderRules() {
        listContainer.removeAllViews()
        emptyLabel.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE

        rules.forEachIndexed { index, rule ->
            listContainer.addView(buildRuleRow(index, rule))
        }
    }

    private fun buildRuleRow(index: Int, rule: ScheduleRule): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }

        row.addView(TextView(this).apply {
            text = rule.label.ifBlank { describeAction(rule.action) }
            textSize = 16f
        })
        row.addView(TextView(this).apply {
            text = describe(rule)
            textSize = 13f
            alpha = 0.7f
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        controls.addView(CheckBox(this).apply {
            text = "On"
            isChecked = rule.enabled
            setOnCheckedChangeListener { _, checked ->
                replace(index, rule.copy(enabled = checked))
            }
        })
        controls.addView(Button(this).apply {
            text = "Edit"
            setOnClickListener { showRuleEditor(index) }
        })
        controls.addView(Button(this).apply {
            text = "Delete"
            setOnClickListener {
                rules.removeAt(index)
                persist()
            }
        })
        // Priority is list order, so it has to be changeable from here.
        if (index > 0) {
            controls.addView(Button(this).apply {
                text = "▲"
                setOnClickListener {
                    val moved = rules.removeAt(index)
                    rules.add(index - 1, moved)
                    persist()
                }
            })
        }

        row.addView(controls)
        return row
    }

    private fun describe(rule: ScheduleRule): String {
        val time = "${formatMinute(rule.startMinuteOfDay)} – ${formatMinute(rule.endMinuteOfDay)}"
        val season = rule.dateWindow?.let {
            ", ${formatMonthDay(it.start)} → ${formatMonthDay(it.end)}"
        } ?: ""
        return "$time$season · ${describeAction(rule.action)}"
    }

    private fun describeAction(action: ScheduleAction): String = when (action) {
        is ScheduleAction.PlayPreset -> "play “${action.presetName}”"
        ScheduleAction.TurnOff -> "lights off"
    }

    private fun formatMinute(minuteOfDay: Int): String =
        "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    private fun formatMonthDay(monthDay: MonthDay): String =
        "%02d/%02d".format(monthDay.monthValue, monthDay.dayOfMonth)

    private fun replace(index: Int, rule: ScheduleRule) {
        rules[index] = rule
        persist()
    }

    private fun persist() {
        ScheduleStore.save(prefs, rules)
        ScheduleApplier.apply(this)
        renderRules()
    }

    // ---- rule editor -----------------------------------------------------

    private fun showRuleEditor(index: Int?) {
        val existing = index?.let { rules[it] }
        val presetNames = loadPresetNames()

        if (presetNames.isEmpty()) {
            Toast.makeText(this, "Save a preset first — a rule plays one.", Toast.LENGTH_LONG).show()
            return
        }

        var startMinute = existing?.startMinuteOfDay ?: 20 * 60
        var endMinute = existing?.endMinuteOfDay ?: 7 * 60
        var window = existing?.dateWindow

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), 0)
        }

        val labelField = EditText(this).apply {
            hint = "Name (optional)"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(existing?.label.orEmpty())
        }
        content.addView(labelField)

        val startButton = Button(this).apply { text = "From ${formatMinute(startMinute)}" }
        startButton.setOnClickListener {
            pickTime(startMinute) { picked ->
                startMinute = picked
                startButton.text = "From ${formatMinute(picked)}"
            }
        }
        content.addView(startButton)

        val endButton = Button(this).apply { text = "Until ${formatMinute(endMinute)}" }
        endButton.setOnClickListener {
            pickTime(endMinute) { picked ->
                endMinute = picked
                endButton.text = "Until ${formatMinute(picked)}"
            }
        }
        content.addView(endButton)

        content.addView(TextView(this).apply {
            text = "An end earlier than the start runs through midnight."
            textSize = 12f
            alpha = 0.7f
        })

        // Action: every saved preset, plus switching off.
        val actionLabels = presetNames + OFF_LABEL
        val actionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ScheduleActivity,
                android.R.layout.simple_spinner_dropdown_item,
                actionLabels
            )
            val current = when (val action = existing?.action) {
                is ScheduleAction.PlayPreset -> actionLabels.indexOf(action.presetName)
                ScheduleAction.TurnOff -> actionLabels.lastIndex
                null -> 0
            }
            setSelection(current.coerceAtLeast(0))
        }
        content.addView(TextView(this).apply { text = "Do:" ; setPadding(0, dp(8), 0, 0) })
        content.addView(actionSpinner)

        val seasonButton = Button(this)
        fun renderSeason() {
            seasonButton.text = window
                ?.let { "Only ${formatMonthDay(it.start)} → ${formatMonthDay(it.end)}" }
                ?: "All year round"
        }
        renderSeason()
        seasonButton.setOnClickListener {
            if (window != null) {
                window = null
                renderSeason()
            } else {
                pickMonthDay("Season starts") { start ->
                    pickMonthDay("Season ends") { end ->
                        window = DateWindow(start, end)
                        renderSeason()
                    }
                }
            }
        }
        content.addView(seasonButton)
        content.addView(TextView(this).apply {
            text = "Tap again to clear the season."
            textSize = 12f
            alpha = 0.7f
        })

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New rule" else "Edit rule")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val chosen = actionLabels[actionSpinner.selectedItemPosition]
                val action = if (chosen == OFF_LABEL) {
                    ScheduleAction.TurnOff
                } else {
                    ScheduleAction.PlayPreset(chosen)
                }

                val rule = ScheduleRule(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    label = labelField.text.toString().trim(),
                    enabled = existing?.enabled ?: true,
                    startMinuteOfDay = startMinute,
                    endMinuteOfDay = endMinute,
                    dateWindow = window,
                    action = action
                )

                if (index == null) rules.add(rule) else rules[index] = rule
                persist()
            }
            .show()
    }

    private fun pickTime(initialMinuteOfDay: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinuteOfDay / 60,
            initialMinuteOfDay % 60,
            true
        ).show()
    }

    /**
     * Month and day only: a season repeats every year, so asking for a year
     * would invite the user to write one that expires.
     */
    private fun pickMonthDay(title: String, onPicked: (MonthDay) -> Unit) {
        val monthPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 12
            value = 1
        }
        val dayPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 31
            value = 1
        }
        monthPicker.setOnValueChangedListener { _, _, month ->
            dayPicker.maxValue = MonthDay.of(month, 1).month.maxLength()
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), 0)
            addView(monthPicker)
            addView(dayPicker)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(row)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                // February 29 is allowed: MonthDay accepts it and it simply never
                // matches in a common year.
                runCatching { MonthDay.of(monthPicker.value, dayPicker.value) }
                    .getOrNull()
                    ?.let(onPicked)
            }
            .show()
    }

    private fun loadPresetNames(): List<String> {
        val raw = prefs.getString("presets_json", null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.optString("name") }
            .filter { it.isNotBlank() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val OFF_LABEL = "Switch the LEDs off"
    }
}
