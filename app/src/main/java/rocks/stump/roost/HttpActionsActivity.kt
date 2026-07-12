package rocks.stump.roost

import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * HTTP actions detail screen (split out of the former combined ActionsActivity — owner feedback). The
 * saved HTTP actions (ADR-0004) as a card of rows: each shows its resolved icon override + "METHOD · host"
 * and taps into [HttpActionActivity] to edit, followed by a "New action → builder" row into
 * [EndpointsActivity]. Toggling an action off / deleting it happens on its home tile or in the builder.
 *
 * Governing: ADR-0005 (settings navigation IA), ADR-0004 (generalized HTTP-action provider),
 * ADR-0001 (framework-only)
 */
class HttpActionsActivity : SettingsScreen() {

    override fun screenTitle(): String = "HTTP Actions"

    // Rebuild on return so a newly-added/edited HTTP action (saved in the builder, which finishes back to
    // here) or a fresh icon override shows immediately. Skip the first onResume — onCreate already built —
    // so we don't build twice on entry.
    private var builtOnce = false

    override fun onResume() {
        super.onResume()
        if (builtOnce) rebuild() else builtOnce = true
    }

    override fun buildContent(body: LinearLayout) {
        body.addView(sectionHeader("HTTP actions", firstOnScreen = true))
        val httpButtons = Prefs.actionButtons(this).filter { it.kind == ActionKind.HTTP }
        val httpRows = mutableListOf<View>()
        for (b in httpButtons) {
            val act = Prefs.httpAction(this, b.a)
            val sub = if (act != null) "${act.method} · ${HttpActionClient.hostOf(act.url)}" else "HTTP action"
            val open = {
                startActivity(Intent(this, HttpActionActivity::class.java)
                    .putExtra(HttpActionActivity.EXTRA_ID, b.a))
            }
            // Honour a chosen icon override (e.g. Jellyfin) here too — the home already resolves
            // overrideIcon(b.key); Settings must match instead of always showing the ic_scene sparkle.
            val override = Prefs.iconOverride(this, b.key)?.let { IconStore.drawableFor(this, it) }
            httpRows.add(
                if (override != null) navRow(override, b.title, sub) { open() }
                else navRow(R.drawable.ic_scene, b.title, sub) { open() }
            )
        }
        httpRows.add(newActionRow())
        body.addView(card(httpRows))
        body.addView(hint("Every “POST something and show if it worked” button — Home Assistant scenes included — is one saved HTTP action."))
    }

    private fun newActionRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            isClickable = true
            setOnClickListener { startActivity(Intent(this@HttpActionsActivity, EndpointsActivity::class.java)) }
        }
        val holder = FrameLayout(this).apply {
            background = Roost.rounded(Roost.soft(accent), dp(10f).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f)).apply { rightMargin = dp(12f) }
        }
        holder.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_plus); setColorFilter(accent)
            val p = dp(8f); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        row.addView(holder)
        row.addView(TextView(this).apply {
            text = "New Action"; setTextColor(accent); textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = "builder"; setTextColor(SUBTLE); textSize = 10.5f; typeface = Typeface.MONOSPACE
        })
        return row
    }
}
