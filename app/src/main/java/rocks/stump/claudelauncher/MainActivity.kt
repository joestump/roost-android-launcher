package rocks.stump.claudelauncher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The HOME surface. Two behaviors, switchable in Settings:
 *
 *  - Curated  : Home shows the favorites grid. On boot, Claude is foregrounded once.
 *  - Appliance: Home shows a minimal Claude "lock" screen; a long-press reveals the grid
 *               for the current visit only (re-locks when you leave).
 *
 * Boot auto-launch (both modes) is driven by a one-shot flag armed in BootReceiver and
 * consumed here — this keeps the actual startActivity() in the foreground, which is allowed.
 */
class MainActivity : Activity() {

    /** Appliance-mode: true only while the user has long-pressed to reveal the grid this visit. */
    private var applianceRevealed = false

    override fun onResume() {
        super.onResume()
        applyScreenOn()

        if (Prefs.pendingBootLaunch(this)) {
            Prefs.setPendingBootLaunch(this, false)
            if (launchClaude()) return   // Claude is now foreground; nothing to draw.
        }

        render()
    }

    override fun onStop() {
        super.onStop()
        applianceRevealed = false        // re-lock appliance mode on the next visit
    }

    /** Home is a dead end — never let Back drop the user to a blank task. */
    override fun onBackPressed() { /* no-op */ }

    private fun applyScreenOn() {
        if (Prefs.keepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun render() {
        val appliance = Prefs.mode(this) == Prefs.MODE_APPLIANCE
        if (appliance && !applianceRevealed) renderLock() else renderGrid()
    }

    // --- Appliance lock screen -------------------------------------------------------------

    private fun renderLock() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BG)
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setOnLongClickListener {
                applianceRevealed = true
                renderGrid()
                true
            }
        }
        buildTile(Prefs.claudePkg(this), big = true) { launchClaude() }?.let { root.addView(it) }
        root.addView(TextView(this).apply {
            text = getString(R.string.appliance_hint)
            setTextColor(HINT)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
        setContentView(root)
    }

    // --- Curated / revealed grid -----------------------------------------------------------

    private fun renderGrid() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(24))
        }

        // Featured Claude tile.
        buildTile(Prefs.claudePkg(this), big = true) { launchClaude() }?.let { col.addView(it) }

        // Grid of the other favorites that are actually installed.
        val grid = GridLayout(this).apply {
            columnCount = 3
            setPadding(0, dp(24), 0, 0)
        }
        val claudePkg = Prefs.claudePkg(this)
        Prefs.favorites(this)
            .filter { it != claudePkg }
            .mapNotNull { pkg -> appLabel(pkg)?.let { pkg to it } }
            .sortedBy { it.second.lowercase() }
            .forEach { (pkg, _) ->
                buildTile(pkg, big = false) { launchPackage(pkg) }?.let { grid.addView(it) }
            }
        col.addView(grid)

        // Escape hatch to the app picker / settings.
        col.addView(TextView(this).apply {
            text = getString(R.string.apps_and_settings)
            setTextColor(HINT)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(28), 0, 0)
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(col)
        })
    }

    // --- Tile construction -----------------------------------------------------------------

    private fun buildTile(pkg: String, big: Boolean, onClick: () -> Unit): View? {
        // For the Claude package we always show a tile (even pre-install) so the device is usable
        // the moment Claude finishes installing; other packages only render once installed.
        val label = appLabel(pkg) ?: if (pkg == Prefs.claudePkg(this)) "Claude" else return null
        val icon: Drawable? = try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = if (big) dp(16) else dp(12)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            setOnClickListener { onClick() }
        }
        cell.addView(ImageView(this).apply {
            icon?.let { setImageDrawable(it) }
            val s = if (big) dp(96) else dp(56)
            layoutParams = LinearLayout.LayoutParams(s, s)
        })
        cell.addView(TextView(this).apply {
            text = label
            setTextColor(FG)
            textSize = if (big) 20f else 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        })

        cell.layoutParams = if (big) {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        } else {
            GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setGravity(Gravity.CENTER)
            }
        }
        return cell
    }

    // --- Helpers ---------------------------------------------------------------------------

    private fun appLabel(pkg: String): String? = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun launchClaude(): Boolean = launchPackage(Prefs.claudePkg(this))

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

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    companion object {
        private const val BG = 0xFF141414.toInt()
        private const val FG = 0xFFF2F2F2.toInt()
        private const val HINT = 0xFF9A9A9A.toInt()
    }
}
