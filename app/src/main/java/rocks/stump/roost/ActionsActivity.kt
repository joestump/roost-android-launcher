package rocks.stump.roost

import android.content.Intent
import android.widget.LinearLayout

/**
 * Action buttons LANDING (ADR-0005). The "Action buttons" entry from [AppsActivity] used to cram HTTP
 * actions, Home Assistant, and app-shortcuts onto one screen (owner feedback: too much on one screen).
 * It now drills into three focused per-provider detail screens plus a dedicated reorder screen, in the
 * shared [SettingsScreen] navRow/card language:
 *
 *  1. HTTP actions  → [HttpActionsActivity] — saved HTTP actions + "New action → builder".
 *  2. Home Assistant → [HassActivity] — connected accounts + add-account form + per-account scenes.
 *  3. App shortcuts → [ShortcutsActivity] — scan apps + enable the ones you want as buttons.
 *  4. Arrange Tiles → [ArrangeActivity] — toggle any home tile on/off and long-press-drag their home order.
 *
 * Each provider screen toggles its own membership; the cross-type ORDER lives on the Arrange screen (the
 * old combined "Enabled buttons" drag list is gone). Nothing that used to be reachable was dropped.
 *
 * Governing: ADR-0005 (settings navigation IA), ADR-0002 (pluggable action-button providers),
 * ADR-0004 (generalized HTTP-action provider), ADR-0001 (framework-only)
 */
class ActionsActivity : SettingsScreen() {

    override fun screenTitle(): String = getString(R.string.actions_title)

    override fun onResume() {
        super.onResume()
        rebuild()   // refresh the trailing counts on return
    }

    override fun buildContent(body: LinearLayout) {
        val buttons = Prefs.actionButtons(this)
        val httpCount = buttons.count { it.kind == ActionKind.HTTP }.toString()
        val hassCount = Prefs.hassAccounts(this).size.toString()
        val shortcutCount = buttons.count { it.kind == ActionKind.SHORTCUT }.toString()
        val enabledCount = buttons.size.toString()

        // Synced actions (ADR-0006 / SPEC-0003): a folder Roost imports actions.d/*.json from. The sub
        // reflects whether a folder is granted; the trailing count is the number of imported ids.
        val syncedGranted = Prefs.syncedFolderUri(this) != null
        val syncedIds = Prefs.syncedActionIds(this).size
        val syncedSub = if (syncedGranted) "$syncedIds synced · from actions.d" else "Provision from a shared folder"

        body.addView(gap(dp(4f)))
        body.addView(card(
            navRow(R.drawable.ic_bolt, "HTTP Actions", "Endpoints you POST to", httpCount) {
                startActivity(Intent(this, HttpActionsActivity::class.java))
            },
            navRow(R.drawable.ic_scene, "Home Assistant", "Scenes shown as buttons", hassCount) {
                startActivity(Intent(this, HassActivity::class.java))
            },
            navRow(R.drawable.ic_search, "App Shortcuts", "One-tap app shortcuts", shortcutCount) {
                startActivity(Intent(this, ShortcutsActivity::class.java))
            },
            navRow(R.drawable.ic_folder_sync, "Synced Actions", syncedSub,
                if (syncedGranted) syncedIds.toString() else null) {
                startActivity(Intent(this, SyncedActionsActivity::class.java))
            },
            navRow(R.drawable.ic_drag_handle, "Arrange Tiles", "Enable, disable, and reorder", enabledCount) {
                startActivity(Intent(this, ArrangeActivity::class.java))
            }
        ))
        body.addView(hint("Each type enables its own buttons; “Arrange Tiles” toggles every home tile on or off and sets their order on the home screen."))
    }
}
