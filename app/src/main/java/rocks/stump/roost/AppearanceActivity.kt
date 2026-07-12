package rocks.stump.roost

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Appearance (ADR-0005): the accent-tint swatch chooser (Honey / Slate / Sage / Violet), the Actions
 * zone density, and the wallpaper. Choosing an accent auto-repaints the matching dock wallpaper, so
 * there is no separate "apply wallpaper" step.
 *
 * Governing: ADR-0005 (settings navigation IA)
 */
class AppearanceActivity : SettingsScreen() {

    override fun screenTitle(): String = "Appearance"

    override fun buildContent(body: LinearLayout) {
        // --- Accent tint ---
        body.addView(sectionHeader("Accent tint", firstOnScreen = true))
        body.addView(swatchRow())
        body.addView(hint("Tints the mascot, highlights and controls across Roost. Match it to your model."))

        // --- Action density (one setting for the whole home Actions zone — SPEC-0002) ---
        body.addView(sectionHeader("Action density"))
        val densities = listOf(ActionDensity.SLIM, ActionDensity.REGULAR, ActionDensity.RICH)
        val selected = densities.indexOf(Prefs.actionDensity(this)).coerceAtLeast(0)
        body.addView(segmented(listOf("Slim", "Regular", "Rich"), selected) { i ->
            Prefs.setActionDensity(this, densities[i])
            rebuild()
        })
        body.addView(hint("How the home renders its tiles."))

        // --- Launcher filters (which type-filter chips the home offers above the tiles) — ADR-0007 ---
        // One toggle per ActionKind: ON = its chip is offered on the launcher (when tiles of that kind
        // exist), OFF = the chip is suppressed. Bound to Prefs.hiddenFilterKinds (on = NOT hidden).
        body.addView(sectionHeader("Launcher filters"))
        val filterKinds = listOf(
            ActionKind.APP, ActionKind.WEB, ActionKind.SHORTCUT, ActionKind.HTTP, ActionKind.HASS_SCENE
        )
        val hiddenKinds = Prefs.hiddenFilterKinds(this)
        body.addView(card(filterKinds.map { kind ->
            toggleRow(kind.filterLabel(), "", kind.name !in hiddenKinds) { checked ->
                Prefs.setFilterKindHidden(this, kind, !checked)
            }
        }))
        body.addView(hint("Choose which type filters can appear above your tiles on the home screen."))

        // --- Wallpaper ---
        // No manual "apply" button anymore: choosing an accent above already repaints the matching dock
        // wallpaper (see swatchRow). Keep just a gradient preview + a muted "it's automatic" note. (Fix 5.)
        body.addView(sectionHeader("Wallpaper"))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(14f), dp(12f))
        }
        // A little gradient-preview chip in the current accent.
        row.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Roost.soft(accent), Roost.DOCK)
            ).apply { cornerRadius = dp(11f).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { rightMargin = dp(13f) }
        })
        row.addView(TextView(this).apply {
            text = "Your wallpaper matches your theme — it updates automatically when you change the accent."
            setTextColor(SUBTLE); textSize = 12f; setLineSpacing(0f, 1.25f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        body.addView(card(row))
    }

    private fun swatchRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        Roost.ACCENTS.forEachIndexed { i, (name, color) ->
            val on = color == accent
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                background = Roost.rounded(
                    if (on) Roost.soft(color) else Roost.withAlpha(0xFFFFFFFF.toInt(), 0x08),
                    dp(14f).toFloat(),
                    if (on) Roost.withAlpha(color, 0x66) else rowBorder(), dp(1f)
                )
                setPadding(0, dp(12f), 0, dp(12f))
                isClickable = true
                setOnClickListener {
                    Prefs.setAccent(this@AppearanceActivity, color)
                    // The matching wallpaper is a given — repaint the dock gradient for the new accent
                    // right away rather than making it a separate manual step. (Fix 5.)
                    Roost.applyWallpaper(this@AppearanceActivity)
                    Prefs.setWallpaperApplied(this@AppearanceActivity, true)
                    rebuild()
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (i > 0) leftMargin = dp(10f) }
            }
            // The colored dot, with a selection ring when active.
            card.addView(FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (on) setStroke(dp(2f), color)
                }
                layoutParams = LinearLayout.LayoutParams(dp(34f), dp(34f))
            })
            // Full-width, center-gravity label so the name sits centered under its circle. (Fix 4.)
            card.addView(TextView(this).apply {
                text = name
                setTextColor(if (on) Roost.TEXT else SUBTLE)
                textSize = 11f
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(8f), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
            row.addView(card)
        }
        return row
    }
}
