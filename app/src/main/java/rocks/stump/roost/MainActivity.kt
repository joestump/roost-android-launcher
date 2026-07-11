package rocks.stump.roost

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.BatteryManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The HOME surface, styled per the "Roost" design.
 *
 *  - Curated  : the robot mascot + greeting + featured agent card + utility grid.
 *  - Appliance: an ambient "at rest" face (mascot + greeting); long-press reveals the grid.
 *
 * Boot auto-launch (both modes) is driven by a one-shot flag armed in BootReceiver and consumed
 * here, so the actual startActivity() runs from the foreground HOME activity (allowed).
 */
class MainActivity : Activity() {

    private var applianceRevealed = false

    private val accent: Int get() = Prefs.accent(this)
    private fun dp(v: Float): Int = Roost.dp(this, v)

    override fun onResume() {
        super.onResume()
        applyScreenOn()

        // First run: paint a matching wallpaper so Recents/transitions share Roost's look.
        if (!Prefs.wallpaperApplied(this)) {
            Roost.applyWallpaper(this)
            Prefs.setWallpaperApplied(this, true)
        }

        if (Prefs.pendingBootLaunch(this)) {
            Prefs.setPendingBootLaunch(this, false)
            if (launchAgent()) return
        }
        render()
    }

    override fun onStop() {
        super.onStop()
        applianceRevealed = false
    }

    override fun onBackPressed() { /* home is a dead end */ }

    private fun applyScreenOn() {
        if (Prefs.keepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun render() {
        val appliance = Prefs.mode(this) == Prefs.MODE_APPLIANCE
        if (appliance && !applianceRevealed) renderAmbient() else renderHome()
    }

    // --- Curated home -----------------------------------------------------------------------

    private fun renderHome() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22f), dp(46f), dp(22f), dp(28f))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        col.addView(mascot(dp(128f)))
        col.addView(greetingView())
        col.addView(statusView())
        col.addView(spacer(dp(22f)))
        col.addView(featuredCard())
        col.addView(spacer(dp(22f)))
        col.addView(utilityGrid())
        col.addView(appsSettingsLink(dp(26f)))

        setContentView(ScrollView(this).apply {
            background = Roost.dockBackground(this@MainActivity)
            isFillViewport = true
            addView(col)
        })
    }

    // --- Appliance "at rest" face -----------------------------------------------------------

    private fun renderAmbient() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32f), dp(32f), dp(32f), dp(32f))
        }
        col.addView(mascot(dp(150f)))
        col.addView(greetingView())
        col.addView(statusView())
        col.addView(TextView(this).apply {
            text = getString(R.string.appliance_hint)
            setTextColor(Roost.MUTED)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, dp(28f), 0, 0)
        })

        val root = FrameLayout(this).apply {
            background = Roost.dockBackground(this@MainActivity)
            isLongClickable = true
            setOnLongClickListener {
                applianceRevealed = true
                renderHome()
                true
            }
            addView(
                col,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER }
            )
        }
        setContentView(root)
    }

    // --- Pieces -----------------------------------------------------------------------------

    private fun mascot(sizePx: Int): MascotView = MascotView(this).apply {
        accent = this@MainActivity.accent
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    private fun greetingView(): TextView = TextView(this).apply {
        text = getString(R.string.greeting_home)
        setTextColor(Roost.TEXT)
        textSize = 21f
        gravity = Gravity.CENTER
        setPadding(0, dp(10f), 0, 0)
    }

    private fun statusView(): TextView = TextView(this).apply {
        text = statusLine()
        setTextColor(Roost.MUTED)
        textSize = 12f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(0, dp(6f), 0, 0)
    }

    private fun statusLine(): String {
        val (pct, charging) = battery()
        val word = if (charging) "docked & charging" else "on battery"
        return if (pct >= 0) "roost · $word · $pct%" else "roost · $word"
    }

    private fun battery(): Pair<Int, Boolean> {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return pct to (plugged != 0)
    }

    private fun featuredCard(): View {
        val pkg = Prefs.agentPkg(this)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(Roost.PANEL, dp(18f).toFloat(), Roost.HAIRLINE, dp(1f))
            setPadding(dp(15f), dp(15f), dp(15f), dp(15f))
            elevation = dp(2f).toFloat()
            isClickable = true
            setOnClickListener { launchAgent() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val iconTile = FrameLayout(this).apply {
            background = Roost.rounded(Roost.TILE, dp(14f).toFloat())
            val s = dp(64f)
            layoutParams = LinearLayout.LayoutParams(s, s)
        }
        iconTile.addView(ImageView(this).apply {
            setImageDrawable(appIcon(pkg))
            val p = dp(12f)
            setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        card.addView(iconTile)

        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14f), 0, dp(10f), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = appLabel(pkg) ?: "Agent"
            setTextColor(Roost.TEXT)
            textSize = 18f
            typeface = Roost.medium()
        })
        texts.addView(TextView(this).apply {
            text = getString(R.string.featured_subtitle)
            setTextColor(Roost.MUTED)
            textSize = 12.5f
            setPadding(0, dp(2f), 0, 0)
        })
        card.addView(texts)

        card.addView(chip(getString(R.string.featured)))
        return card
    }

    private fun chip(label: String): TextView = TextView(this).apply {
        text = label.uppercase()
        setTextColor(accent)
        textSize = 10f
        letterSpacing = 0.08f
        typeface = Roost.medium()
        background = Roost.rounded(Roost.soft(accent), dp(20f).toFloat())
        setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
    }

    private fun utilityGrid(): View {
        val columns = 3
        val grid = GridLayout(this).apply { columnCount = columns }
        val cell = (resources.displayMetrics.widthPixels - dp(44f)) / columns
        val agentPkg = Prefs.agentPkg(this)

        Prefs.favorites(this)
            .filter { it != agentPkg }
            .mapNotNull { pkg -> appLabel(pkg)?.let { pkg to it } }
            .sortedBy { it.second.lowercase() }
            .forEach { (pkg, label) ->
                grid.addView(tile(label, appIcon(pkg), cell, isAdd = false) { launchPackage(pkg) })
            }
        grid.addView(tile(getString(R.string.add), null, cell, isAdd = true) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })

        grid.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return grid
    }

    private fun tile(label: String, icon: Drawable?, cellPx: Int, isAdd: Boolean, onClick: () -> Unit): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6f), 0, dp(6f))
            isClickable = true
            setOnClickListener { onClick() }
        }

        val surface = FrameLayout(this).apply {
            background = Roost.rounded(
                Roost.TILE, dp(16f).toFloat(),
                if (isAdd) Roost.soft(accent) else Roost.HAIRLINE, dp(1f)
            )
            val s = dp(58f)
            layoutParams = LinearLayout.LayoutParams(s, s)
        }
        surface.addView(ImageView(this).apply {
            if (isAdd) {
                setImageResource(R.drawable.ic_plus)
                setColorFilter(accent)
                val p = dp(16f); setPadding(p, p, p, p)
            } else {
                setImageDrawable(icon)
                val p = dp(13f); setPadding(p, p, p, p)
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        cell.addView(surface)

        cell.addView(TextView(this).apply {
            text = label
            setTextColor(Roost.MUTED)
            textSize = 12.5f
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(7f), 0, 0)
        })

        cell.layoutParams = GridLayout.LayoutParams().apply {
            width = cellPx
            height = GridLayout.LayoutParams.WRAP_CONTENT
            setGravity(Gravity.CENTER)
        }
        return cell
    }

    private fun appsSettingsLink(topPx: Int): TextView = TextView(this).apply {
        text = getString(R.string.apps_and_settings)
        setTextColor(Roost.MUTED)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(0, topPx, 0, 0)
        setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
    }

    private fun spacer(heightPx: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, heightPx)
    }

    // --- App resolution ---------------------------------------------------------------------

    private fun appIcon(pkg: String): Drawable? =
        try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    private fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun launchAgent(): Boolean = launchPackage(Prefs.agentPkg(this))

    private fun launchPackage(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } else {
            Toast.makeText(this, getString(R.string.not_installed, pkg), Toast.LENGTH_SHORT).show()
            false
        }
    }
}
