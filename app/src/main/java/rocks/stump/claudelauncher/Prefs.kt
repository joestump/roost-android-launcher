package rocks.stump.claudelauncher

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin typed wrapper over SharedPreferences. All launcher state lives here so both
 * MainActivity (the HOME surface) and SettingsActivity read/write the same source of truth.
 */
object Prefs {
    private const val NAME = "claude_launcher"

    const val MODE_CURATED = "curated"
    const val MODE_APPLIANCE = "appliance"

    const val DEFAULT_CLAUDE_PKG = "com.anthropic.claude"

    /** Seeded favorites. Not-yet-installed packages simply don't render until they exist. */
    val DEFAULT_FAVORITES: Set<String> = linkedSetOf(
        DEFAULT_CLAUDE_PKG,
        "com.wireguard.android",   // WireGuard
        "proton.android.pass",     // Proton Pass
        "ch.protonmail.android"    // Proton Mail
    )

    private const val K_MODE = "mode"
    private const val K_BOOT_LAUNCH = "auto_launch_boot"
    private const val K_KEEP_SCREEN_ON = "keep_screen_on"
    private const val K_CLAUDE_PKG = "claude_pkg"
    private const val K_FAVORITES = "favorites"
    private const val K_PENDING_BOOT = "pending_boot_launch"
    private const val K_ACCENT = "accent"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun mode(c: Context): String = sp(c).getString(K_MODE, MODE_CURATED) ?: MODE_CURATED
    fun setMode(c: Context, v: String) = sp(c).edit().putString(K_MODE, v).apply()

    fun autoLaunchOnBoot(c: Context): Boolean = sp(c).getBoolean(K_BOOT_LAUNCH, true)
    fun setAutoLaunchOnBoot(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_BOOT_LAUNCH, v).apply()

    fun keepScreenOn(c: Context): Boolean = sp(c).getBoolean(K_KEEP_SCREEN_ON, true)
    fun setKeepScreenOn(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_KEEP_SCREEN_ON, v).apply()

    fun claudePkg(c: Context): String = sp(c).getString(K_CLAUDE_PKG, DEFAULT_CLAUDE_PKG) ?: DEFAULT_CLAUDE_PKG
    fun setClaudePkg(c: Context, v: String) = sp(c).edit().putString(K_CLAUDE_PKG, v).apply()

    fun favorites(c: Context): MutableSet<String> =
        LinkedHashSet(sp(c).getStringSet(K_FAVORITES, DEFAULT_FAVORITES) ?: DEFAULT_FAVORITES)

    fun setFavorites(c: Context, v: Set<String>) =
        sp(c).edit().putStringSet(K_FAVORITES, LinkedHashSet(v)).apply()

    /** One-shot flag set by BootReceiver, consumed on the first HOME onResume after boot. */
    fun pendingBootLaunch(c: Context): Boolean = sp(c).getBoolean(K_PENDING_BOOT, false)
    fun setPendingBootLaunch(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_PENDING_BOOT, v).apply()

    /** Themeable accent color (ARGB int). Defaults to Honey. */
    fun accent(c: Context): Int = sp(c).getInt(K_ACCENT, Roost.DEFAULT_ACCENT)
    fun setAccent(c: Context, v: Int) = sp(c).edit().putInt(K_ACCENT, v).apply()
}
