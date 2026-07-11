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
 * The on-tile firing state machine of ADR-0004 — a compound row (rounded pill, fill/border tinted
 * per state) wrapping an animated [ActionDiscView] plus a label, a mono status line, an optional
 * "task" tag, and an error-expand that reveals status/reason + redacted auth + Re-fire / Dismiss.
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
    private var hostLine = ""
    private var errCode = ""
    private var errMsg = ""
    private var expanded = false

    private val disc = ActionDiscView(context).also { it.accent = accent }
    private val label = TextView(context)
    private val status = TextView(context)
    private val taskTag = TextView(context)
    private val chevron = ImageView(context)
    private val row = LinearLayout(context)
    private val expandBox = LinearLayout(context)
    private val decayHandler = Handler(Looper.getMainLooper())
    private var decay: Runnable? = null
    // An independent ceiling on the pending state: the client's per-phase socket timeouts can stack
    // (connect + read) and DNS is unbounded, so "firing…" could otherwise persist ~16s+ or forever.
    private var pendingTimeout: Runnable? = null

    private fun dp(v: Float) = Roost.dp(context, v)

    init {
        orientation = VERTICAL
        applyFill()

        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(13f), dp(11f), dp(13f), dp(11f))
        row.isClickable = true
        row.setOnClickListener { if (state != State.PENDING) onFire?.invoke() }

        disc.layoutParams = LayoutParams(dp(38f), dp(38f))
        row.addView(disc)

        val labels = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(13f) }
        }
        label.setTextColor(Roost.TEXT)
        label.textSize = 15f
        label.typeface = Roost.medium()
        label.maxLines = 1
        labels.addView(label)
        status.textSize = 10.5f
        status.typeface = Typeface.MONOSPACE
        status.setPadding(0, dp(2f), 0, 0)
        status.maxLines = 1
        labels.addView(status)
        row.addView(labels)

        taskTag.text = "TASK"
        taskTag.textSize = 8.5f
        taskTag.typeface = Typeface.MONOSPACE
        taskTag.letterSpacing = 0.06f
        taskTag.setTextColor(0xFF8F8578.toInt())
        taskTag.setPadding(dp(6f), dp(3f), dp(6f), dp(3f))
        taskTag.background = Roost.rounded(Color.TRANSPARENT, dp(6f).toFloat(), Roost.HAIRLINE, dp(1f))
        taskTag.visibility = GONE
        row.addView(taskTag, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(8f)
        })

        chevron.setImageResource(android.R.drawable.arrow_down_float)
        chevron.visibility = GONE
        chevron.setOnClickListener { toggleExpand() }
        row.addView(chevron, LayoutParams(dp(18f), dp(18f)).apply { leftMargin = dp(8f) })

        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        expandBox.orientation = VERTICAL
        expandBox.setPadding(dp(13f), 0, dp(13f), dp(13f))
        expandBox.visibility = GONE
        addView(expandBox, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        applyState()
    }

    /** One-time bind of the static content (title, idle glyph, task-ness, host). */
    fun bind(title: String, idleIcon: Drawable?, isTask: Boolean, host: String) {
        this.titleText = title
        this.isTask = isTask
        this.hostLine = host
        label.text = title
        disc.idleIcon = idleIcon
        taskTag.visibility = if (isTask) VISIBLE else GONE
        applyState()
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
        errCode = ""; setStateInternal(State.SUCCESS, statusOverride = "done · ${codeText(code)}")
        scheduleDecay(1600L)
    }

    fun showQueued(code: Int) {
        cancelDecay()
        setStateInternal(State.QUEUED, statusOverride = "queued · accepted")
        scheduleDecay(2400L)
    }

    fun showError(code: String, message: String) {
        cancelDecay()
        errCode = code.ifBlank { "ERROR" }
        errMsg = message
        setStateInternal(State.ERROR)
    }

    fun showTimeout() {
        cancelDecay()
        errCode = "TIMEOUT"
        errMsg = "no response after 8s"
        setStateInternal(State.TIMEOUT)
    }

    /** Brief success flash for tap-to-launch (shortcut) actions — no long pending. */
    fun flashSuccess() { cancelDecay(); setStateInternal(State.SUCCESS, statusOverride = "opened"); scheduleDecay(1200L) }

    private fun setStateInternal(s: State, statusOverride: String? = null) {
        if (s != State.PENDING) cancelPendingTimeout()   // any transition out of pending disarms the ceiling
        state = s
        if (s != State.ERROR && s != State.TIMEOUT) expanded = false
        disc.setState(s)
        applyState(statusOverride)
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

    private fun applyState(statusOverride: String? = null) {
        val semantic = when (state) {
            State.SUCCESS, State.QUEUED -> Roost.SAGE
            State.ERROR -> Roost.CLAY
            State.TIMEOUT -> Roost.AMBER
            else -> accent
        }
        val (statusText, statusColor) = when (state) {
            State.IDLE -> (if (isTask) "enqueue a durable task" else "tap to fire") to 0xFF8F8578.toInt()
            State.PENDING -> "firing…" to accent
            State.SUCCESS -> (statusOverride ?: "done") to Roost.SAGE
            State.QUEUED -> (statusOverride ?: "queued · accepted") to Roost.SAGE
            State.ERROR -> "failed · tap to see why" to Roost.CLAY
            State.TIMEOUT -> "timed out · 8s" to Roost.AMBER
        }
        status.text = statusText
        status.setTextColor(statusColor)

        val sticky = state == State.ERROR || state == State.TIMEOUT
        chevron.visibility = if (sticky) VISIBLE else GONE
        chevron.setColorFilter(semantic)

        applyFill()
        renderExpand(semantic)
    }

    private fun applyFill() {
        val (fill, border) = when (state) {
            State.PENDING -> Roost.soft(accent) to Roost.withAlpha(accent, 0x59)
            State.SUCCESS, State.QUEUED -> Roost.withAlpha(Roost.SAGE, 0x1A) to Roost.withAlpha(Roost.SAGE, 0x4D)
            State.ERROR -> Roost.withAlpha(Roost.CLAY, 0x1A) to Roost.withAlpha(Roost.CLAY, 0x52)
            State.TIMEOUT -> Roost.withAlpha(Roost.AMBER, 0x1A) to Roost.withAlpha(Roost.AMBER, 0x52)
            else -> 0x0AFFFFFF to Roost.HAIRLINE
        }
        background = Roost.rounded(fill, dp(18f).toFloat(), border, dp(1f))
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
            text = "POST ${hostLine.ifBlank { "endpoint" }} · Authorization: ••••••"
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
                    val gpad = (w * 0.24f).toInt()
                    ic.setBounds(gpad, gpad, (w - gpad).toInt(), (h - gpad).toInt())
                    ic.setTint(glyphPaint.color)
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
