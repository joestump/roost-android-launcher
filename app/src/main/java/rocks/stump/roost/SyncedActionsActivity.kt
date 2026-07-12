package rocks.stump.roost

import android.content.Intent
import android.graphics.Typeface
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * The "Synced actions" settings screen (ADR-0006 / SPEC-0003). Before a folder is granted it explains
 * the flow and offers a SAF folder picker; once granted it shows the folder, the last-sync status, a
 * "Sync now" pill, the read-only list of currently-synced action titles, and a "Remove folder" row.
 *
 * The grant is a persistable SAF tree URI (survives restarts); reconciliation runs OFF the main thread
 * both here (sync-now / just-granted) and on the home surface's resume ([MainActivity]).
 *
 * Governing: ADR-0006 (declarative action provisioning), SPEC-0003 REQ "Grant a synced folder",
 * ADR-0005 (settings navigation IA), ADR-0001 (framework-only)
 */
class SyncedActionsActivity : SettingsScreen() {

    override fun screenTitle(): String = "Synced actions"

    private var syncing = false

    // Rebuild on return so status stays fresh, but not on the first resume (onCreate already built).
    private var builtOnce = false
    override fun onResume() {
        super.onResume()
        if (builtOnce) rebuild() else builtOnce = true
    }

    override fun buildContent(body: LinearLayout) {
        val uri = Prefs.syncedFolderUri(this)
        if (uri == null) buildGrantState(body) else buildGrantedState(body, uri)
    }

    // --- No folder granted yet ------------------------------------------------------------------

    private fun buildGrantState(body: LinearLayout) {
        body.addView(sectionHeader("Provision from a folder", firstOnScreen = true))
        val explain = TextView(this).apply {
            text = "Point Roost at a folder your agent shares to this phone (via Syncthing). Roost reads " +
                "every actions.d/*.json file in it and turns each into an HTTP action button — add, edit, or " +
                "delete a file and the button follows on the next sync. Your hand-built actions are never touched."
            setTextColor(SUBTLE); textSize = 12.5f
            setPadding(dp(6f), dp(2f), dp(6f), dp(4f))
        }
        body.addView(card(listOf(wrapRow(explain), grantRow())))
        body.addView(hint("Only actions Roost imported from the folder are managed here; everything else stays yours."))
    }

    private fun grantRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            isClickable = true
            setOnClickListener { launchPicker() }
        }
        row.addView(TextView(this).apply {
            text = "Grant a folder"; setTextColor(accent); textSize = 15f; typeface = Roost.medium()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = "choose…"; setTextColor(SUBTLE); textSize = 11f; typeface = Typeface.MONOSPACE
        })
        return row
    }

    // --- Folder granted -------------------------------------------------------------------------

    private fun buildGrantedState(body: LinearLayout, uri: String) {
        val name = SyncedActions.folderName(this, uri) ?: "granted folder"
        val ids = Prefs.syncedActionIds(this)
        val lastAt = Prefs.syncLastAt(this)
        val rel = if (lastAt > 0L)
            DateUtils.getRelativeTimeSpanString(lastAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                .toString()
        else "not synced yet"
        val status = "${ids.size} synced · $rel"

        body.addView(sectionHeader("Folder", firstOnScreen = true))
        body.addView(card(
            twoLineRow(name, "reads actions.d/*.json"),
            twoLineRow("Status", status)
        ))

        // Sync now (accent pill) — reconciles off-thread, toasts the summary, then rebuilds.
        val pillRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(14f), dp(4f), dp(2f))
        }
        pillRow.addView(TextView(this).apply {
            text = if (syncing) "Syncing…" else "Read actions.d/ now"
            setTextColor(SUBTLE); textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        pillRow.addView(accentPill(if (syncing) "Syncing…" else "Sync now") { syncNow() })
        body.addView(pillRow)

        // The currently-synced action titles (read-only).
        body.addView(sectionHeader("Synced actions"))
        if (ids.isEmpty()) {
            body.addView(card(listOf(twoLineRow("No synced actions", "drop an actions.d/*.json into the folder"))))
        } else {
            val buttons = Prefs.actionButtons(this)
            val rows = ids.map { id ->
                val btn = buttons.find { it.key == "http:$id" }
                val act = Prefs.httpAction(this, id)
                val title = btn?.title ?: id
                val sub = if (act != null) "${act.method} · ${HttpActionClient.hostOf(act.url)}" else "HTTP action"
                twoLineRow(title, sub)
            }
            body.addView(card(rows))
        }

        // Remove folder — releases the grant + stops syncing; keeps the imported actions in place.
        body.addView(sectionHeader("Manage"))
        body.addView(card(listOf(removeRow(uri))))
        body.addView(hint("Removing the folder stops syncing and releases access. The actions it created stay put — they just become yours to manage by hand."))
    }

    private fun removeRow(uri: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            isClickable = true
            setOnClickListener { removeFolder(uri) }
        }
        row.addView(TextView(this).apply {
            text = "Remove folder"; setTextColor(Roost.CLAY); textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = "stop syncing"; setTextColor(SUBTLE); textSize = 11f; typeface = Typeface.MONOSPACE
        })
        return row
    }

    // --- Actions --------------------------------------------------------------------------------

    // Governing: ADR-0006, SPEC-0003 REQ "Grant a synced folder"
    private fun launchPicker() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(i, REQ_PICK) }
            .onFailure { Toast.makeText(this, "No folder picker available", Toast.LENGTH_SHORT).show() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        // Persist the grant so it survives restarts (SPEC-0003 "Granting persists"). Only record the
        // folder if the take actually succeeded — otherwise the screen would show a "granted" folder
        // that silently stops working after a restart because the permission was never persisted.
        val persisted = runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!persisted) {
            Toast.makeText(this, "Couldn't keep access to that folder — try again", Toast.LENGTH_LONG).show()
            return
        }
        Prefs.setSyncedFolderUri(this, uri.toString())
        syncNow()
    }

    private fun syncNow() {
        if (syncing) return
        syncing = true
        rebuild()   // reflect the "Syncing…" pill state
        Thread {
            val res = SyncedActions.reconcile(this)
            runOnUiThread {
                syncing = false
                if (!isFinishing) {
                    Toast.makeText(this, res.toastText(), Toast.LENGTH_LONG).show()
                    rebuild()
                }
            }
        }.start()
    }

    // Keep it simple (SPEC-0003 note): release the grant + clear the tracked set so future syncs of a
    // different folder won't touch them, but LEAVE the imported actions/buttons in place.
    private fun removeFolder(uri: String) {
        runCatching {
            contentResolver.releasePersistableUriPermission(
                android.net.Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        Prefs.setSyncedFolderUri(this, null)
        Prefs.setSyncedActionIds(this, emptySet())
        Prefs.setSyncLast(this, 0L, "")
        Toast.makeText(this, "Folder removed · actions kept", Toast.LENGTH_SHORT).show()
        rebuild()
    }

    // --- small row helpers ----------------------------------------------------------------------

    /** A card row with a bold label over a muted sub-line (read-only status / list item). */
    private fun twoLineRow(label: String, sub: String): View {
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
        }
        stack.addView(TextView(this).apply {
            text = label; setTextColor(ROW_LABEL); textSize = 14.5f; maxLines = 1
        })
        stack.addView(TextView(this).apply {
            text = sub; setTextColor(SUBTLE); textSize = 11.5f; typeface = Typeface.MONOSPACE
            setPadding(0, dp(2f), 0, 0)
        })
        return stack
    }

    /** Wrap an arbitrary view as a padded card row. */
    private fun wrapRow(v: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10f), dp(12f), dp(10f), dp(12f))
        addView(v)
    }

    companion object {
        private const val REQ_PICK = 0x5CD1   // arbitrary request code for the folder picker
    }
}
