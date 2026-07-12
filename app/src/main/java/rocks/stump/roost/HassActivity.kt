package rocks.stump.roost

import android.graphics.Typeface
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
 * Home Assistant detail screen (split out of the former combined ActionsActivity — owner feedback). The
 * connected accounts as status cards, each account's scenes as toggle-chips (tap to load, tap a chip to
 * enable/disable it as a button), plus an add-account form. HASS scenes remain a first-class provider
 * (ADR-0002); a scene toggled on becomes an ActionButton and appears in the home Actions zone.
 *
 * Governing: ADR-0005 (settings navigation IA), ADR-0002 (pluggable action-button providers),
 * ADR-0001 (framework-only)
 */
class HassActivity : SettingsScreen() {

    private val CLAY = 0xFFCF6B5A.toInt()

    override fun screenTitle(): String = "Home Assistant"

    override fun buildContent(body: LinearLayout) {
        body.addView(sectionHeader("Home Assistant", firstOnScreen = true))
        for (acct in Prefs.hassAccounts(this)) {
            hassAccountViews(acct).forEach { body.addView(it) }
        }
        // Add-account form.
        val haName = plainField(getString(R.string.hass_name_hint))
        val haUrl = plainField(getString(R.string.hass_url_hint), uri = true, mono = true)
        val haToken = plainField(getString(R.string.hass_token_hint))
        body.addView(gap(dp(4f)))
        body.addView(haName); body.addView(gap(dp(9f)))
        body.addView(haUrl); body.addView(gap(dp(9f)))
        body.addView(haToken); body.addView(gap(dp(12f)))
        body.addView(addAccountRow(haName, haUrl, haToken))
    }

    private fun hassAccountViews(acct: HassAccount): List<View> {
        val scenesBox = FlowLayout(this, dp(8f), dp(8f)).apply {
            setPadding(dp(2f), dp(12f), dp(2f), 0)
        }
        val on = Prefs.actionButtons(this).count { it.kind == ActionKind.HASS_SCENE && it.a == acct.id }
        val accountCard = card(
            hassStatusRow(acct),
            navRow(null, "Scenes shown as buttons", "Tap to load and toggle scenes", "$on on") {
                loadScenes(acct, scenesBox)
            },
            removeAccountRow(acct)
        )
        return listOf(accountCard, scenesBox, gap(dp(14f)))
    }

    private fun hassStatusRow(acct: HassAccount): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        row.addView(tileIcon(R.drawable.ic_scene, 34f))
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        stack.addView(TextView(this).apply {
            text = acct.name.ifBlank { acct.url }; setTextColor(Roost.TEXT); textSize = 14.5f; maxLines = 1
        })
        stack.addView(TextView(this).apply {
            text = acct.url; setTextColor(SUBTLE); textSize = 10.5f; typeface = Typeface.MONOSPACE
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(2f), 0, 0)
        })
        val last4 = if (acct.token.length >= 4) acct.token.takeLast(4) else "••••"
        stack.addView(TextView(this).apply {
            text = "token •••• $last4"
            setTextColor(SECTION); textSize = 10f; typeface = Typeface.MONOSPACE
            setPadding(0, dp(3f), 0, 0)
        })
        row.addView(stack)
        return row
    }

    private fun removeAccountRow(acct: HassAccount): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
            isClickable = true
            setOnClickListener { Prefs.removeHassAccount(this@HassActivity, acct.id); rebuild() }
        }
        row.addView(TextView(this).apply {
            text = "Remove account"; setTextColor(CLAY); textSize = 13.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_trash); setColorFilter(CLAY)
            val p = dp(3f); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f))
        })
        return row
    }

    private fun addAccountRow(name: EditText, url: EditText, token: EditText): View =
        LinearLayout(this).apply {
            addView(TextView(this@HassActivity).apply {
                text = "+  " + getString(R.string.hass_add)
                setTextColor(accent); textSize = 13f
                background = Roost.rounded(Roost.soft(accent), dp(12f).toFloat())
                setPadding(dp(18f), dp(11f), dp(18f), dp(11f))
                isClickable = true
                setOnClickListener {
                    val u = normalizeUrl(url.text.toString())
                    val tok = token.text.toString().trim()
                    if (u.isNotEmpty() && tok.isNotEmpty()) {
                        Prefs.addHassAccount(this@HassActivity, name.text.toString().trim(), u, tok)
                        rebuild()
                    } else {
                        Toast.makeText(this@HassActivity, getString(R.string.hass_need_url_token), Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }

    private fun loadScenes(acct: HassAccount, box: ViewGroup) {
        box.removeAllViews()
        box.addView(hint(getString(R.string.actions_loading)))
        Thread {
            val result = runCatching { Hass.scenes(acct) }
            runOnUiThread {
                box.removeAllViews()
                result.onSuccess { scenes ->
                    if (scenes.isEmpty()) {
                        box.addView(hint(getString(R.string.actions_no_scenes)))
                    } else {
                        for ((eid, sceneName) in scenes) {
                            val button = ActionButton(ActionKind.HASS_SCENE, "hass:${acct.id}:$eid", sceneName, acct.id, eid)
                            box.addView(sceneChip(sceneName, Prefs.isActionEnabled(this, button.key)) { checked ->
                                Prefs.setActionEnabled(this, button, checked)
                            })
                        }
                    }
                }.onFailure {
                    box.addView(hint(getString(R.string.actions_hass_error)))
                }
            }
        }.start()
    }

    /** A HASS scene as a toggle-chip: dot + name, accent when enabled as a button. */
    private fun sceneChip(name: String, initial: Boolean, onToggle: (Boolean) -> Unit): View {
        var on = initial
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(7f), dp(7f)).apply { rightMargin = dp(7f) }
        }
        val label = TextView(this).apply { text = name; textSize = 12.5f }
        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13f), dp(8f), dp(13f), dp(8f))
            isClickable = true
            addView(dot); addView(label)
        }
        fun style() {
            chip.background = Roost.rounded(
                if (on) Roost.soft(accent) else inputBg(), dp(20f).toFloat(),
                if (on) Roost.withAlpha(accent, 0x66) else rowBorder(), dp(1f))
            label.setTextColor(if (on) accent else Roost.MUTED)
            dot.background = Roost.rounded(if (on) accent else Roost.MUTED, dp(4f).toFloat())
        }
        style()
        chip.setOnClickListener { on = !on; style(); onToggle(on) }
        return chip
    }

    private fun tileIcon(res: Int, sizeDp: Float): View {
        val holder = FrameLayout(this).apply {
            background = Roost.rounded(Roost.TILE, dp(10f).toFloat(), Roost.HAIRLINE, dp(1f))
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { rightMargin = dp(12f) }
        }
        holder.addView(ImageView(this).apply {
            setImageResource(res); setColorFilter(accent)
            val p = dp(sizeDp * 0.24f); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        return holder
    }
}
