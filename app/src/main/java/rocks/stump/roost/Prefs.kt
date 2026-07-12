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
        "com.wireguard.android",                    // WireGuard
        "proton.android.pass",                      // Proton Pass
        "ch.protonmail.android",                    // Proton Mail
        "com.nutomic.syncthingandroid",             // Syncthing (official / F-Droid)
        "com.github.catfriend1.syncthingandroid"    // Syncthing-Fork (Play)
    )

    private const val K_MODE = "mode"
    private const val K_BOOT_LAUNCH = "auto_launch_boot"
    private const val K_KEEP_SCREEN_ON = "keep_screen_on"
    private const val K_BANDWIDTH = "bandwidth_graph"
    private const val K_BOOT_INTRO = "boot_intro"
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
    private const val K_HTTP_ACTIONS = "http_actions"
    private const val K_HTTP_SECRETS = "http_secrets"
    private const val K_ACTION_DENSITY = "action_density"
    private const val K_SYNCED_FOLDER = "synced_folder_uri"
    private const val K_SYNCED_IDS = "synced_action_ids"
    private const val K_SYNCED_SOURCES = "synced_action_sources"
    private const val K_SYNC_LAST_AT = "sync_last_at"
    private const val K_SYNC_LAST_SUMMARY = "sync_last_summary"

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

    /**
     * Play the "waking up" boot intro (mascot + terminal boot log) after a boot-triggered launch, in
     * curated mode. Off by default so normal home launch is unchanged; a simple flag gates the screen.
     */
    fun bootIntro(c: Context): Boolean = sp(c).getBoolean(K_BOOT_INTRO, false)
    fun setBootIntro(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_BOOT_INTRO, v).apply()

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

    // --- HTTP actions (generalized provider) ---
    // A tolerant JSON collection keyed by id, mirroring hass_accounts: a malformed entry is skipped,
    // never fatal, and there is no migration/seed. Secrets live in a parallel keyed store so they
    // never travel inline with the definition.
    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "General HTTP-action definition"

    fun httpActions(c: Context): MutableList<HttpAction> {
        val arr = JSONArray(sp(c).getString(K_HTTP_ACTIONS, "[]") ?: "[]")
        val out = mutableListOf<HttpAction>()
        for (i in 0 until arr.length()) {
            val o = runCatching { arr.getJSONObject(i) }.getOrNull() ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue                                    // skip malformed
            val auth = runCatching { HttpAuth.valueOf(o.optString("auth", "NONE")) }
                .getOrDefault(HttpAuth.NONE)
            val headers = mutableListOf<Pair<String, String>>()
            val ha = o.optJSONArray("headers")
            if (ha != null) {
                for (j in 0 until ha.length()) {
                    val h = runCatching { ha.getJSONObject(j) }.getOrNull() ?: continue
                    val k = h.optString("k")
                    if (k.isNotBlank()) headers.add(k to h.optString("v"))
                }
            }
            out.add(
                HttpAction(
                    id, o.optString("method", "POST").ifBlank { "POST" },
                    o.optString("url"), headers, auth, o.optString("body")
                )
            )
        }
        return out
    }

    fun setHttpActions(c: Context, actions: List<HttpAction>) {
        val arr = JSONArray()
        actions.forEach { a ->
            val ha = JSONArray()
            a.headers.forEach { (k, v) -> ha.put(JSONObject().put("k", k).put("v", v)) }
            arr.put(
                JSONObject().put("id", a.id).put("method", a.method).put("url", a.url)
                    .put("auth", a.auth.name).put("body", a.body).put("headers", ha)
            )
        }
        sp(c).edit().putString(K_HTTP_ACTIONS, arr.toString()).apply()
    }

    fun httpAction(c: Context, id: String): HttpAction? = httpActions(c).find { it.id == id }

    /** Insert or replace [action] by id. */
    fun setHttpAction(c: Context, action: HttpAction) {
        val cur = httpActions(c).filterNot { it.id == action.id }.toMutableList()
        cur.add(action)
        setHttpActions(c, cur)
    }

    fun removeHttpAction(c: Context, id: String) {
        setHttpActions(c, httpActions(c).filterNot { it.id == id })
        setHttpSecret(c, id, null)
        setActionButtons(c, actionButtons(c).filterNot { it.kind == ActionKind.HTTP && it.a == id })
    }

    // Secrets keyed by action id — entered masked, shown only as •••• last4, redacted from echoes.
    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Secret handling"
    fun httpSecret(c: Context, id: String): String {
        val o = JSONObject(sp(c).getString(K_HTTP_SECRETS, "{}") ?: "{}")
        return if (o.has(id)) o.optString(id) else ""
    }

    fun setHttpSecret(c: Context, id: String, value: String?) {
        val o = JSONObject(sp(c).getString(K_HTTP_SECRETS, "{}") ?: "{}")
        if (value.isNullOrEmpty()) o.remove(id) else o.put(id, value)
        sp(c).edit().putString(K_HTTP_SECRETS, o.toString()).apply()
    }

    // --- Synced actions (declarative provisioning from a granted folder) ---
    // The persisted SAF tree URI of the owner's Syncthing-shared folder, the set of ids Roost has
    // imported from that folder's actions.d/*.json (the reconcile-removal scope — manual actions are
    // never in here), and a compact last-sync status. All tolerant/typed like the collections above.
    // Governing: ADR-0006 (declarative action provisioning), SPEC-0003 REQ "Grant a synced folder"

    /** The persisted SAF tree URI (as a String), or null if no folder has been granted. */
    fun syncedFolderUri(c: Context): String? = sp(c).getString(K_SYNCED_FOLDER, null)
    fun setSyncedFolderUri(c: Context, uri: String?) =
        sp(c).edit().putString(K_SYNCED_FOLDER, uri).apply()

    /** The ids Roost imported from the folder — the ONLY ids a sync is allowed to remove. */
    fun syncedActionIds(c: Context): MutableSet<String> =
        LinkedHashSet(sp(c).getStringSet(K_SYNCED_IDS, emptySet()) ?: emptySet())
    fun setSyncedActionIds(c: Context, ids: Set<String>) =
        sp(c).edit().putStringSet(K_SYNCED_IDS, LinkedHashSet(ids)).apply()

    /** id → source filename for each synced action, so removal keys off file PRESENCE (a present-but-
     *  malformed file still protects its action) rather than parseability. */
    fun syncedActionSources(c: Context): Map<String, String> {
        val o = JSONObject(sp(c).getString(K_SYNCED_SOURCES, "{}") ?: "{}")
        val out = LinkedHashMap<String, String>()
        for (k in o.keys()) out[k] = o.optString(k)
        return out
    }
    fun setSyncedActionSources(c: Context, map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (id, name) -> o.put(id, name) }
        sp(c).edit().putString(K_SYNCED_SOURCES, o.toString()).apply()
    }

    fun syncLastAt(c: Context): Long = sp(c).getLong(K_SYNC_LAST_AT, 0L)
    fun syncLastSummary(c: Context): String = sp(c).getString(K_SYNC_LAST_SUMMARY, "") ?: ""
    fun setSyncLast(c: Context, at: Long, summary: String) =
        sp(c).edit().putLong(K_SYNC_LAST_AT, at).putString(K_SYNC_LAST_SUMMARY, summary).apply()

    // --- Action-zone tile density (one setting for the whole zone) ---
    // Stored as the enum name; a bad/absent value falls back to REGULAR (today's look).
    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "On-tile firing state machine"
    fun actionDensity(c: Context): ActionDensity =
        runCatching {
            ActionDensity.valueOf(
                sp(c).getString(K_ACTION_DENSITY, ActionDensity.REGULAR.name) ?: ActionDensity.REGULAR.name
            )
        }.getOrDefault(ActionDensity.REGULAR)

    fun setActionDensity(c: Context, d: ActionDensity) =
        sp(c).edit().putString(K_ACTION_DENSITY, d.name).apply()

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
