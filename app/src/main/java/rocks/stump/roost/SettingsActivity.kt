package rocks.stump.roost

import android.content.Intent
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Settings LANDING (ADR-0005): a short list of category rows, each opening its own detail Activity via
 * the system back stack. Replaces the old monolithic scroll — no capability was dropped, only regrouped
 * (every former control now lives behind one of these categories, mapped 1:1 in the ADR's category tree).
 *
 * Governing: ADR-0005 (settings navigation IA)
 */
class SettingsActivity : SettingsScreen() {

    override fun screenTitle(): String = "Settings"

    override fun buildContent(body: LinearLayout) {
        body.addView(deviceStrip())
        body.addView(gap(dp(20f)))

        // Home & behavior → BehaviorActivity
        val modeLabel = if (Prefs.mode(this) == Prefs.MODE_APPLIANCE) "Boot-direct" else "Curated"
        body.addView(categoryRow(
            R.drawable.ic_cat_home, "Home & behavior",
            "$modeLabel · auto-launch · screen on"
        ) { open(BehaviorActivity::class.java) })

        // Agent → AgentActivity
        val agentName = Prefs.agentName(this).trim().ifEmpty { "unnamed" }
        val featured = appLabel(Prefs.agentPkg(this)) ?: "not set"
        body.addView(categoryRow(
            R.drawable.ic_cat_agent, "Agent",
            "$agentName · $featured"
        ) { open(AgentActivity::class.java) })

        // Appearance → AppearanceActivity
        body.addView(categoryRow(
            R.drawable.ic_cat_appearance, "Appearance",
            "Accent tint · wallpaper"
        ) { open(AppearanceActivity::class.java) })

        // Apps, tiles & content → AppsActivity
        body.addView(categoryRow(
            R.drawable.ic_cat_grid, "Apps, tiles & content",
            "Favorites · web apps · actions · hidden"
        ) { open(AppsActivity::class.java) })

        // Network → NetworkActivity
        val tunnel = Prefs.wireguardTunnel(this).trim().ifEmpty { "no tunnel" }
        body.addView(categoryRow(
            R.drawable.ic_cat_network, "Network",
            "WireGuard · $tunnel"
        ) { open(NetworkActivity::class.java) })

        // Open Android system settings
        body.addView(openAndroidRow())
    }

    private fun deviceStrip(): View {
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(Roost.soft(accent), dp(18f).toFloat(), Roost.soft(accent), dp(1f))
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        strip.addView(FrameLayout(this).apply {
            background = Roost.rounded(Roost.TILE, dp(14f).toFloat(), Roost.soft(accent), dp(1f))
            layoutParams = LinearLayout.LayoutParams(dp(44f), dp(40f)).apply { rightMargin = dp(13f) }
            addView(ImageView(this@SettingsActivity).apply {
                setImageResource(R.drawable.ic_cat_agent)
                setColorFilter(accent)
                val p = dp(10f); setPadding(p, p, p, p)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            })
        })
        val agentName = Prefs.agentName(this).trim().ifEmpty { "This" }
        val stack = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stack.addView(TextView(this).apply {
            text = "$agentName's device"
            setTextColor(Roost.TEXT)
            textSize = 15f
            typeface = Roost.medium()
        })
        stack.addView(TextView(this).apply {
            // Show the app version, not the package name (owner feedback). (Fix 6.)
            text = "Roost · ${BuildConfig.VERSION_NAME}"
            setTextColor(SUBTLE)
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(2f), 0, 0)
        })
        strip.addView(stack)
        return strip
    }

    private fun openAndroidRow(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(15f), dp(15f), dp(15f), dp(15f))
        isClickable = true
        setOnClickListener { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        addView(ImageView(this@SettingsActivity).apply {
            setImageResource(R.drawable.ic_external)
            setColorFilter(Roost.MUTED)
            layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f)).apply { rightMargin = dp(10f) }
        })
        addView(TextView(this@SettingsActivity).apply {
            text = getString(R.string.open_android_settings_full)
            setTextColor(Roost.MUTED)
            textSize = 13.5f
        })
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    private fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { null }

    override fun onResume() {
        super.onResume()
        // Category subtitles reflect live state (mode, agent, tunnel) — repaint on return.
        rebuild()
    }
}
