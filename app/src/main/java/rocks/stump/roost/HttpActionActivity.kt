package rocks.stump.roost

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The HTTP-action builder (ADR-0004): title + icon, a method segmented control, a URL field, an auth
 * selector (None/Bearer/HMAC) with a plain-language hint and a masked secret, add/remove header rows,
 * a JSON body editor with tappable {{var}} chips + live validity, and a Test-fire that runs the REAL
 * request off-thread and shows the status + truncated body with Authorization/signatures redacted.
 * Save persists the HttpAction + secret and enables an ActionButton(HTTP).
 *
 * Reachable raw, or pre-filled by [EndpointsActivity] via the EXTRA_* template extras.
 *
 * Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Action builder"
 */
class HttpActionActivity : Activity() {

    private lateinit var id: String
    private var method = "POST"
    private var auth = HttpAuth.NONE
    private var secretReplacing = false
    // The auth scheme the currently-stored secret was saved under (null when no secret is stored). A
    // stored secret is only valid — shown masked, reused on save — while auth == secretScheme; switching
    // schemes invalidates it so a Bearer token can never be reused as an HMAC shared secret. (Fix 7.)
    private var secretScheme: HttpAuth? = null

    private lateinit var titleEdit: EditText
    private lateinit var urlEdit: EditText
    private lateinit var bodyEdit: EditText
    private lateinit var iconImage: ImageView
    private lateinit var methodRow: LinearLayout
    private lateinit var authRow: LinearLayout
    private lateinit var authHint: TextView
    private lateinit var secretBox: LinearLayout
    private lateinit var headersBox: LinearLayout
    private lateinit var jsonHint: TextView
    private lateinit var testPanel: LinearLayout

    private val methodPills = mutableListOf<TextView>()
    private val authPills = mutableListOf<TextView>()
    private val headerRows = mutableListOf<Pair<EditText, EditText>>()
    private var newSecret: String = ""

    private val accent get() = Prefs.accent(this)
    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH")
    // No {{prompt}} chip: no fire path (MainActivity.invokeAction / testFire) ever supplies a "prompt"
    // var, so it would always resolve to empty — don't advertise a no-op token.
    private val varChips = listOf("battery", "timestamp", "agent", "device")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        id = intent.getStringExtra(EXTRA_ID) ?: UUID.randomUUID().toString()
        val existing = Prefs.httpAction(this, id)
        method = intent.getStringExtra(EXTRA_METHOD) ?: existing?.method ?: "POST"
        auth = runCatching { HttpAuth.valueOf(intent.getStringExtra(EXTRA_AUTH) ?: existing?.auth?.name ?: "NONE") }
            .getOrDefault(HttpAuth.NONE)
        val hasStoredSecret = Prefs.httpSecret(this, id).isNotEmpty()
        // The stored secret (if any) belongs to the scheme the action was last saved with.
        secretScheme = if (hasStoredSecret) existing?.auth else null
        secretReplacing = !hasStoredSecret

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(26f), dp(20f), dp(44f))
        }

        col.addView(TextView(this).apply {
            text = "HTTP Action"
            setTextColor(Roost.TEXT); textSize = 22f; typeface = Roost.medium()
            setPadding(0, 0, 0, dp(16f))
        })

        // --- title + icon ---
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        iconImage = ImageView(this).apply {
            background = Roost.rounded(Roost.soft(accent), dp(13f).toFloat(), Roost.soft(accent), dp(1f))
            val p = dp(11f); setPadding(p, p, p, p)
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@HttpActionActivity, IconPickerActivity::class.java)
                    .putExtra(IconPickerActivity.EXTRA_KEY, buttonKey()))
            }
        }
        titleRow.addView(iconImage, LinearLayout.LayoutParams(dp(46f), dp(46f)).apply { rightMargin = dp(10f) })
        val existingTitle = Prefs.actionButtons(this).find { it.key == buttonKey() }?.title
        titleEdit = edit("Action name", false).apply {
            setText(intent.getStringExtra(EXTRA_TITLE) ?: existingTitle ?: "")
            textSize = 16f; typeface = Roost.medium()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(titleEdit)
        col.addView(titleRow)

        // --- request (method + url) ---
        col.addView(sectionLabel("Request"))
        methodRow = segmentedRow()
        methods.forEach { m ->
            val pill = pill(m) { method = m; styleMethodPills() }
            methodPills.add(pill); methodRow.addView(pill)
        }
        col.addView(methodRow)
        urlEdit = edit("https://…", true).apply {
            setText(intent.getStringExtra(EXTRA_URL) ?: existing?.url ?: "")
            typeface = Typeface.MONOSPACE; textSize = 13f
            background = fieldBg()
            val p = dp(12f); setPadding(p, p, p, p)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9f) }
        }
        col.addView(urlEdit)

        // --- auth ---
        col.addView(sectionLabel("Auth"))
        authRow = segmentedRow()
        listOf(HttpAuth.NONE to "None", HttpAuth.BEARER to "Bearer", HttpAuth.HMAC to "HMAC").forEach { (a, lbl) ->
            val pill = pill(lbl) { onAuthSelected(a) }
            pill.tag = a
            authPills.add(pill); authRow.addView(pill)
        }
        col.addView(authRow)
        authHint = TextView(this).apply {
            setTextColor(0xFF8F8578.toInt()); textSize = 11.5f
            setPadding(dp(4f), dp(9f), dp(4f), 0)
        }
        col.addView(authHint)
        secretBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(11f), 0, 0)
        }
        col.addView(secretBox)

        // --- headers ---
        val headHdr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(22f), dp(4f), dp(9f))
        }
        headHdr.addView(TextView(this).apply {
            text = "HEADERS"; setTextColor(0xFF6E665B.toInt()); textSize = 10f
            letterSpacing = 0.15f; typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headHdr.addView(TextView(this).apply {
            text = "+ add"; setTextColor(accent); textSize = 10f; typeface = Typeface.MONOSPACE
            isClickable = true; setOnClickListener { addHeaderRow("", "") }
        })
        col.addView(headHdr)
        headersBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(headersBox)

        // --- body ---
        col.addView(sectionLabel("JSON body"))
        val chipFlow = FlowLayout(this, hGap = dp(6f), vGap = dp(6f)).apply {
            setPadding(0, 0, 0, dp(9f))
        }
        varChips.forEach { v ->
            chipFlow.addView(TextView(this).apply {
                text = "{{$v}}"; setTextColor(accent); textSize = 10.5f; typeface = Typeface.MONOSPACE
                background = Roost.rounded(Roost.soft(accent), dp(8f).toFloat())
                setPadding(dp(9f), dp(5f), dp(9f), dp(5f))
                isClickable = true
                setOnClickListener { insertVar(v) }
            })
        }
        col.addView(chipFlow)
        bodyEdit = EditText(this).apply {
            setText(intent.getStringExtra(EXTRA_BODY) ?: existing?.body ?: "")
            setTextColor(0xFFE8E1D5.toInt()); setHintTextColor(Roost.MUTED)
            hint = "{ }"
            typeface = Typeface.MONOSPACE; textSize = 12.5f
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            background = fieldBg()
            val p = dp(13f); setPadding(p, p, p, p)
        }
        col.addView(bodyEdit)
        jsonHint = TextView(this).apply {
            textSize = 10.5f; typeface = Typeface.MONOSPACE
            setPadding(dp(4f), dp(7f), 0, 0)
        }
        col.addView(jsonHint)
        bodyEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = updateJsonHint()
            override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
            override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
        })

        // --- test fire ---
        col.addView(TextView(this).apply {
            text = "Test Fire"
            setTextColor(accent); textSize = 14f; gravity = Gravity.CENTER; typeface = Roost.medium()
            background = Roost.rounded(0x0DFFFFFF, dp(14f).toFloat(), Roost.soft(accent), dp(1f))
            setPadding(0, dp(14f), 0, dp(14f))
            isClickable = true
            setOnClickListener { testFire(this) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20f) }
        })
        testPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12f), 0, 0)
            visibility = View.GONE
        }
        col.addView(testPanel)

        // --- save ---
        col.addView(TextView(this).apply {
            text = "Save Action"
            setTextColor(Roost.DOCK); textSize = 15f; gravity = Gravity.CENTER; typeface = Roost.medium()
            background = Roost.rounded(accent, dp(14f).toFloat())
            setPadding(0, dp(14f), 0, dp(14f))
            isClickable = true
            setOnClickListener { save() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24f) }
        })

        setContentView(ScrollView(this).apply {
            background = Roost.dockBackground(this@HttpActionActivity)
            addView(col)
        })

        // seed header rows
        (existing?.headers ?: parseTemplateHeaders()).forEach { (k, v) -> addHeaderRow(k, v) }

        styleMethodPills(); styleAuthPills(); renderSecret(); updateJsonHint(); refreshIcon()
    }

    override fun onResume() { super.onResume(); refreshIcon() }

    // --- dynamic sections --------------------------------------------------------------------

    private fun styleMethodPills() = methodPills.forEachIndexed { i, p ->
        stylePill(p, methods[i] == method)
    }

    private fun styleAuthPills() = authPills.forEach { p ->
        stylePill(p, p.tag == auth)
    }

    private fun stylePill(p: TextView, selected: Boolean) {
        p.setTextColor(if (selected) accent else Roost.MUTED)
        p.background = Roost.rounded(if (selected) Roost.soft(accent) else 0, dp(9f).toFloat())
    }

    // Security: changing the auth scheme must never silently carry a secret across schemes. Drop any
    // in-memory draft; renderSecret() then requires fresh entry whenever auth != the stored scheme.
    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Secret handling"
    private fun onAuthSelected(a: HttpAuth) {
        if (a != auth) {
            auth = a
            newSecret = ""
        }
        styleAuthPills(); renderSecret()
    }

    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Secret handling"
    private fun renderSecret() {
        authHint.text = when (auth) {
            HttpAuth.NONE -> "No credentials sent with the request."
            HttpAuth.BEARER -> "Bearer = a secret token sent as-is in the Authorization header."
            HttpAuth.HMAC -> "HMAC = sign the body with a shared secret; the secret never leaves the phone."
        }
        secretBox.removeAllViews()
        if (auth == HttpAuth.NONE) { secretBox.visibility = View.GONE; return }
        secretBox.visibility = View.VISIBLE

        val saved = Prefs.httpSecret(this, id)
        // Only reuse/show the stored secret while the auth scheme still matches the one it was saved
        // under — otherwise force fresh entry so a token isn't reused as a different kind of credential.
        val savedUsable = saved.isNotEmpty() && secretScheme == auth
        if (savedUsable && !secretReplacing) {
            // Show only an obscured summary + Replace — never re-display the value.
            val rowV = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = fieldBg(Roost.soft(accent))
                setPadding(dp(14f), dp(12f), dp(6f), dp(12f))
            }
            rowV.addView(TextView(this).apply {
                val kind = if (auth == HttpAuth.HMAC) "shared secret" else "token"
                text = "$kind ···· ${saved.takeLast(4)}"
                setTextColor(Roost.MUTED); textSize = 13f; typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            rowV.addView(TextView(this).apply {
                text = "Replace"; setTextColor(accent); textSize = 12f
                setPadding(dp(12f), dp(6f), dp(12f), dp(6f)); isClickable = true
                setOnClickListener { secretReplacing = true; newSecret = ""; renderSecret() }
            })
            secretBox.addView(rowV)
        } else {
            val field = EditText(this).apply {
                hint = if (auth == HttpAuth.HMAC) "Enter shared secret" else "Enter token"
                setTextColor(Roost.TEXT); setHintTextColor(Roost.MUTED)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                typeface = Typeface.MONOSPACE; textSize = 13f
                background = fieldBg(Roost.soft(accent))
                val p = dp(12f); setPadding(p, p, p, p)
                addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) { newSecret = s?.toString() ?: "" }
                    override fun beforeTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                    override fun onTextChanged(a: CharSequence?, b: Int, c: Int, d: Int) {}
                })
            }
            secretBox.addView(field)
        }
    }

    private fun addHeaderRow(k: String, v: String) {
        val rowV = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4f), 0, dp(4f))
        }
        val keyE = headerField("Header", k)
        val valE = headerField("value", v)
        rowV.addView(keyE, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rowV.addView(valE, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(7f)
        })
        val pair = keyE to valE
        headerRows.add(pair)
        rowV.addView(ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFF6E665B.toInt())
            isClickable = true
            setOnClickListener { headerRows.remove(pair); headersBox.removeView(rowV) }
            layoutParams = LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { leftMargin = dp(4f) }
        })
        headersBox.addView(rowV)
    }

    private fun updateJsonHint() {
        val valid = isValidJson(bodyEdit.text.toString())
        jsonHint.text = if (valid) "valid JSON · variables resolve at fire time" else "⚠ not valid JSON"
        jsonHint.setTextColor(if (valid) 0xFF6E665B.toInt() else Roost.AMBER)
        bodyEdit.background = fieldBg(if (valid) Roost.HAIRLINE else Roost.withAlpha(Roost.AMBER, 0x66))
    }

    private fun insertVar(v: String) {
        val start = bodyEdit.selectionStart.coerceAtLeast(0)
        bodyEdit.text.insert(start, "{{$v}}")
    }

    // --- test fire ---------------------------------------------------------------------------

    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Action builder"
    private fun testFire(btn: TextView) {
        if (!isValidJson(bodyEdit.text.toString())) {
            showTestPanel(Roost.AMBER, "BODY", "not sent",
                "Body is not valid JSON — fix it before test-firing.\n(variables like {{device}} are fine.)")
            return
        }
        val action = currentAction()
        val secret = effectiveSecret()
        btn.text = "Testing…"; btn.isClickable = false
        Thread {
            val res = HttpActionClient.fire(action, secret, HttpActionClient.defaultVars(this))
            runOnUiThread {
                btn.text = "Test Fire"; btn.isClickable = true
                val color = when {
                    res.timeout -> Roost.AMBER
                    res.ok -> Roost.SAGE
                    else -> Roost.CLAY
                }
                val statusLabel = when {
                    res.timeout -> "TIMEOUT"
                    res.code > 0 -> res.code.toString()
                    else -> "ERROR"
                }
                val redacted = HttpActionClient.redact(res.body.ifBlank { res.reason }, secret)
                showTestPanel(color, statusLabel, HttpActionClient.hostOf(action.url),
                    redacted.ifBlank { "(no body)" } + "\n\n# Authorization: •••••• (redacted)")
            }
        }.start()
    }

    private fun showTestPanel(color: Int, status: String, meta: String, body: String) {
        testPanel.removeAllViews()
        testPanel.visibility = View.VISIBLE
        testPanel.background = Roost.rounded(0x40000000, dp(13f).toFloat(), Roost.withAlpha(color, 0x4D), dp(1f))
        val hdr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(11f), dp(14f), dp(11f))
        }
        hdr.addView(TextView(this).apply {
            text = status; setTextColor(color); textSize = 11f; typeface = Typeface.MONOSPACE
            background = Roost.rounded(Roost.withAlpha(color, 0x29), dp(7f).toFloat())
            setPadding(dp(9f), dp(3f), dp(9f), dp(3f))
        })
        hdr.addView(TextView(this).apply {
            text = meta; setTextColor(0xFF8F8578.toInt()); textSize = 10.5f; typeface = Typeface.MONOSPACE
            setPadding(dp(9f), 0, 0, 0)
        })
        testPanel.addView(hdr)
        testPanel.addView(TextView(this).apply {
            text = body; setTextColor(Roost.MUTED); textSize = 11f; typeface = Typeface.MONOSPACE
            setPadding(dp(14f), dp(4f), dp(14f), dp(12f))
        })
    }

    // --- save --------------------------------------------------------------------------------

    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Secret handling"
    private fun save() {
        val title = titleEdit.text.toString().trim().ifBlank { HttpActionClient.hostOf(urlEdit.text.toString()) }
        val action = currentAction().copy()
        if (action.url.isBlank()) { toast("A URL is required"); return }

        // Security: if the auth scheme was changed and no fresh secret was entered, a secret stored
        // under the OLD scheme is still on disk — never reuse it under the new scheme. Require re-entry.
        if (auth != HttpAuth.NONE && newSecret.isBlank() && secretScheme != null && secretScheme != auth) {
            val kind = if (auth == HttpAuth.HMAC) "shared secret" else "token"
            toast("Enter the $kind for this auth")
            secretReplacing = true; renderSecret()
            return
        }

        Prefs.setHttpAction(this, action)
        when {
            auth == HttpAuth.NONE -> Prefs.setHttpSecret(this, id, null)
            newSecret.isNotBlank() -> Prefs.setHttpSecret(this, id, newSecret)
            // else: same scheme, nothing re-entered → keep the stored secret untouched.
        }
        // Enable an ActionButton(HTTP) referencing the definition by id (ADR-0002/ADR-0004).
        val button = ActionButton(ActionKind.HTTP, buttonKey(), title, id, "")
        Prefs.setActionEnabled(this, button, true)
        toast("Saved")
        finish()
    }

    private fun currentAction(): HttpAction {
        val headers = headerRows.mapNotNull { (k, v) ->
            val key = k.text.toString().trim()
            if (key.isEmpty()) null else key to v.text.toString()
        }
        return HttpAction(
            id, method, HttpActionClient.normalizeUrl(urlEdit.text.toString()),
            headers, auth, bodyEdit.text.toString()
        )
    }

    // A freshly-typed secret wins; otherwise only fall back to the stored secret while its scheme still
    // matches the selected auth — never reuse a stored credential under a different scheme (Fix 7).
    private fun effectiveSecret(): String = when {
        newSecret.isNotBlank() -> newSecret
        secretScheme == auth -> Prefs.httpSecret(this, id)
        else -> ""
    }

    private fun buttonKey() = "http:$id"

    private fun parseTemplateHeaders(): List<Pair<String, String>> = emptyList()

    private fun refreshIcon() {
        val ov = Prefs.iconOverride(this, buttonKey())?.let { IconStore.drawableFor(this, it) }
        val icon: Drawable? = ov ?: runCatching {
            resources.getDrawable(R.drawable.ic_scene, theme)?.mutate()
        }.getOrNull()
        iconImage.setImageDrawable(icon)
        if (ov == null) iconImage.setColorFilter(accent) else iconImage.clearColorFilter()
    }

    // --- helpers -----------------------------------------------------------------------------

    // Validate exactly what would be SENT: substitute vars with the same defaults fire uses
    // (HttpActionClient.substitute + defaultVars), not a placeholder "0". Otherwise a body like
    // {"pct": {{battery}}} reads "valid JSON" but fires malformed {"pct": } when the var resolves empty.
    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Action builder"
    private fun isValidJson(body: String): Boolean {
        val b = body.trim()
        if (b.isEmpty()) return true
        val resolved = HttpActionClient.substitute(b, HttpActionClient.defaultVars(this)).trim()
        if (resolved.isEmpty()) return true
        return try {
            if (resolved.startsWith("[")) JSONArray(resolved) else JSONObject(resolved)
            true
        } catch (e: Exception) { false }
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t.uppercase(); setTextColor(0xFF6E665B.toInt()); textSize = 10f
        letterSpacing = 0.15f; typeface = Typeface.MONOSPACE
        setPadding(dp(4f), dp(22f), dp(4f), dp(9f))
    }

    private fun segmentedRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        background = Roost.rounded(0x0AFFFFFF, dp(13f).toFloat(), Roost.HAIRLINE, dp(1f))
        setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun pill(label: String, onSelect: () -> Unit) = TextView(this).apply {
        text = label; textSize = 11.5f; gravity = Gravity.CENTER; typeface = Typeface.MONOSPACE
        setPadding(0, dp(9f), 0, dp(9f)); isClickable = true
        setOnClickListener { onSelect() }
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    private fun edit(hintText: String, uri: Boolean) = EditText(this).apply {
        hint = hintText
        inputType = if (uri) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        else InputType.TYPE_CLASS_TEXT
        setTextColor(Roost.TEXT); setHintTextColor(Roost.MUTED)
    }

    private fun headerField(hintText: String, value: String) = EditText(this).apply {
        hint = hintText; setText(value)
        setTextColor(Roost.TEXT); setHintTextColor(Roost.MUTED)
        inputType = InputType.TYPE_CLASS_TEXT
        typeface = Typeface.MONOSPACE; textSize = 12.5f
        background = fieldBg()
        val p = dp(11f); setPadding(p, p, p, p)
    }

    private fun fieldBg(border: Int = Roost.HAIRLINE) =
        Roost.rounded(0x0DFFFFFF, dp(12f).toFloat(), border, dp(1f))

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_ID = "http_id"
        const val EXTRA_TITLE = "http_title"
        const val EXTRA_METHOD = "http_method"
        const val EXTRA_URL = "http_url"
        const val EXTRA_AUTH = "http_auth"
        const val EXTRA_BODY = "http_body"
    }
}
