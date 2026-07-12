package rocks.stump.roost

import android.content.Context

/**
 * The single union of every home tile except the featured agent — favorite apps, web apps, and the
 * stored action buttons (shortcuts / HASS / HTTP) — as one ordered List<ActionButton>. Both the home
 * renderer and the Arrange screen consume this, so their order can never diverge.
 *
 * APP/WEB buttons are EPHEMERAL: derived read-only from Prefs.favorites / Prefs.webApps, never written
 * into action_buttons. Placement (order) lives in Prefs.tileLayout keyed by ActionButton.key, so the
 * hidden / disabled / icon stores compose with zero migration.
 *
 * Governing: ADR-0007 (unified tile model), SPEC-0004 REQ "Apps and web apps are ActionButtons" / "One layout"
 */
object UnifiedTiles {

    /** The raw union in DEFAULT order: favorites (installed, minus the agent, alphabetical by label), then
     *  web apps (stored order), then the stored action buttons (their stored order). */
    fun union(context: Context): List<ActionButton> {
        val pm = context.packageManager
        val agent = Prefs.agentPkg(context)
        val apps = Prefs.favorites(context)
            .filter { it != agent && pm.getLaunchIntentForPackage(it) != null }
            .mapNotNull { pkg ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrNull() ?: return@mapNotNull null
                ActionButton(ActionKind.APP, "app:$pkg", label, pkg, "")
            }
            .sortedBy { it.title.lowercase() }
        val web = Prefs.webApps(context)
            .map { wa -> ActionButton(ActionKind.WEB, "web:${wa.url}", wa.name, wa.url, "") }
        return apps + web + Prefs.actionButtons(context)
    }

    /** The union ordered by Prefs.tileLayout: seeds on first run from the union order and appends brand-
     *  new keys to the tail. Stale keys no provider still emits are filtered out of the RETURNED list (via
     *  mapNotNull) but left untouched in the STORED layout — so a transiently-unavailable tile keeps its
     *  saved position and reappears in place. NOT filtered by hidden/disabled — Arrange shows disabled
     *  tiles; the home applies that filter itself. */
    fun ordered(context: Context): List<ActionButton> {
        val u = union(context)
        Prefs.seedTileLayoutIfNeeded(context, u.map { it.key })
        val layout = Prefs.tileLayout(context)
        val newKeys = u.map { it.key }.filterNot { it in layout }
        if (newKeys.isNotEmpty()) Prefs.setTileLayout(context, layout + newKeys)
        val byKey = u.associateBy { it.key }
        return (layout + newKeys).mapNotNull { byKey[it] }
    }
}
