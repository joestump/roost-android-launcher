package rocks.stump.claudelauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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

/** Settings + favorites picker + accent-tint chooser, styled per the "Roost" palette. */
class SettingsActivity : Activity() {

    private val accent: Int get() = Prefs.accent(this)
    private fun dp(v: Float): Int = Roost.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(30f), dp(22f), dp(44f))
        }

        col.addView(title(getString(R.string.settings_title)))

        // --- Home mode ---
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

        // --- Accent tint ---
        col.addView(header(getString(R.string.settings_accent)))
        col.addView(accentSwatches())

        // --- Wallpaper ---
        col.addView(header(getString(R.string.settings_wallpaper)))
        col.addView(Button(this).apply {
            text = getString(R.string.settings_wallpaper_apply)
            setOnClickListener {
                val ok = Roost.applyWallpaper(this@SettingsActivity)
                Prefs.setWallpaperApplied(this@SettingsActivity, true)
                Toast.makeText(
                    this@SettingsActivity,
                    if (ok) R.string.wallpaper_set else R.string.wallpaper_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        // --- Featured agent app ---
        col.addView(header(getString(R.string.settings_pkg)))
        val pkgEdit = EditText(this).apply {
            setText(Prefs.claudePkg(this@SettingsActivity))
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Roost.TEXT)
            setHintTextColor(Roost.MUTED)
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

        // --- Favorites ---
        col.addView(header(getString(R.string.settings_favorites)))
        val favs = Prefs.favorites(this)
        for ((pkg, label) in installedLaunchable()) {
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6f), 0, dp(6f))
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
                    setTextColor(Roost.MUTED)
                    textSize = 14f
                })
            })
        }

        col.addView(TextView(this).apply {
            text = getString(R.string.open_android_settings)
            setTextColor(accent)
            textSize = 15f
            setPadding(0, dp(28f), 0, 0)
            setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        })

        setContentView(ScrollView(this).apply {
            background = Roost.dockBackground(this@SettingsActivity)
            addView(col)
        })
    }

    private fun accentSwatches(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4f), 0, dp(4f))
        }
        for ((name, color) in Roost.ACCENTS) {
            val selected = color == accent
            val swatch = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (selected) Roost.DOCK else Roost.TEXT)
                background = Roost.rounded(
                    if (selected) color else Roost.TILE, dp(20f).toFloat(),
                    if (selected) color else Roost.HAIRLINE, dp(if (selected) 2f else 1f)
                )
                setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
                setOnClickListener {
                    Prefs.setAccent(this@SettingsActivity, color)
                    recreate()
                }
            }
            row.addView(swatch, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8f) })
        }
        return row
    }

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

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextColor(Roost.TEXT)
        textSize = 22f
        typeface = Roost.medium()
        setPadding(0, 0, 0, dp(6f))
    }

    private fun header(t: String) = TextView(this).apply {
        text = t.uppercase()
        setTextColor(accent)
        textSize = 12f
        letterSpacing = 0.08f
        typeface = Roost.medium()
        setPadding(0, dp(24f), 0, dp(8f))
    }

    private fun radio(t: String) = RadioButton(this).apply {
        text = t
        setTextColor(Roost.TEXT)
        id = View.generateViewId()
    }

    private fun switchRow(t: String, initial: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = t
        isChecked = initial
        setTextColor(Roost.TEXT)
        setPadding(0, dp(8f), 0, dp(8f))
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }
}
