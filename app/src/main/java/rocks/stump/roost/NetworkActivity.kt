package rocks.stump.roost

import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Network (ADR-0005): clarifies the two separate things about the home VPN chip — (1) the chip shows
 * live VPN status + up/down rates automatically, with no config here; (2) this screen only sets which
 * WireGuard tunnel a TAP on that chip toggles. Reads and writes Prefs.wireguardTunnel.
 *
 * Governing: ADR-0005 (settings navigation IA)
 */
class NetworkActivity : SettingsScreen() {

    override fun screenTitle(): String = "Network"

    override fun buildContent(body: LinearLayout) {
        // First make clear the chip needs no setup — it auto-detects the VPN and shows rates. Everything
        // below is only about what tapping the chip does. (Fix 7: owner was confused by "a VPN button".)
        body.addView(sectionHeader("Home VPN chip", firstOnScreen = true))
        body.addView(hint(
            "The VPN chip on the home screen already shows live VPN status and up/down rates on its own — " +
            "nothing to set up. The field below only changes what tapping it does."
        ))

        body.addView(sectionHeader("Tunnel toggled on tap"))
        body.addView(textInput(
            Prefs.wireguardTunnel(this),
            getString(R.string.wg_tunnel_hint)
        ) { Prefs.setWireguardTunnel(this, it) })
        body.addView(hint(
            "Tapping the VPN chip toggles this named WireGuard tunnel on/off (needs WireGuard's " +
            "\"Allow remote control apps\"). Leave blank and a tap just opens the WireGuard app."
        ))

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
