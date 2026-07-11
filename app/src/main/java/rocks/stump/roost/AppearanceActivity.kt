package rocks.stump.roost

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Appearance (ADR-0005): the accent-tint swatch chooser (Honey / Slate / Sage / Violet) and the
 * one-shot "Match wallpaper to Roost" action.
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

        // --- Wallpaper ---
        body.addView(sectionHeader("Wallpaper"))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(14f), dp(12f))
        }
        // A little gradient-preview chip.
        row.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Roost.soft(accent), Roost.DOCK)
            ).apply { cornerRadius = dp(11f).toFloat() }
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { rightMargin = dp(13f) }
        })
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        stack.addView(TextView(this).apply {
            text = getString(R.string.settings_wallpaper_apply); setTextColor(ROW_LABEL); textSize = 14.5f
        })
        stack.addView(TextView(this).apply {
            text = "Set the system wallpaper to the dock gradient"
            setTextColor(SUBTLE); textSize = 11.5f; setPadding(0, dp(2f), 0, 0)
        })
        row.addView(stack)
        row.addView(accentPill("Apply") { applyWallpaper() })
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
            card.addView(TextView(this).apply {
                text = name
                setTextColor(if (on) Roost.TEXT else SUBTLE)
                textSize = 11f
                setPadding(0, dp(8f), 0, 0)
            })
            row.addView(card)
        }
        return row
    }

    private fun applyWallpaper() {
        val ok = Roost.applyWallpaper(this)
        Prefs.setWallpaperApplied(this, true)
        Toast.makeText(this, if (ok) R.string.wallpaper_set else R.string.wallpaper_failed, Toast.LENGTH_SHORT).show()
    }
}
