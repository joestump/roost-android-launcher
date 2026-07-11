package rocks.stump.roost

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Manage "action buttons" — pluggable, per-provider. Two providers today: Android app-shortcuts and
 * Home Assistant scenes. The user connects accounts / scans, then ticks which items become buttons.
 */
class ActionsActivity : Activity() {

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private val accent: Int get() = Prefs.accent(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(28f), dp(22f), dp(44f))
        }

        col.addView(title(getString(R.string.actions_title)))

        // --- Enabled buttons (review / remove) ---
        col.addView(header(getString(R.string.actions_enabled)))
        val enabled = Prefs.actionButtons(this)
        if (enabled.isEmpty()) {
            col.addView(muted(getString(R.string.actions_none)))
        } else {
            for (b in enabled) {
                col.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6f), 0, dp(6f))
                    addView(TextView(this@ActionsActivity).apply {
                        text = b.title
                        setTextColor(Roost.TEXT)
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(Button(this@ActionsActivity).apply {
                        text = getString(R.string.remove)
                        setOnClickListener {
                            Prefs.setActionEnabled(this@ActionsActivity, b, false)
                            recreate()
                        }
                    })
                })
            }
        }

        // --- Home Assistant ---
        col.addView(header(getString(R.string.actions_hass)))
        for (acct in Prefs.hassAccounts(this)) {
            col.addView(TextView(this).apply {
                text = "${acct.name}\n${acct.url}"
                setTextColor(Roost.TEXT)
                textSize = 14f
                setPadding(0, dp(8f), 0, dp(2f))
            })
            val scenesBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4f), 0, 0, 0)
            }
            col.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(this@ActionsActivity).apply {
                    text = getString(R.string.actions_load_scenes)
                    setOnClickListener { loadScenes(acct, scenesBox, this) }
                })
                addView(Button(this@ActionsActivity).apply {
                    text = getString(R.string.remove)
                    setOnClickListener {
                        Prefs.removeHassAccount(this@ActionsActivity, acct.id)
                        recreate()
                    }
                })
            })
            col.addView(scenesBox)
        }

        // Add-account form
        val haName = edit(getString(R.string.hass_name_hint), false)
        val haUrl = edit(getString(R.string.hass_url_hint), true)
        val haToken = edit(getString(R.string.hass_token_hint), false)
        col.addView(haName); col.addView(haUrl); col.addView(haToken)
        col.addView(Button(this).apply {
            text = getString(R.string.hass_add)
            setOnClickListener {
                val url = normalizeUrl(haUrl.text.toString())
                val token = haToken.text.toString().trim()
                if (url.isNotEmpty() && token.isNotEmpty()) {
                    Prefs.addHassAccount(this@ActionsActivity, haName.text.toString().trim(), url, token)
                    recreate()
                } else {
                    toast(getString(R.string.hass_need_url_token))
                }
            }
        })

        // --- App shortcuts ---
        col.addView(header(getString(R.string.actions_shortcuts)))
        val shortcutsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(Button(this).apply {
            text = getString(R.string.actions_scan)
            setOnClickListener { scanShortcuts(shortcutsBox, this) }
        })
        col.addView(shortcutsBox)

        setContentView(ScrollView(this).apply {
            background = Roost.dockBackground(this@ActionsActivity)
            addView(col)
        })
    }

    // --- Home Assistant scene loading ---

    private fun loadScenes(acct: HassAccount, box: LinearLayout, btn: Button) {
        btn.isEnabled = false
        btn.text = getString(R.string.actions_loading)
        Thread {
            val result = runCatching { Hass.scenes(acct) }
            runOnUiThread {
                btn.isEnabled = true
                btn.text = getString(R.string.actions_load_scenes)
                box.removeAllViews()
                result.onSuccess { scenes ->
                    if (scenes.isEmpty()) box.addView(muted(getString(R.string.actions_no_scenes)))
                    for ((eid, name) in scenes) {
                        val button = ActionButton(ActionKind.HASS_SCENE, "hass:${acct.id}:$eid", name, acct.id, eid)
                        box.addView(checkRow(name, eid, Prefs.isActionEnabled(this, button.key)) { on ->
                            Prefs.setActionEnabled(this, button, on)
                        })
                    }
                }.onFailure {
                    box.addView(muted(getString(R.string.actions_hass_error)))
                }
            }
        }.start()
    }

    // --- Shortcut scanning ---

    private fun scanShortcuts(box: LinearLayout, btn: Button) {
        btn.isEnabled = false
        btn.text = getString(R.string.actions_scanning)
        Thread {
            val items = runCatching { ShortcutProvider.scanAll(this) }.getOrDefault(emptyList())
            runOnUiThread {
                btn.isEnabled = true
                btn.text = getString(R.string.actions_scan)
                box.removeAllViews()
                if (items.isEmpty()) {
                    box.addView(muted(getString(R.string.actions_no_shortcuts)))
                } else {
                    for (item in items) {
                        val button = item.toButton()
                        box.addView(checkRow(item.label, item.appLabel, Prefs.isActionEnabled(this, button.key)) { on ->
                            Prefs.setActionEnabled(this, button, on)
                        })
                    }
                }
            }
        }.start()
    }

    // --- Small UI helpers ---

    private fun checkRow(label: String, sub: String?, enabled: Boolean, onToggle: (Boolean) -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5f), 0, dp(5f))
            addView(CheckBox(this@ActionsActivity).apply {
                isChecked = enabled
                buttonTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(accent, Roost.MUTED)
                )
                setOnCheckedChangeListener { _, c -> onToggle(c) }
            })
            addView(TextView(this@ActionsActivity).apply {
                text = if (sub.isNullOrBlank()) label else "$label\n$sub"
                setTextColor(Roost.TEXT)
                textSize = 14f
            })
        }

    private fun title(t: String) = TextView(this).apply {
        text = t; setTextColor(Roost.TEXT); textSize = 22f; typeface = Roost.medium()
        setPadding(0, 0, 0, dp(6f))
    }

    private fun header(t: String) = TextView(this).apply {
        text = t.uppercase(); setTextColor(accent); textSize = 12f; letterSpacing = 0.08f
        typeface = Roost.medium(); setPadding(0, dp(24f), 0, dp(8f))
    }

    private fun muted(t: String) = TextView(this).apply {
        text = t; setTextColor(Roost.MUTED); textSize = 13f; setPadding(0, dp(4f), 0, dp(4f))
    }

    private fun edit(hintText: String, uri: Boolean) = EditText(this).apply {
        hint = hintText
        inputType = if (uri) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        else InputType.TYPE_CLASS_TEXT
        setTextColor(Roost.TEXT); setHintTextColor(Roost.MUTED)
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    private fun normalizeUrl(raw: String): String {
        val u = raw.trim()
        return when {
            u.isEmpty() -> ""
            u.startsWith("http://") || u.startsWith("https://") -> u
            else -> "https://$u"
        }
    }
}
