package rocks.stump.claudelauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * Fully programmatic settings + favorites picker. Changes apply immediately (no save button
 * except for the Claude package override, which is free text).
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(40))
        }

        // --- Launcher mode ---
        col.addView(header(getString(R.string.settings_mode)))
        val modeGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val curated = radio(getString(R.string.mode_curated))
        val appliance = radio(getString(R.string.mode_appliance))
        modeGroup.addView(curated)
        modeGroup.addView(appliance)
        if (Prefs.mode(this) == Prefs.MODE_APPLIANCE) appliance.isChecked = true else curated.isChecked = true
        modeGroup.setOnCheckedChangeListener { _, id ->
            Prefs.setMode(this, if (id == appliance.id) Prefs.MODE_APPLIANCE else Prefs.MODE_CURATED)
        }
        col.addView(modeGroup)

        // --- Behavior ---
        col.addView(header(getString(R.string.settings_behavior)))
        col.addView(switchRow(getString(R.string.behavior_boot), Prefs.autoLaunchOnBoot(this)) {
            Prefs.setAutoLaunchOnBoot(this, it)
        })
        col.addView(switchRow(getString(R.string.behavior_screen), Prefs.keepScreenOn(this)) {
            Prefs.setKeepScreenOn(this, it)
        })

        // --- Claude package override ---
        col.addView(header(getString(R.string.settings_pkg)))
        val pkgEdit = EditText(this).apply {
            setText(Prefs.claudePkg(this@SettingsActivity))
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(FG)
            setHintTextColor(0xFF888888.toInt())
        }
        col.addView(pkgEdit)
        col.addView(Button(this).apply {
            text = getString(R.string.settings_pkg_save)
            setOnClickListener {
                val v = pkgEdit.text.toString().trim()
                if (v.isNotEmpty()) {
                    Prefs.setClaudePkg(this@SettingsActivity, v)
                    Toast.makeText(this@SettingsActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                }
            }
        })

        // --- Favorites picker ---
        col.addView(header(getString(R.string.settings_favorites)))
        val favs = Prefs.favorites(this)
        for ((pkg, label) in installedLaunchable()) {
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
                addView(CheckBox(this@SettingsActivity).apply {
                    isChecked = favs.contains(pkg)
                    setOnCheckedChangeListener { _, checked ->
                        val cur = Prefs.favorites(this@SettingsActivity)
                        if (checked) cur.add(pkg) else cur.remove(pkg)
                        Prefs.setFavorites(this@SettingsActivity, cur)
                    }
                })
                addView(TextView(this@SettingsActivity).apply {
                    text = "$label\n$pkg"
                    setTextColor(0xFFDDDDDD.toInt())
                    textSize = 14f
                })
            })
        }

        // --- Android settings escape hatch ---
        col.addView(TextView(this).apply {
            text = getString(R.string.open_android_settings)
            setTextColor(0xFF8AB4F8.toInt())
            textSize = 15f
            setPadding(0, dp(28), 0, 0)
            setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(col)
        })
    }

    /** All launchable apps except ourselves, de-duplicated by package and sorted by label. */
    private fun installedLaunchable(): List<Pair<String, String>> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull {
                val pkg = it.activityInfo.packageName
                if (pkg == packageName) null else pkg to it.loadLabel(pm).toString()
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    private fun header(t: String) = TextView(this).apply {
        text = t
        setTextColor(FG)
        textSize = 16f
        setPadding(0, dp(22), 0, dp(8))
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun radio(t: String) = RadioButton(this).apply {
        text = t
        setTextColor(0xFFDDDDDD.toInt())
        id = View.generateViewId()
    }

    private fun switchRow(t: String, initial: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t
        isChecked = initial
        setTextColor(0xFFDDDDDD.toInt())
        setPadding(0, dp(8), 0, dp(8))
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        private const val BG = 0xFF141414.toInt()
        private const val FG = 0xFFF2F2F2.toInt()
    }
}
