package rocks.stump.roost

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * The reconciler for declaratively-provisioned HTTP actions (ADR-0006 / SPEC-0003). An agent drops
 * one JSON file per action into a Syncthing-shared `actions.d/` directory the owner has granted Roost
 * a persistable SAF tree URI to; [reconcile] reads that directory and folds the files into ordinary
 * `ActionKind.HTTP` actions ([HttpAction] + an enabled [ActionButton]) — upserting each present file
 * and removing only *previously-synced* ids whose file has disappeared, so actions the owner built by
 * hand are never touched.
 *
 * Framework-only per ADR-0001: folder access is pure `DocumentsContract` + `ContentResolver` + `org.json`
 * (no AndroidX `DocumentFile`). [reconcile] does blocking I/O — callers run it OFF the main thread and
 * marshal any UI back themselves.
 *
 * Governing: ADR-0006 (declarative action provisioning), SPEC-0003
 */
object SyncedActions {

    /** Outcome of one reconcile pass; [error] non-null means the folder couldn't be read this time. */
    data class Result(
        val added: Int,
        val updated: Int,
        val removed: Int,
        val skipped: Int,
        val error: String?
    ) {
        /** True when the home surface should re-render (something actually changed). */
        fun changed(): Boolean = added > 0 || updated > 0 || removed > 0

        /** A one-line human summary for a toast / stored status. */
        fun toastText(): String {
            if (error != null) return "Sync failed: $error"
            val parts = mutableListOf<String>()
            if (added > 0) parts.add("$added added")
            if (updated > 0) parts.add("$updated updated")
            if (removed > 0) parts.add("$removed removed")
            if (skipped > 0) parts.add("$skipped skipped")
            return "Synced" + if (parts.isEmpty()) " · no changes" else " · " + parts.joinToString(", ")
        }
    }

    private val DIR = DocumentsContract.Document.MIME_TYPE_DIR

    private data class Child(val docId: String, val name: String, val mime: String)

    // One action parsed from an actions.d/*.json file. `secret`/`icon` are null when the file omits them.
    private data class FileAction(
        val id: String,
        val title: String,
        val method: String,
        val url: String,
        val headers: List<Pair<String, String>>,
        val auth: HttpAuth,
        val body: String,
        val secret: String?,
        val icon: String?
    )

    /**
     * Reconcile the granted folder into HTTP actions. No-op (zero result) when no folder is granted.
     * Robust to permission loss / a bad URI (returns an [Result.error] without mutating the synced set,
     * so a transient blip never nukes imported actions). A missing `actions.d/` is treated as an empty
     * file set so declarative removal still runs.
     *
     * Governing: ADR-0006, SPEC-0003 REQ "Declarative import from actions.d JSON",
     * SPEC-0003 REQ "Declarative removal, scoped to synced ids only"
     */
    fun reconcile(context: Context): Result {
        val uriStr = Prefs.syncedFolderUri(context) ?: return Result(0, 0, 0, 0, null)

        val parsedPairs: List<Pair<String, FileAction>>   // (source filename, action)
        val presentFilenames: Set<String>                 // every .json filename currently in actions.d/
        var skipped: Int
        try {
            val treeUri = Uri.parse(uriStr)
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            // Enumerating the tree root is the operation that fails on a lost/revoked grant — let it throw
            // into the catch below so we surface an error instead of treating the folder as empty.
            val rootChildren = listChildren(context, treeUri, rootDocId)
            val actionsDir = rootChildren.firstOrNull { it.name == "actions.d" && it.mime == DIR }

            val jsonFiles = if (actionsDir == null) emptyList()
            else listChildren(context, treeUri, actionsDir.docId)
                .filter { it.mime != DIR && it.name.endsWith(".json", ignoreCase = true) }

            // Removal keys off file PRESENCE (filename), not parseability, so a present-but-malformed
            // file (a mid-write truncation OR a genuinely bad file) still protects its action.
            presentFilenames = jsonFiles.map { it.name }.toSet()
            val pairs = mutableListOf<Pair<String, FileAction>>()
            var skips = 0
            for (f in jsonFiles) {
                // A genuine READ failure (I/O error / null stream) THROWS into the folder-level catch
                // below, so a transient blip mutates nothing. A parse failure returns null (a skip);
                // the file's presence still protects its action (see the removal phase).
                val fa = readFileAction(context, treeUri, f)
                if (fa == null) skips++ else pairs.add(f.name to fa)
            }
            parsedPairs = pairs
            skipped = skips
        } catch (e: Exception) {
            val r = Result(0, 0, 0, 0, e.message ?: "couldn't read the folder")
            Prefs.setSyncLast(context, System.currentTimeMillis(), r.toastText())
            return r
        }

        // De-dupe on id (last file wins) so two files claiming the same id can't double-count.
        val byId = LinkedHashMap<String, FileAction>()
        parsedPairs.forEach { byId[it.second.id] = it.second }

        var added = 0
        var updated = 0
        for (fa in byId.values) {
            val key = "http:${fa.id}"
            val existing = Prefs.httpAction(context, fa.id)
            val newAction = HttpAction(fa.id, fa.method, fa.url, fa.headers, fa.auth, fa.body)

            // UPSERT the definition. setHttpAction replaces by id; the home order is governed by the
            // ActionButton list (below), not by http_actions order, so this never reshuffles the home.
            Prefs.setHttpAction(context, newAction)

            // Enable / update the button WITHOUT reordering: append a new one, edit an existing one in
            // place. This preserves the owner's arrangement on every sync.
            // Governing: SPEC-0003 REQ "Reconcile trigger and ordering"
            val buttons = Prefs.actionButtons(context)
            val existingBtn = buttons.find { it.key == key }
            val titleChanged = existingBtn != null && existingBtn.title != fa.title
            if (existingBtn == null) {
                Prefs.setActionEnabled(context, ActionButton(ActionKind.HTTP, key, fa.title, fa.id, ""), true)
            } else if (titleChanged) {
                Prefs.setActionButtons(context, buttons.map {
                    if (it.key == key) it.copy(title = fa.title) else it
                })
            }

            // Any provided secret goes to the normal per-id secret store; it never travels inline again.
            // Governing: SPEC-0003 REQ "Secrets and framework-only I/O"
            if (fa.secret != null) Prefs.setHttpSecret(context, fa.id, fa.secret)

            val defChanged = existing == null || existing != newAction || titleChanged
            when {
                existing == null -> added++
                defChanged -> updated++
            }

            // Best-effort icon: fetch only on first import or when the action changed (never re-hit the
            // network for an unchanged action on every resume). An icon miss NEVER fails the sync.
            if (fa.icon != null && (existing == null || defChanged || Prefs.iconOverride(context, key) == null)) {
                runCatching { fetchIcon(context, key, fa.icon) }
            }
        }

        // REMOVAL — keyed on file PRESENCE (by filename), never on parseability, and only ever for
        // previously-synced ids (manual ids are never tracked, so always safe). Roost remembers which
        // filename backs each synced id; a previously-synced id is removed ONLY when its source file is
        // gone from the folder. A present-but-malformed file (mid-write truncation OR a genuinely bad
        // file) keeps its prior id→filename mapping, so it never deletes an action — and a real file
        // deletion is honored immediately, regardless of any OTHER file's parse state. First run after a
        // grant (empty prevSources) removes nothing — it just records the sources to reconcile against.
        // Governing: SPEC-0003 REQ "Declarative removal, scoped to synced ids only"
        val prevSources = Prefs.syncedActionSources(context)   // id -> filename
        var removed = 0
        for ((id, fname) in prevSources) {
            if (fname !in presentFilenames) {
                Prefs.removeHttpAction(context, id)   // purges definition + secret + button
                removed++
            }
        }
        // Rebuild id→filename: parsed files this pass, plus present-but-unparsed files keep their prior id.
        val nextSources = LinkedHashMap<String, String>()
        parsedPairs.forEach { (fname, fa) -> nextSources[fa.id] = fname }
        val prevIdByFilename = prevSources.entries.associate { it.value to it.key }
        val parsedFilenames = parsedPairs.map { it.first }.toSet()
        for (fname in presentFilenames) {
            if (fname !in parsedFilenames) {                       // present but didn't parse this pass
                val id = prevIdByFilename[fname]
                if (id != null && Prefs.httpAction(context, id) != null) nextSources[id] = fname
            }
        }
        Prefs.setSyncedActionSources(context, nextSources)
        Prefs.setSyncedActionIds(context, nextSources.keys)

        val result = Result(added, updated, removed, skipped, null)
        Prefs.setSyncLast(context, System.currentTimeMillis(), result.toastText())
        return result
    }

    // --- folder I/O (DocumentsContract only) ----------------------------------------------------

    /** List a directory's children as (docId, displayName, mime). Throws if the tree can't be read. */
    private fun listChildren(context: Context, treeUri: Uri, parentDocId: String): List<Child> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val out = mutableListOf<Child>()
        val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw IllegalStateException("folder not readable")
        cursor.use { c ->
            while (c.moveToNext()) {
                out.add(Child(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: ""))
            }
        }
        return out
    }

    /**
     * Read + parse one file into a [FileAction], or null to skip it (present-but-malformed / id-less).
     * A genuine READ failure (I/O error, null stream) is NOT a skip — it THROWS so [reconcile]'s
     * folder-level catch surfaces a transient error and mutates nothing, instead of treating a present
     * file as absent and removing its action.
     * Governing: SPEC-0003 REQ "Declarative removal, scoped to synced ids only"
     */
    private fun readFileAction(context: Context, treeUri: Uri, file: Child): FileAction? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, file.docId)
        val text = context.contentResolver.openInputStream(docUri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("couldn't read ${file.name}")
        // Malformed / incomplete JSON (e.g. a mid-write truncation) parses to null -> skipped, but the
        // file's presence still protects its action from declarative removal (see reconcile()).
        return runCatching { parse(text) }.getOrNull()
    }

    /** Parse the one-file schema. Requires id, title, method, url; skips (returns null) otherwise. */
    private fun parse(text: String): FileAction? {
        val o = JSONObject(text)
        val id = o.optString("id").trim()
        val title = o.optString("title").trim()
        val method = o.optString("method").trim().uppercase()
        val url = o.optString("url").trim()
        if (id.isBlank() || title.isBlank() || method.isBlank() || url.isBlank()) return null

        val auth = runCatching { HttpAuth.valueOf(o.optString("auth", "NONE").trim().uppercase()) }
            .getOrDefault(HttpAuth.NONE)
        val headers = parseHeaders(o.opt("headers"))
        val body = o.optString("body", "")
        val secret = if (o.has("secret")) o.optString("secret").ifBlank { null } else null
        val icon = if (o.has("icon")) o.optString("icon").trim().ifBlank { null } else null
        return FileAction(id, title, method, url, headers, auth, body, secret, icon)
    }

    // Accept the documented object form {"X-Foo":"bar"} and, tolerantly, an array of {k/key/name,v/value}.
    private fun parseHeaders(raw: Any?): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        when (raw) {
            is JSONObject -> {
                val keys = raw.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k.isNotBlank()) out.add(k to raw.optString(k))
                }
            }
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    val h = raw.optJSONObject(i) ?: continue
                    val k = (h.optString("k").ifBlank { h.optString("key") }.ifBlank { h.optString("name") }).trim()
                    if (k.isNotBlank()) out.add(k to (h.optString("v").ifBlank { h.optString("value") }))
                }
            }
        }
        return out
    }

    // --- optional per-action icon (best-effort, ADR-0003 IconStore) -----------------------------

    private val ICON_TINT = 0xFFE8E1D5.toInt()

    /** Resolve [icon] (a selfh.st/Simple-Icons/Heroicons slug OR a URL) to a cached file + set override.
     *  Records `mono` per SUCCESSFUL branch (SVG slug sets = monochrome, tintable with accent; raster
     *  selfh.st/URL logos = full-color) — never inferred after the fact, since the slug path tries a
     *  raster source first and only falls back to SVG. */
    private fun fetchIcon(context: Context, key: String, icon: String) {
        var mono = false
        val file = if (icon.startsWith("http://") || icon.startsWith("https://")) {
            if (icon.endsWith(".svg", ignoreCase = true)) IconStore.cacheSvg(context, icon, ICON_TINT).also { mono = it != null }
            else IconStore.cacheRaster(context, icon)
        } else {
            // A bare slug: try selfh.st raster first (full-color), then Simple Icons / Heroicons SVG (mono).
            IconStore.cacheRaster(context, IconStore.selfhstPngUrl(icon))
                ?: IconStore.cacheSvg(context, IconStore.iconifyUrl("simple-icons", icon), ICON_TINT).also { mono = it != null }
                ?: IconStore.cacheSvg(context, IconStore.iconifyUrl("heroicons-solid", icon), ICON_TINT).also { mono = it != null }
        }
        if (file != null) Prefs.setIconOverride(context, key, file.path, mono = mono)
    }

    // --- display helpers (for the settings screen) ----------------------------------------------

    /** The granted folder's display name (e.g. "roost"), or null if it can't be resolved. */
    fun folderName(context: Context, uriStr: String): String? = runCatching {
        val treeUri = Uri.parse(uriStr)
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        context.contentResolver.query(
            docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
}
