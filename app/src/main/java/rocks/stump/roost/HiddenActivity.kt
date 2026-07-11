package rocks.stump.roost

import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Hidden items (ADR-0005): restore tiles the owner hid via the home long-press menu. Reads Prefs.hidden
 * and clears the hidden flag per item.
 *
 * Governing: ADR-0005 (settings navigation IA)
 */
class HiddenActivity : SettingsScreen() {

    override fun screenTitle(): String = "Hidden items"

    override fun buildContent(body: LinearLayout) {
        body.addView(hint("Tiles you hid from the home long-press menu. Restore any to the grid.")
            .apply { setPadding(dp(4f), dp(4f), dp(4f), dp(12f)) })

        val hidden = Prefs.hiddenItems(this).toList()
        if (hidden.isEmpty()) {
            body.addView(TextView(this).apply {
                text = "Nothing hidden."
                setTextColor(SUBTLE); textSize = 13.5f
                setPadding(dp(4f), dp(4f), 0, 0)
            })
            return
        }
        body.addView(card(hidden.map { key -> hiddenRow(key) }))
    }

    private fun hiddenRow(key: String): View {
        val label = hiddenLabel(key)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(14f), dp(12f))
        }
        val icon = appDrawable(key)
        val holder = FrameLayout(this).apply {
            background = Roost.rounded(Roost.TILE, dp(11f).toFloat(), Roost.HAIRLINE, dp(1f))
            layoutParams = LinearLayout.LayoutParams(dp(38f), dp(38f)).apply { rightMargin = dp(13f) }
        }
        if (icon != null) {
            holder.addView(ImageView(this).apply {
                setImageDrawable(icon)
                val p = dp(7f); setPadding(p, p, p, p)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            })
        } else {
            holder.addView(TextView(this).apply {
                text = label.take(1).uppercase()
                setTextColor(SUBTLE); textSize = 16f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            })
        }
        row.addView(holder)
        row.addView(TextView(this).apply {
            text = label
            setTextColor(0xFFC9C0B2.toInt()); textSize = 14.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = getString(R.string.tile_restore)
            setTextColor(accent); textSize = 12.5f
            background = Roost.rounded(0, dp(20f).toFloat(), Roost.soft(accent), dp(1f))
            setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
            isClickable = true
            setOnClickListener { Prefs.setHidden(this@HiddenActivity, key, false); rebuild() }
        })
        return row
    }

    private fun hiddenLabel(key: String): String = when {
        key == "agent" -> appLabel(Prefs.agentPkg(this)) ?: "Agent"
        key.startsWith("app:") -> appLabel(key.removePrefix("app:")) ?: key.removePrefix("app:")
        key.startsWith("web:") -> {
            val url = key.removePrefix("web:")
            Prefs.webApps(this).find { it.url == url }?.name ?: url
        }
        else -> Prefs.actionButtons(this).find { it.key == key }?.title
            ?: key.substringAfterLast(":").ifBlank { key }
    }

    private fun appDrawable(key: String): Drawable? {
        val pkg = when {
            key == "agent" -> Prefs.agentPkg(this)
            key.startsWith("app:") -> key.removePrefix("app:")
            else -> return null
        }
        return try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }
    }

    private fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { null }
}
