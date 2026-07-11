package rocks.stump.roost

import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Network (ADR-0005): the WireGuard tunnel name for the one-tap VPN toggle, plus the "Allow remote
 * control apps" note. Reads and writes Prefs.wireguardTunnel.
 *
 * Governing: ADR-0005 (settings navigation IA)
 */
class NetworkActivity : SettingsScreen() {

    override fun screenTitle(): String = "Network"

    override fun buildContent(body: LinearLayout) {
        body.addView(sectionHeader("WireGuard tunnel", firstOnScreen = true))
        body.addView(textInput(
            Prefs.wireguardTunnel(this),
            getString(R.string.wg_tunnel_hint)
        ) { Prefs.setWireguardTunnel(this, it) })
        body.addView(hint("The tunnel the home one-tap VPN button toggles. Leave blank to just open WireGuard."))

        // The "Allow remote control apps" note, styled as an accent callout.
        body.addView(gap(dp(16f)))
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Roost.rounded(Roost.soft(accent), dp(16f).toFloat(), Roost.soft(accent), dp(1f))
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
        }
        note.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_cat_network)
            setColorFilter(accent)
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)).apply { rightMargin = dp(10f) }
        })
        note.addView(TextView(this).apply {
            text = getString(R.string.wg_note)
            setTextColor(0xFFC9C0B2.toInt()); textSize = 12f; setLineSpacing(0f, 1.3f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        body.addView(note)
    }
}
