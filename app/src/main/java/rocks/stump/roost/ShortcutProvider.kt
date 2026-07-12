package rocks.stump.roost

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process

/**
 * Reads and launches other apps' app-shortcuts (the long-press menu items). This only works because
 * Roost is the device's default home app — `LauncherApps.getShortcuts` requires that role.
 */
object ShortcutProvider {

    data class Item(val pkg: String, val appLabel: String, val id: String, val label: String) {
        val key get() = "shortcut:$pkg:$id"
        fun toButton() = ActionButton(ActionKind.SHORTCUT, key, "$appLabel · $label", pkg, id)
    }

    /**
     * Render a SHORTCUT button as "<shortcut> in <App>" (e.g. "New tab in Firefox", "Video in Camera")
     * for display, without changing the stored "App · label" title (so no migration is needed). [appName]
     * is the live launcher label; if null/blank (e.g. the app was uninstalled) we fall back to the app
     * name baked into the stored title. Guards avoid "Firefox in Firefox" and labels that already name
     * their app.
     */
    fun displayTitle(button: ActionButton, appName: String?): String {
        // Stored title is "appLabel · label"; split on the FIRST " · " so a shortcut whose own label
        // contains " · " keeps all of it (app names don't contain the separator).
        val label = button.title.substringAfter(" · ")
        val app = appName?.takeIf { it.isNotBlank() }
            ?: button.title.substringBefore(" · ", "")
        return if (app.isBlank() || label.equals(app, ignoreCase = true) || label.contains(app, ignoreCase = true))
            label
        else
            "$label in $app"
    }

    private fun launcherApps(c: Context): LauncherApps? =
        c.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    private fun flags(): Int =
        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED

    private fun shortcutsFor(c: Context, pkg: String): List<ShortcutInfo> {
        val la = launcherApps(c) ?: return emptyList()
        return try {
            val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(flags())
            la.getShortcuts(q, Process.myUserHandle()) ?: emptyList()
        } catch (e: Exception) {
            emptyList() // SecurityException if Roost isn't the active launcher
        }
    }

    /** Every shortcut across all installed launchable apps (for the picker). */
    fun scanAll(c: Context): List<Item> {
        val pm = c.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pkgs = pm.queryIntentActivities(launch, 0)
            .map { it.activityInfo.packageName }.distinct().filter { it != c.packageName }
        val out = mutableListOf<Item>()
        for (pkg in pkgs) {
            val appLabel = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg }
            for (s in shortcutsFor(c, pkg)) {
                val label = (s.shortLabel ?: s.longLabel ?: s.id).toString()
                out.add(Item(pkg, appLabel, s.id, label))
            }
        }
        return out.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    fun icon(c: Context, pkg: String, id: String): Drawable? {
        val la = launcherApps(c) ?: return null
        val s = shortcutsFor(c, pkg).find { it.id == id } ?: return null
        return try {
            la.getShortcutIconDrawable(s, c.resources.displayMetrics.densityDpi)
        } catch (e: Exception) { null }
    }

    fun invoke(c: Context, pkg: String, id: String): Boolean {
        val la = launcherApps(c) ?: return false
        return try {
            la.startShortcut(pkg, id, null, null, Process.myUserHandle())
            true
        } catch (e: Exception) { false }
    }
}
