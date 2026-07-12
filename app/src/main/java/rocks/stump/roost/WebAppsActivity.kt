package rocks.stump.roost

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Web apps (ADR-0005): add / list / remove a name + URL that opens fullscreen as a WebView tile on the
 * home grid. Each web app can carry a custom icon chosen from the shared [IconPickerActivity] (ADR-0003),
 * keyed by "web:<url>" exactly as the home grid keys its tile overrides. Reads and writes Prefs.webApps.
 *
 * Add-form flow (owner feedback: "why enter URL before choosing an icon", and choosing an icon wiped the
 * name/URL and wasn't applied): the name + URL live in [addName]/[addUrl] on the Activity, kept in sync
 * as the user types, so the icon-picker round-trip (which finishes back here → onResume → rebuild) never
 * wipes them. The icon override is keyed "web:<normalizedUrl>" in Prefs and shown as "selected" in the
 * add-form slot on return; it carries straight to the home tile when "Add web app" persists the URL.
 *
 * Governing: ADR-0005 (settings navigation IA), ADR-0003 (icon rendering strategy)
 */
class WebAppsActivity : SettingsScreen() {

    // Held add-form state so a rebuild (notably returning from the icon picker) restores what was typed.
    private var addName = ""
    private var addUrl = ""

    override fun screenTitle(): String = "Web Apps"

    override fun buildContent(body: LinearLayout) {
        body.addView(hint("Pin a URL as a fullscreen tile on the home grid.")
            .apply { setPadding(dp(4f), dp(4f), dp(4f), dp(12f)) })

        val apps = Prefs.webApps(this)
        if (apps.isNotEmpty()) {
            body.addView(card(apps.map { wa -> webAppRow(wa) }))
            body.addView(gap(dp(20f)))
        }

        body.addView(sectionHeader("Add web app", firstOnScreen = apps.isEmpty()))

        // Name + URL first (the natural order), then "Choose icon", then Add. Seed from the held state so
        // nothing is lost across rebuilds, and keep the state synced as the user types.
        val name = plainField(getString(R.string.webapp_name_hint)).apply { setText(addName) }
        val url = plainField(getString(R.string.webapp_url_hint), uri = true, mono = true).apply { setText(addUrl) }
        name.addTextChangedListener(watch { addName = it })
        url.addTextChangedListener(watch { addUrl = it })

        body.addView(name)
        body.addView(gap(dp(9f)))
        body.addView(url)
        body.addView(gap(dp(9f)))
        body.addView(chooseIconRow(url))
        body.addView(gap(dp(12f)))
        body.addView(LinearLayout(this).apply {
            addView(TextView(this@WebAppsActivity).apply {
                text = "+  " + getString(R.string.webapp_add)
                setTextColor(accent); textSize = 13f
                background = Roost.rounded(Roost.soft(accent), dp(12f).toFloat())
                setPadding(dp(18f), dp(11f), dp(18f), dp(11f))
                isClickable = true
                setOnClickListener {
                    val u = normalizeUrl(url.text.toString())
                    if (u.isNotEmpty()) {
                        // The icon override is already keyed "web:<u>", so it carries to the home tile.
                        Prefs.addWebApp(this@WebAppsActivity, name.text.toString().trim(), u)
                        addName = ""; addUrl = ""   // clear the add form (the icon override stays keyed to <u>)
                        rebuild()
                    }
                }
            })
        })
    }

    /** Refresh so restored name/url + a freshly-picked icon show on return from the icon picker. */
    override fun onResume() {
        super.onResume()
        rebuild()
    }

    private fun watch(onChange: (String) -> Unit) = object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) { onChange(s?.toString() ?: "") }
        override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
    }

    // --- rows ---------------------------------------------------------------------------------

    private fun webAppRow(wa: WebApp): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(13f), dp(14f), dp(13f))
        }
        row.addView(iconHolder("web:${wa.url}", 38f) { openIconPicker("web:${wa.url}") })
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        stack.addView(TextView(this).apply {
            text = wa.name; setTextColor(Roost.TEXT); textSize = 14.5f; maxLines = 1
        })
        stack.addView(TextView(this).apply {
            text = wa.url; setTextColor(SUBTLE); textSize = 10.5f; typeface = Typeface.MONOSPACE
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2f), 0, 0)
        })
        row.addView(stack)
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_trash)
            setColorFilter(0xFFCF6B5A.toInt())
            val p = dp(6f); setPadding(p, p, p, p)
            isClickable = true
            background = Roost.rounded(0, dp(16f).toFloat())
            setOnClickListener { Prefs.removeWebApp(this@WebAppsActivity, wa.url); rebuild() }
            layoutParams = LinearLayout.LayoutParams(dp(32f), dp(32f))
        })
        return row
    }

    /**
     * The add-form "Choose icon" row. Its leading slot shows the icon already chosen for the entered URL
     * (so it reads as "selected" after the picker returns), else the web glyph. Tapping requires a URL —
     * icon overrides are keyed "web:<normalizedUrl>" — and opens the picker WITHOUT pinning a tile; the
     * tile is created only on "Add web app". The typed name/URL are already held in [addName]/[addUrl]
     * (synced on every keystroke), so returning here restores them instead of wiping them.
     */
    private fun chooseIconRow(url: EditText): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(rowBg(), dp(13f).toFloat(), rowBorder(), dp(1f))
            setPadding(dp(12f), dp(9f), dp(14f), dp(9f))
            isClickable = true
            setOnClickListener {
                val u = normalizeUrl(url.text.toString())
                if (u.isEmpty()) {
                    Toast.makeText(this@WebAppsActivity, "Enter a URL first, then choose its icon.", Toast.LENGTH_SHORT).show()
                } else {
                    openIconPicker("web:$u")
                }
            }
        }
        val chosen: Drawable? =
            if (addUrl.isNotBlank()) Prefs.iconOverride(this, "web:${normalizeUrl(addUrl)}")
                ?.let { IconStore.drawableFor(this, it) }
            else null
        val holder = FrameLayout(this).apply {
            background = Roost.rounded(Roost.TILE, dp(11f).toFloat(), Roost.HAIRLINE, dp(1f))
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { rightMargin = dp(12f) }
        }
        holder.addView(ImageView(this).apply {
            if (chosen != null) {
                setImageDrawable(chosen)
            } else {
                setImageResource(R.drawable.ic_web); setColorFilter(accent)
            }
            val p = dp(8f); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        row.addView(holder)
        row.addView(TextView(this).apply {
            text = if (chosen != null) "Change Icon" else "Choose Icon"
            setTextColor(ROW_LABEL); textSize = 13.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(0xFF6E665B.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f))
        })
        return row
    }

    /** A tappable rounded icon holder: shows the web app's override icon if set, else the web glyph. */
    private fun iconHolder(key: String, sizeDp: Float, onClick: () -> Unit): View {
        val holder = FrameLayout(this).apply {
            background = Roost.rounded(Roost.TILE, dp(11f).toFloat(), Roost.HAIRLINE, dp(1f))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { rightMargin = dp(13f) }
        }
        val override: Drawable? = Prefs.iconOverride(this, key)?.let { IconStore.drawableFor(this, it) }
        holder.addView(ImageView(this).apply {
            if (override != null) {
                setImageDrawable(override)
            } else {
                setImageResource(R.drawable.ic_web); setColorFilter(accent)
            }
            val p = dp(9f); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        return holder
    }

    private fun openIconPicker(key: String) {
        startActivity(Intent(this, IconPickerActivity::class.java)
            .putExtra(IconPickerActivity.EXTRA_KEY, key))
    }
}
