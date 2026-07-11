package rocks.stump.roost

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
 * Governing: ADR-0005 (settings navigation IA), ADR-0003 (icon rendering strategy)
 */
class WebAppsActivity : SettingsScreen() {

    override fun screenTitle(): String = "Web apps"

    override fun buildContent(body: LinearLayout) {
        body.addView(hint("Pin a URL as a fullscreen tile on the home grid.")
            .apply { setPadding(dp(4f), dp(4f), dp(4f), dp(12f)) })

        val apps = Prefs.webApps(this)
        if (apps.isNotEmpty()) {
            body.addView(card(apps.map { wa -> webAppRow(wa) }))
            body.addView(gap(dp(20f)))
        }

        body.addView(sectionHeader("Add web app", firstOnScreen = apps.isEmpty()))

        // "Choose icon" — picks a custom tile icon as part of adding (keyed to the URL you enter).
        val name = plainField(getString(R.string.webapp_name_hint))
        val url = plainField(getString(R.string.webapp_url_hint), uri = true, mono = true)

        body.addView(chooseIconRow { url.text.toString() })
        body.addView(gap(dp(9f)))
        body.addView(name)
        body.addView(gap(dp(9f)))
        body.addView(url)
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
                        Prefs.addWebApp(this@WebAppsActivity, name.text.toString().trim(), u)
                        rebuild()
                    }
                }
            })
        })
    }

    /** Refresh so a freshly-picked icon shows on return from the icon picker. */
    override fun onResume() {
        super.onResume()
        rebuild()
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

    /** The add-form "Choose icon" row: commits the entered URL then opens the picker keyed to it. */
    private fun chooseIconRow(currentUrl: () -> String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(rowBg(), dp(13f).toFloat(), rowBorder(), dp(1f))
            setPadding(dp(12f), dp(9f), dp(14f), dp(9f))
            isClickable = true
            setOnClickListener {
                val u = normalizeUrl(currentUrl())
                if (u.isEmpty()) {
                    Toast.makeText(this@WebAppsActivity, "Enter a URL first, then choose its icon.", Toast.LENGTH_SHORT).show()
                } else {
                    // Ensure the web app exists so the override keys onto a real tile, then pick.
                    if (Prefs.webApps(this@WebAppsActivity).none { it.url == u }) {
                        Prefs.addWebApp(this@WebAppsActivity, "", u)
                    }
                    openIconPicker("web:$u")
                }
            }
        }
        val holder = FrameLayout(this).apply {
            background = Roost.rounded(Roost.TILE, dp(11f).toFloat(), Roost.HAIRLINE, dp(1f))
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f)).apply { rightMargin = dp(12f) }
        }
        holder.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_web)
            setColorFilter(accent)
            val p = dp(8f); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        row.addView(holder)
        row.addView(TextView(this).apply {
            text = "Choose icon"
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
