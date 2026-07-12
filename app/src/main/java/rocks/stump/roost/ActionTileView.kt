package rocks.stump.roost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The on-tile firing state machine of ADR-0004 — an animated [ActionDiscView] plus a label, a mono
 * status line, an optional "task" tag, and an error-expand that reveals status/reason + redacted auth
 * + Re-fire / Dismiss.
 *
 * Renders in one of three owner-chosen [ActionDensity] layouts (SLIM / REGULAR / RICH) — see
 * [rebuildViews]. All three share ONE state model + the single pending-ring animation + the 8s
 * timeout; only the disc size, spacing, and chrome change per density.
 *
 * The whole idle → pending → success/queued → error → timeout story lives on the control — no Toast.
 * Health colors are the fixed semantic ramp (Sage/Amber/Clay), never the accent.
 *
 * Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "On-tile firing state machine"
 */
class ActionTileView(context: Context, private val accent: Int) : LinearLayout(context) {

    enum class State { IDLE, PENDING, SUCCESS, QUEUED, ERROR, TIMEOUT }

    /** Fired when the user taps the tile while idle (or Re-fires from the error panel). */
    var onFire: (() -> Unit)? = null

    /** True for durable-task endpoints — reads "queued", carries a `task` tag, longer hold. */
    var isTask: Boolean = false

    private var state = State.IDLE
    private var titleText = ""
    // The per-tile subtitle line (a kind-specific tagline from the tile's metadata — a host, "scene",
    // "shortcut", "METHOD · host", …). Stored in hostLine (the field name predates the uniform model).
    private var hostLine = ""
    // The IDLE status text — the action line shown on every tile: fire kinds carry "tap to fire",
    // launch kinds their verb ("tap to open" / "tap to run"). SLIM shows a terse fire state ("ready")
    // instead, but only for fire kinds; launch kinds keep their verb (owner: v0.9.1).
    private var idleStatus = "tap to fire"
    // Whether this tile fires (HTTP / HASS_SCENE) vs. launches (APP / WEB / SHORTCUT). Drives SLIM's
    // terse IDLE wording: fire → "ready", launch → the action verb.
    private var isFireKind = true
    private var showStatus = true
    private var density = ActionDensity.REGULAR
    private var errCode = ""
    private var errMsg = ""
    private var expanded = false
    // Last HTTP status code (for SLIM's terse "200 OK") + an optional flash string ("opened") that
    // wins over the computed success text for tap-to-launch SHORTCUT actions.
    private var lastCode = 0
    private var flashText: String? = null

    private val disc = ActionDiscView(context).also { it.accent = accent }
    private val label = TextView(context)
    private val status = TextView(context)
    private val hostLabel = TextView(context)      // RICH: the subtitle line under the label
    private val taskTag = TextView(context)
    private val chevron = ImageView(context)
    private val row = LinearLayout(context)
    private val labels = LinearLayout(context)     // REGULAR: the label-over-status stack
    private val expandBox = LinearLayout(context)
    private val decayHandler = Handler(Looper.getMainLooper())
    private var decay: Runnable? = null
    // An independent ceiling on the pending state: the client's per-phase socket timeouts can stack
    // (connect + read) and DNS is unbounded, so "firing…" could otherwise persist ~16s+ or forever.
    private var pendingTimeout: Runnable? = null

    private fun dp(v: Float) = Roost.dp(context, v)

    init {
        orientation = VERTICAL

        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        // The WHOLE card fires (not just the disc row): tapping anywhere on the tile invokes the action,
        // and long-press bubbles to the card's own long-click listener (set by MainActivity.tileMenu).
        // The inner row is left non-clickable so its taps fall through to the card.
        isClickable = true
        setOnClickListener { if (state != State.PENDING) onFire?.invoke() }

        // View identities are stable across densities (so the disc keeps its animation + the state
        // model is untouched); only sizes/typefaces/parents are re-applied per density in rebuildViews.
        label.setTextColor(Roost.TEXT)
        label.maxLines = 1
        label.ellipsize = android.text.TextUtils.TruncateAt.END
        status.typeface = Typeface.MONOSPACE
        status.maxLines = 1
        status.ellipsize = android.text.TextUtils.TruncateAt.END

        // RICH host line — dim mono, single-line ellipsis. Text/visibility set per-build.
        hostLabel.typeface = Typeface.MONOSPACE
        hostLabel.textSize = 9f
        hostLabel.setTextColor(0xFF6E665B.toInt())
        hostLabel.letterSpacing = 0.04f
        hostLabel.maxLines = 1
        hostLabel.ellipsize = android.text.TextUtils.TruncateAt.END

        taskTag.text = "task"
        taskTag.textSize = 8f
        taskTag.typeface = Typeface.MONOSPACE
        taskTag.letterSpacing = 0.06f
        taskTag.setTextColor(0xFF8F8578.toInt())
        taskTag.setPadding(dp(5f), dp(2f), dp(5f), dp(2f))
        taskTag.background = Roost.rounded(Color.TRANSPARENT, dp(5f).toFloat(), Roost.HAIRLINE, dp(1f))

        chevron.setImageResource(android.R.drawable.arrow_down_float)
        chevron.setOnClickListener { toggleExpand() }

        expandBox.orientation = VERTICAL
        expandBox.setPadding(dp(13f), 0, dp(13f), dp(13f))
        expandBox.visibility = GONE

        rebuildViews()
        applyState()
    }

    /** One-time bind of the static content (title, idle glyph, task-ness, subtitle) + [density].
     *  [subtitle] is the kind-specific tagline shown under the label; [idleStatus] is the action line
     *  (fire kinds "tap to fire", launch kinds their verb). [isFireKind] drives SLIM's terse IDLE
     *  wording — "ready" for fire kinds, the [idleStatus] verb for launch kinds. [showStatus] gates
     *  whether the status line renders at all. [tintIdleIcon] is false for full-color app/shortcut
     *  icons + overrides so they aren't accent-washed. */
    fun bind(
        title: String, idleIcon: Drawable?, isTask: Boolean, subtitle: String,
        density: ActionDensity, idleStatus: String = "tap to fire", showStatus: Boolean = true,
        tintIdleIcon: Boolean = true, isFireKind: Boolean = true
    ) {
        this.titleText = title
        this.isTask = isTask
        this.hostLine = subtitle
        this.idleStatus = idleStatus
        this.showStatus = showStatus
        this.isFireKind = isFireKind
        this.density = density
        label.text = title
        disc.idleIcon = idleIcon
        disc.tintIdleIcon = tintIdleIcon
        rebuildViews()
        applyState()
    }

    // --- density layouts ---------------------------------------------------------------------
    // The three DENSITY layouts (SPEC-0002), realizing the FINAL "Actions zone density" design. One
    // state model + one pending-ring animation + the 8s timeout drive all three; only the disc size,
    // spacing, and chrome change. SLIM is a compact per-state-tinted card row (24dp disc), REGULAR the
    // balanced default card (36dp disc + label-over-status + a "task" chip), RICH a tall 2-column grid
    // card (42dp disc on top, then label + a "METHOD · host" line + a bottom status). The RICH cards are
    // laid into a 2-up grid by the caller (MainActivity.actionsZone); SLIM/REGULAR stay a vertical list.
    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "On-tile firing state machine"
    private fun rebuildViews() {
        removeAllViews()
        row.removeAllViews()
        labels.removeAllViews()
        setPadding(0, 0, 0, 0)
        // The row is a SHARED view; reset its padding each rebuild so a prior density's row padding
        // (buildSlim/buildRegular set one; the constructor runs buildRegular before bind() picks RICH)
        // doesn't leak into RICH and indent/offset its top-left icon.
        row.setPadding(0, 0, 0, 0)
        minimumHeight = 0
        row.gravity = Gravity.CENTER_VERTICAL
        when (density) {
            ActionDensity.SLIM -> buildSlim()
            ActionDensity.REGULAR -> buildRegular()
            ActionDensity.RICH -> buildRich()
        }
    }

    // SLIM: a compact card row (radius 12, per-state tint applied in applyFill) — a 24dp disc, the label,
    // and a terse mono status on the right. No task chip, no expand. The pending ring scales to the 24dp
    // disc automatically (ActionDiscView draws it relative to its own size).
    private fun buildSlim() {
        row.setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
        disc.layoutParams = LayoutParams(dp(24f), dp(24f))
        row.addView(disc)

        labels.orientation = VERTICAL
        label.textSize = 13.5f
        label.typeface = Typeface.DEFAULT
        labels.addView(label)
        hostLabel.text = hostLine
        hostLabel.visibility = if (hostLine.isBlank()) GONE else VISIBLE
        hostLabel.setPadding(0, dp(1f), 0, 0)
        labels.addView(hostLabel)
        row.addView(labels, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(11f) })

        status.textSize = 9.5f
        status.setPadding(0, 0, 0, 0)
        row.addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8f)
        })

        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    // REGULAR: the default tile — 36dp disc + label-over-status stack inside a per-state tinted card
    // (radius 16). A "task" chip rides the right for durable tasks; the error/timeout expand chevron
    // appears only when sticky, preserving the SPEC-0002 error detail panel.
    private fun buildRegular() {
        row.setPadding(dp(14f), dp(12f), dp(14f), dp(12f))
        disc.layoutParams = LayoutParams(dp(36f), dp(36f))
        row.addView(disc)

        labels.orientation = VERTICAL
        label.textSize = 14.5f
        label.typeface = Roost.medium()
        labels.addView(label)
        // Subtitle (metadata) line between title and the action line — all three lines on every tile.
        hostLabel.text = hostLine
        hostLabel.visibility = if (hostLine.isBlank()) GONE else VISIBLE
        hostLabel.setPadding(0, dp(1f), 0, 0)
        labels.addView(hostLabel)
        status.textSize = 10f
        status.setPadding(0, dp(2f), 0, 0)
        labels.addView(status)
        row.addView(labels, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(12f) })

        row.addView(taskTag, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8f)
        })
        row.addView(chevron, LayoutParams(dp(18f), dp(18f)).apply { leftMargin = dp(8f) })

        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(expandBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    // RICH: a tall grid CARD (radius 17, per-state tint, min-height ~128dp). The tile IS the card here:
    // a top row (42dp disc + a top-right "task" chip for durable tasks), then the label, then the
    // per-kind subtitle line (blank → collapsed), then the mono status pinned to the bottom. Tapping in
    // the error state re-fires ("tap to retry"); the inline expand panel is reserved for REGULAR (a grid
    // cell has no room for it) — SLIM likewise carries no expand.
    private fun buildRich() {
        setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
        minimumHeight = dp(128f)

        // Top row: the disc, and (for durable tasks) a "task" chip pinned top-right. The RICH glyph fills
        // the disc (low inset) so the icon reads flush-left, its left edge on the card's text column.
        row.gravity = Gravity.TOP
        disc.iconInset = 0.06f
        disc.layoutParams = LayoutParams(dp(42f), dp(42f))
        row.addView(disc)
        row.addView(View(context).apply { layoutParams = LayoutParams(0, dp(1f), 1f) })
        row.addView(taskTag, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(9f)
        })

        label.textSize = 15f
        label.typeface = Roost.medium()
        addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        // The per-kind subtitle line. Now that every tile is uniform, a blank subtitle collapses (GONE)
        // rather than reserving space — the grid's MATCH_PARENT height equalization keeps paired cards even.
        hostLabel.text = hostLine
        hostLabel.visibility = if (hostLine.isBlank()) GONE else VISIBLE
        addView(hostLabel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(3f)
        })

        // Weighted spacer pushes the status to the bottom of the min-height card.
        addView(View(context).apply { layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f) })

        status.textSize = 10f
        status.setPadding(0, dp(6f), 0, 0)
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun isPending() = state == State.PENDING

    // --- state transitions -------------------------------------------------------------------

    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "8s → timeout"
    fun showPending() {
        cancelDecay()
        setStateInternal(State.PENDING)
        // Arm an 8s ceiling: if no result lands (DNS blackhole, stacked socket timeouts) the tile falls
        // to TIMEOUT on its own. Any real result cancels this via setStateInternal leaving PENDING.
        pendingTimeout = Runnable { if (state == State.PENDING) showTimeout() }
        decayHandler.postDelayed(pendingTimeout!!, PENDING_TIMEOUT_MS)
    }

    fun showSuccess(code: Int) {
        cancelDecay()
        errCode = ""; flashText = null; lastCode = code
        setStateInternal(State.SUCCESS)
        scheduleDecay(1600L)
    }

    fun showQueued(code: Int) {
        cancelDecay()
        flashText = null; lastCode = code
        setStateInternal(State.QUEUED)
        scheduleDecay(2400L)
    }

    fun showError(code: String, message: String) {
        cancelDecay()
        flashText = null
        errCode = code.ifBlank { "ERROR" }
        errMsg = message
        setStateInternal(State.ERROR)
    }

    fun showTimeout() {
        cancelDecay()
        flashText = null
        errCode = "TIMEOUT"
        errMsg = "no response after 8s"
        setStateInternal(State.TIMEOUT)
    }

    /** Brief success flash for tap-to-launch (shortcut) actions — no long pending. */
    fun flashSuccess() { cancelDecay(); flashText = "opened"; setStateInternal(State.SUCCESS); scheduleDecay(1200L) }

    private fun setStateInternal(s: State) {
        if (s != State.PENDING) cancelPendingTimeout()   // any transition out of pending disarms the ceiling
        state = s
        if (s != State.ERROR && s != State.TIMEOUT) expanded = false
        disc.setState(s)
        applyState()
    }

    private fun scheduleDecay(ms: Long) {
        decay = Runnable { setStateInternal(State.IDLE) }
        decayHandler.postDelayed(decay!!, ms)
    }

    private fun cancelDecay() { decay?.let { decayHandler.removeCallbacks(it) }; decay = null }

    private fun cancelPendingTimeout() {
        pendingTimeout?.let { decayHandler.removeCallbacks(it) }; pendingTimeout = null
    }

    private fun toggleExpand() {
        if (state != State.ERROR && state != State.TIMEOUT) return
        expanded = !expanded
        applyState()
    }

    // --- rendering ---------------------------------------------------------------------------

    private fun applyState() {
        val semantic = when (state) {
            State.SUCCESS, State.QUEUED -> Roost.SAGE
            State.ERROR -> Roost.CLAY
            State.TIMEOUT -> Roost.AMBER
            else -> accent
        }

        // A quiet launch tile (showStatus=false) has no idle/success status line; only an actual fire
        // event (pending/error/timeout — never reached by launch kinds) would surface one.
        val fireState = state == State.PENDING || state == State.ERROR || state == State.TIMEOUT
        val statusVisible = showStatus || fireState

        if (density == ActionDensity.SLIM) {
            // SLIM: a terse mono code, coloured per state; the label stays TEXT (the card tint + the
            // status carry the state read).
            val slimText = when (state) {
                // Launch tiles never fire, so their terse IDLE reads as the action verb, not "ready".
                State.IDLE -> if (isFireKind) "ready" else idleStatus
                State.PENDING -> "firing…"
                State.SUCCESS -> flashText ?: codeText(lastCode)   // "200 OK"
                State.QUEUED -> "queued"
                State.ERROR -> errCode                             // the bare code, e.g. "502"
                State.TIMEOUT -> "timeout"
            }
            val slimColor = when (state) {
                State.IDLE -> Roost.MUTED
                State.PENDING -> accent
                else -> semantic
            }
            status.text = slimText
            status.setTextColor(slimColor)
            status.visibility = if (statusVisible) VISIBLE else GONE
            label.setTextColor(Roost.TEXT)
        } else {
            val (statusText, statusColor) = when (state) {
                State.IDLE -> (if (isTask) "enqueue a durable task" else idleStatus) to 0xFF8F8578.toInt()
                State.PENDING -> "firing…" to accent
                State.SUCCESS -> (flashText ?: "done · ${codeText(lastCode)}") to Roost.SAGE
                State.QUEUED -> "queued · accepted" to Roost.SAGE
                State.ERROR -> "failed · $errCode · tap to retry" to Roost.CLAY
                State.TIMEOUT -> "timed out · 8s" to Roost.AMBER
            }
            status.text = statusText
            status.setTextColor(statusColor)
            status.visibility = if (statusVisible) VISIBLE else GONE
            label.setTextColor(Roost.TEXT)
        }

        // TASK chip belongs to the carded densities only; SLIM stays a terse one-liner.
        taskTag.visibility = if (isTask && density != ActionDensity.SLIM) VISIBLE else GONE

        // The error/timeout expand chevron lives on REGULAR only (SLIM + the grid-cell RICH have no room).
        val sticky = state == State.ERROR || state == State.TIMEOUT
        chevron.visibility = if (sticky && density == ActionDensity.REGULAR) VISIBLE else GONE
        chevron.setColorFilter(semantic)

        applyFill()
        renderExpand(semantic)
    }

    private fun applyFill() {
        when (density) {
            ActionDensity.SLIM -> {
                val (fill, border) = slimTint()
                background = Roost.rounded(fill, dp(12f).toFloat(), border, dp(1f))
            }
            ActionDensity.REGULAR -> {
                val (fill, border) = regularTint()
                background = Roost.rounded(fill, dp(16f).toFloat(), border, dp(1f))
            }
            ActionDensity.RICH -> {
                val (fill, border) = richTint()
                background = Roost.rounded(fill, dp(17f).toFloat(), border, dp(1f))
            }
        }
    }

    // SLIM: a gentle per-state wash on the compact card — soft-accent while pending, faint Sage/Clay/
    // Amber otherwise; idle is the neutral 0.04 white fill + 0.07 white border of the design.
    private fun slimTint(): Pair<Int, Int> = when (state) {
        State.PENDING -> Roost.soft(accent) to Roost.withAlpha(accent, 0x59)
        State.SUCCESS, State.QUEUED -> Roost.withAlpha(Roost.SAGE, 0x14) to Roost.withAlpha(Roost.SAGE, 0x3D)
        State.ERROR -> Roost.withAlpha(Roost.CLAY, 0x14) to Roost.withAlpha(Roost.CLAY, 0x40)
        State.TIMEOUT -> Roost.withAlpha(Roost.AMBER, 0x14) to Roost.withAlpha(Roost.AMBER, 0x40)
        else -> Roost.withAlpha(0xFFFFFFFF.toInt(), 0x0A) to Roost.withAlpha(0xFFFFFFFF.toInt(), 0x12)
    }

    private fun regularTint(): Pair<Int, Int> = when (state) {
        State.PENDING -> Roost.soft(accent) to Roost.withAlpha(accent, 0x59)
        State.SUCCESS, State.QUEUED -> Roost.withAlpha(Roost.SAGE, 0x1A) to Roost.withAlpha(Roost.SAGE, 0x4D)
        State.ERROR -> Roost.withAlpha(Roost.CLAY, 0x1A) to Roost.withAlpha(Roost.CLAY, 0x52)
        State.TIMEOUT -> Roost.withAlpha(Roost.AMBER, 0x1A) to Roost.withAlpha(Roost.AMBER, 0x52)
        else -> 0x0AFFFFFF to Roost.HAIRLINE
    }

    // RICH: a stronger per-state wash than REGULAR so the fuller card reads its state at a glance.
    private fun richTint(): Pair<Int, Int> = when (state) {
        State.PENDING -> Roost.withAlpha(accent, 0x3D) to Roost.withAlpha(accent, 0x73)
        State.SUCCESS, State.QUEUED -> Roost.withAlpha(Roost.SAGE, 0x2E) to Roost.withAlpha(Roost.SAGE, 0x66)
        State.ERROR -> Roost.withAlpha(Roost.CLAY, 0x2E) to Roost.withAlpha(Roost.CLAY, 0x70)
        State.TIMEOUT -> Roost.withAlpha(Roost.AMBER, 0x2E) to Roost.withAlpha(Roost.AMBER, 0x70)
        else -> 0x12FFFFFF to Roost.withAlpha(0xFFFFFFFF.toInt(), 0x1A)
    }

    private fun renderExpand(semantic: Int) {
        expandBox.removeAllViews()
        if (!expanded || (state != State.ERROR && state != State.TIMEOUT)) {
            expandBox.visibility = GONE
            return
        }
        expandBox.visibility = VISIBLE

        expandBox.addView(View(context).apply {
            setBackgroundColor(Roost.HAIRLINE)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1f)).apply { bottomMargin = dp(11f) }
        })
        expandBox.addView(TextView(context).apply {
            text = "$errCode  $errMsg"
            setTextColor(semantic)
            textSize = 11f
            typeface = Typeface.MONOSPACE
        })
        expandBox.addView(TextView(context).apply {
            // The request preview — auth is shown only as a mask (never the secret). SPEC-0002.
            text = "${hostLine.ifBlank { "endpoint" }} · Authorization: ••••••"
            setTextColor(0xFF6E665B.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, dp(4f), 0, 0)
        })

        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(12f), 0, 0)
        }
        actions.addView(pillButton("Re-fire", accent, Roost.soft(accent)) {
            expanded = false; cancelDecay(); onFire?.invoke()
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(pillButton("Dismiss", Roost.MUTED, 0x0AFFFFFF) {
            setStateInternal(State.IDLE)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8f) })
        expandBox.addView(actions)
    }

    private fun pillButton(text: String, fg: Int, bg: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            setTextColor(fg)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, dp(10f))
            background = Roost.rounded(bg, dp(11f).toFloat(), Roost.HAIRLINE, dp(1f))
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun codeText(code: Int): String = when {
        code in 200..299 -> "$code OK"
        code in 100..599 -> "$code"
        else -> "ok"
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelDecay()
        cancelPendingTimeout()
    }

    companion object {
        /** Independent on-tile ceiling for the pending state — realizes SPEC-0002 "8s → timeout". */
        private const val PENDING_TIMEOUT_MS = 8000L
    }
}

/**
 * The animated status disc — a [Canvas] View that draws a rounded-rect disc, a state glyph, and, in
 * the pending state, a sweeping accent arc driven by a Handler(16ms) invalidate() loop. The loop runs
 * ONLY while pending AND the window is visible, and stops the instant the result lands or the view is
 * hidden/detached (battery — same lifecycle discipline as the 1s rateTick in MainActivity).
 *
 * Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "On-tile firing state machine"
 */
class ActionDiscView(context: Context) : View(context) {

    var accent: Int = Roost.DEFAULT_ACCENT
    var idleIcon: Drawable? = null

    // Whether the idle glyph should be accent-tinted. TRUE for the built-in monochrome vector glyphs
    // (ic_scene, the drawn bolt) so they read as part of the themed disc; FALSE for full-color app /
    // shortcut launcher icons and user-picked icon overrides — tinting those would flatten them to a
    // solid accent silhouette. (Fix 3.)
    var tintIdleIcon: Boolean = true

    // Fraction of the disc size the idle glyph is inset by (its padding inside the chip). Default keeps
    // the glyph comfortably centered; RICH lowers it so the icon sits nearly flush-left, aligned with the
    // card's text column (owner request).
    var iconInset: Float = 0.24f

    private var state = ActionTileView.State.IDLE
    private var sweep = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val ring = RectF()

    private val handler = Handler(Looper.getMainLooper())
    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            sweep = (sweep + 12f) % 360f
            invalidate()
            if (ticking) handler.postDelayed(this, 16L)
        }
    }

    fun setState(s: ActionTileView.State) {
        state = s
        if (s == ActionTileView.State.PENDING) startTick() else stopTick()
        invalidate()
    }

    private fun startTick() {
        if (ticking || windowVisibility != VISIBLE) return
        ticking = true
        handler.postDelayed(tick, 16L)
    }

    private fun stopTick() {
        ticking = false
        handler.removeCallbacks(tick)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && state == ActionTileView.State.PENDING) startTick() else stopTick()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopTick()
    }

    private fun withAlpha(color: Int, a: Int) = Roost.withAlpha(color, a)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = w * 0.02f
        rect.set(pad, pad, w - pad, h - pad)
        val radius = w * 0.32f

        val semantic = when (state) {
            ActionTileView.State.SUCCESS, ActionTileView.State.QUEUED -> Roost.SAGE
            ActionTileView.State.ERROR -> Roost.CLAY
            ActionTileView.State.TIMEOUT -> Roost.AMBER
            else -> accent
        }
        fillPaint.color = withAlpha(semantic, 0x29)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        // glyph
        val inset = w * 0.28f
        val gr = RectF(inset, inset, w - inset, h - inset)
        glyphPaint.color = when (state) {
            ActionTileView.State.PENDING -> withAlpha(accent, 0x80)
            else -> semantic
        }
        glyphPaint.strokeWidth = w * 0.055f
        when (state) {
            ActionTileView.State.IDLE, ActionTileView.State.PENDING -> {
                val ic = idleIcon
                if (ic != null) {
                    val gpad = (w * iconInset).toInt()
                    ic.setBounds(gpad, gpad, (w - gpad).toInt(), (h - gpad).toInt())
                    // Only tint monochrome vector glyphs; leave full-color app/shortcut icons and picked
                    // overrides untinted so they render in their real colors, like the home app grid. (Fix 3.)
                    if (tintIdleIcon) ic.setTint(glyphPaint.color) else ic.setTintList(null)
                    ic.draw(canvas)
                } else {
                    drawBolt(canvas, gr)
                }
            }
            ActionTileView.State.SUCCESS -> drawCheck(canvas, gr)
            ActionTileView.State.QUEUED -> drawPlane(canvas, gr)
            ActionTileView.State.ERROR -> drawX(canvas, gr)
            ActionTileView.State.TIMEOUT -> drawClock(canvas, gr)
        }

        if (state == ActionTileView.State.PENDING) {
            val ro = w * 0.04f
            ring.set(-ro, -ro, w + ro, h + ro)
            val r2 = w * 0.02f
            ringPaint.strokeWidth = w * 0.055f
            ringPaint.color = withAlpha(accent, 0x2E)
            canvas.drawRoundRect(ring, radius + r2, radius + r2, ringPaint)
            ringPaint.color = accent
            canvas.drawArc(ring, sweep, 90f, false, ringPaint)
        }
    }

    // --- glyphs (24x24 viewBox mapped into the glyph rect) ------------------------------------

    private fun map(gr: RectF, x: Float, y: Float): FloatArray =
        floatArrayOf(gr.left + gr.width() * (x / 24f), gr.top + gr.height() * (y / 24f))

    private fun drawBolt(canvas: Canvas, gr: RectF) {
        val p = Path()
        val pts = arrayOf(13f to 3f, 5f to 13f, 11f to 13f, 10f to 21f, 18f to 11f, 12f to 11f, 13f to 3f)
        pts.forEachIndexed { i, (x, y) ->
            val m = map(gr, x, y)
            if (i == 0) p.moveTo(m[0], m[1]) else p.lineTo(m[0], m[1])
        }
        canvas.drawPath(p, glyphPaint)
    }

    private fun drawCheck(canvas: Canvas, gr: RectF) {
        val p = Path()
        val a = map(gr, 5f, 13f); val b = map(gr, 10f, 18f); val c = map(gr, 19f, 7f)
        p.moveTo(a[0], a[1]); p.lineTo(b[0], b[1]); p.lineTo(c[0], c[1])
        canvas.drawPath(p, glyphPaint)
    }

    private fun drawPlane(canvas: Canvas, gr: RectF) {
        val p = Path()
        val pts = arrayOf(4f to 12f, 20f to 5f, 13f to 21f, 11f to 15f, 4f to 12f)
        pts.forEachIndexed { i, (x, y) ->
            val m = map(gr, x, y)
            if (i == 0) p.moveTo(m[0], m[1]) else p.lineTo(m[0], m[1])
        }
        canvas.drawPath(p, glyphPaint)
    }

    private fun drawX(canvas: Canvas, gr: RectF) {
        val a = map(gr, 7f, 7f); val b = map(gr, 17f, 17f)
        val c = map(gr, 17f, 7f); val d = map(gr, 7f, 17f)
        canvas.drawLine(a[0], a[1], b[0], b[1], glyphPaint)
        canvas.drawLine(c[0], c[1], d[0], d[1], glyphPaint)
    }

    private fun drawClock(canvas: Canvas, gr: RectF) {
        val cx = gr.centerX(); val cy = gr.centerY()
        val r = gr.width() * (8f / 24f)
        canvas.drawCircle(cx, cy, r, glyphPaint)
        val top = map(gr, 12f, 8f); val mid = map(gr, 12f, 12f); val hand = map(gr, 15.5f, 13.5f)
        val p = Path()
        p.moveTo(top[0], top[1]); p.lineTo(mid[0], mid[1]); p.lineTo(hand[0], hand[1])
        canvas.drawPath(p, glyphPaint)
    }
}
