package rocks.stump.roost

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** A user-added web app: a name + URL that opens fullscreen in a WebView. */
data class WebApp(val name: String, val url: String)

/**
 * Thin typed wrapper over SharedPreferences. All launcher state lives here so both
 * MainActivity (the HOME surface) and SettingsActivity read/write the same source of truth.
 */
object Prefs {
    private const val NAME = "roost"

    const val MODE_CURATED = "curated"
    const val MODE_APPLIANCE = "appliance"

    /** Shipping default for the featured agent app. Owners pick their own in Settings. */
    const val DEFAULT_AGENT_PKG = "com.anthropic.claude"

    /** Seeded favorites. Not-yet-installed packages simply don't render until they exist. */
    val DEFAULT_FAVORITES: Set<String> = linkedSetOf(
        DEFAULT_AGENT_PKG,
        "com.wireguard.android",   // WireGuard
        "proton.android.pass",     // Proton Pass
        "ch.protonmail.android"    // Proton Mail
    )

    private const val K_MODE = "mode"
    private const val K_BOOT_LAUNCH = "auto_launch_boot"
    private const val K_KEEP_SCREEN_ON = "keep_screen_on"
    private const val K_BANDWIDTH = "bandwidth_graph"
    private const val K_AGENT_PKG = "agent_pkg"
    private const val K_FAVORITES = "favorites"
    private const val K_PENDING_BOOT = "pending_boot_launch"
    private const val K_ACCENT = "accent"
    private const val K_WALLPAPER_APPLIED = "wallpaper_applied"
    private const val K_AGENT_NAME = "agent_name"
    private const val K_WEB_APPS = "web_apps"
    private const val K_WG_TUNNEL = "wg_tunnel"
    private const val K_HASS_ACCOUNTS = "hass_accounts"
    private const val K_ACTION_BUTTONS = "action_buttons"
    private const val K_HIDDEN = "hidden_items"
    private const val K_ICON_OVERRIDES = "icon_overrides"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun mode(c: Context): String = sp(c).getString(K_MODE, MODE_CURATED) ?: MODE_CURATED
    fun setMode(c: Context, v: String) = sp(c).edit().putString(K_MODE, v).apply()

    fun autoLaunchOnBoot(c: Context): Boolean = sp(c).getBoolean(K_BOOT_LAUNCH, true)
    fun setAutoLaunchOnBoot(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_BOOT_LAUNCH, v).apply()

    fun keepScreenOn(c: Context): Boolean = sp(c).getBoolean(K_KEEP_SCREEN_ON, true)
    fun setKeepScreenOn(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_KEEP_SCREEN_ON, v).apply()

    fun bandwidthGraph(c: Context): Boolean = sp(c).getBoolean(K_BANDWIDTH, true)
    fun setBandwidthGraph(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_BANDWIDTH, v).apply()

    fun agentPkg(c: Context): String = sp(c).getString(K_AGENT_PKG, DEFAULT_AGENT_PKG) ?: DEFAULT_AGENT_PKG
    fun setAgentPkg(c: Context, v: String) = sp(c).edit().putString(K_AGENT_PKG, v).apply()

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

    /** Whether we've already painted the matching wallpaper once (first-run auto-apply). */
    fun wallpaperApplied(c: Context): Boolean = sp(c).getBoolean(K_WALLPAPER_APPLIED, false)
    fun setWallpaperApplied(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_WALLPAPER_APPLIED, v).apply()

    /** The agent's name (e.g. its username). Blank falls back to "roost" / "your agent". */
    fun agentName(c: Context): String = sp(c).getString(K_AGENT_NAME, "") ?: ""
    fun setAgentName(c: Context, v: String) = sp(c).edit().putString(K_AGENT_NAME, v).apply()

    /** WireGuard tunnel name for the one-tap VPN toggle. Blank → the VPN chip just opens WireGuard. */
    fun wireguardTunnel(c: Context): String = sp(c).getString(K_WG_TUNNEL, "") ?: ""
    fun setWireguardTunnel(c: Context, v: String) = sp(c).edit().putString(K_WG_TUNNEL, v).apply()

    // --- Home Assistant accounts ---

    fun hassAccounts(c: Context): MutableList<HassAccount> {
        val arr = JSONArray(sp(c).getString(K_HASS_ACCOUNTS, "[]") ?: "[]")
        val out = mutableListOf<HassAccount>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(HassAccount(o.optString("id"), o.optString("name"), o.optString("url"), o.optString("token")))
        }
        return out
    }

    fun setHassAccounts(c: Context, accts: List<HassAccount>) {
        val arr = JSONArray()
        accts.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("token", it.token))
        }
        sp(c).edit().putString(K_HASS_ACCOUNTS, arr.toString()).apply()
    }

    fun addHassAccount(c: Context, name: String, url: String, token: String): HassAccount {
        val acct = HassAccount(java.util.UUID.randomUUID().toString(), name.ifBlank { url }, url, token)
        setHassAccounts(c, hassAccounts(c).apply { add(acct) })
        return acct
    }

    fun removeHassAccount(c: Context, id: String) {
        setHassAccounts(c, hassAccounts(c).filterNot { it.id == id })
        // Also drop any action buttons that belonged to this account.
        setActionButtons(c, actionButtons(c).filterNot { it.kind == ActionKind.HASS_SCENE && it.a == id })
    }

    // --- Action buttons (pluggable) ---

    fun actionButtons(c: Context): MutableList<ActionButton> {
        val arr = JSONArray(sp(c).getString(K_ACTION_BUTTONS, "[]") ?: "[]")
        val out = mutableListOf<ActionButton>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val kind = runCatching { ActionKind.valueOf(o.optString("kind")) }.getOrNull() ?: continue
            out.add(ActionButton(kind, o.optString("key"), o.optString("title"), o.optString("a"), o.optString("b")))
        }
        return out
    }

    fun setActionButtons(c: Context, buttons: List<ActionButton>) {
        val arr = JSONArray()
        buttons.forEach {
            arr.put(
                JSONObject().put("kind", it.kind.name).put("key", it.key)
                    .put("title", it.title).put("a", it.a).put("b", it.b)
            )
        }
        sp(c).edit().putString(K_ACTION_BUTTONS, arr.toString()).apply()
    }

    // --- Hidden items (long-press → Hide; recoverable) ---

    fun hiddenItems(c: Context): MutableSet<String> =
        LinkedHashSet(sp(c).getStringSet(K_HIDDEN, emptySet()) ?: emptySet())

    fun isHidden(c: Context, key: String): Boolean = hiddenItems(c).contains(key)

    fun setHidden(c: Context, key: String, hidden: Boolean) {
        val cur = hiddenItems(c)
        if (hidden) cur.add(key) else cur.remove(key)
        sp(c).edit().putStringSet(K_HIDDEN, cur).apply()
    }

    // --- Icon overrides (long-press → Change icon; key → cached file path) ---

    fun iconOverride(c: Context, key: String): String? {
        val o = JSONObject(sp(c).getString(K_ICON_OVERRIDES, "{}") ?: "{}")
        return if (o.has(key)) o.optString(key) else null
    }

    fun setIconOverride(c: Context, key: String, path: String?) {
        val o = JSONObject(sp(c).getString(K_ICON_OVERRIDES, "{}") ?: "{}")
        if (path == null) o.remove(key) else o.put(key, path)
        sp(c).edit().putString(K_ICON_OVERRIDES, o.toString()).apply()
    }

    fun isActionEnabled(c: Context, key: String): Boolean = actionButtons(c).any { it.key == key }

    fun setActionEnabled(c: Context, button: ActionButton, enabled: Boolean) {
        val cur = actionButtons(c).filterNot { it.key == button.key }.toMutableList()
        if (enabled) cur.add(button)
        setActionButtons(c, cur)
    }

    /** User-added web apps (self-hosted dashboards, etc.), each opening fullscreen in a WebView. */
    fun webApps(c: Context): MutableList<WebApp> {
        val arr = JSONArray(sp(c).getString(K_WEB_APPS, "[]") ?: "[]")
        val out = mutableListOf<WebApp>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val url = o.optString("url")
            if (url.isNotBlank()) out.add(WebApp(o.optString("name").ifBlank { url }, url))
        }
        return out
    }

    fun setWebApps(c: Context, apps: List<WebApp>) {
        val arr = JSONArray()
        apps.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        sp(c).edit().putString(K_WEB_APPS, arr.toString()).apply()
    }

    fun addWebApp(c: Context, name: String, url: String) {
        val list = webApps(c)
        list.removeAll { it.url == url }
        list.add(WebApp(name.ifBlank { url }, url))
        setWebApps(c, list)
    }

    fun removeWebApp(c: Context, url: String) {
        setWebApps(c, webApps(c).filterNot { it.url == url })
    }
}
