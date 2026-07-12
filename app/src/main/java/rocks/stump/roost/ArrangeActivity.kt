package rocks.stump.roost

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "Arrange Action Buttons": lists every configured [ActionButton] in Prefs.actionButtons() order as
 * drag-handle rows, reorders them with a framework-only long-press-and-drag (no RecyclerView /
 * ItemTouchHelper), and carries a trailing on/off switch to soft-disable a button on home. The home
 * Actions zone renders in Prefs.actionButtons() order minus hidden/disabled keys, so reordering here
 * reorders the home and the switch shows/hides. Dropping persists order via Prefs.setActionButtons; the
 * switch writes the separate Prefs.disabledActionKeys set (order and enabled-state never clobber each other).
 *
 * WHY THE PRIOR ATTEMPT FAILED, AND WHAT MAKES THIS ONE WORK:
 * The gesture is handled by the *container* ([DragList]), not per-row. That matters because reordering
 * calls removeView/addView on the dragged row mid-gesture — if a row were the touch target it would get
 * ACTION_CANCEL the instant it was removed and the drag would die after the first swap. The container is
 * the touch target instead, so re-parenting children never interrupts the touch stream. Cooperation with
 * the enclosing ScrollView is the other half: on a still-finger long-press we call
 * requestDisallowInterceptTouchEvent(true) so the ScrollView stops stealing the gesture; on a pre-lift
 * move past touch-slop we let the ScrollView intercept (it CANCELs us) so normal scrolling still works.
 *
 * Governing: ADR-0005 (settings navigation IA), ADR-0002 (pluggable action-button providers),
 * ADR-0001 (framework-only)
 */
class ArrangeActivity : SettingsScreen() {

    override fun screenTitle(): String = "Arrange Action Buttons"

    override fun buildContent(body: LinearLayout) {
        body.addView(sectionHeader("Order on home", firstOnScreen = true))
        val enabled = Prefs.actionButtons(this)
        if (enabled.isEmpty()) {
            body.addView(card(listOf(hint(getString(R.string.actions_none)).apply {
                setPadding(dp(16f), dp(16f), dp(16f), dp(16f))
            })))
            return
        }
        val list = DragList(this)
        enabled.forEach { list.addView(dragRow(it)) }
        body.addView(list)
        body.addView(hint("Flip a button off to hide it from the home Actions zone, or long-press its handle and drag to reorder."))
    }

    private fun kindLabel(k: ActionKind): String = when (k) {
        ActionKind.HTTP -> "HTTP action"
        ActionKind.HASS_SCENE -> "Home Assistant scene"
        ActionKind.SHORTCUT -> "App shortcut"
    }

    /**
     * One draggable row: a drag-handle affordance + the button label + its kind. The [ActionButton] rides
     * on the view tag so [DragList] can read back the committed order on drop. Each row is its own rounded
     * card (uniform height, one-line label) so swaps are exact top-swaps. Deliberately NOT clickable, so
     * ACTION_DOWN falls through to the container's onTouchEvent.
     */
    private fun dragRow(b: ActionButton): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(rowBg(), dp(14f).toFloat(), rowBorder(), dp(1f))
            setPadding(dp(12f), dp(14f), dp(14f), dp(14f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
            tag = b
        }
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_drag_handle)
            setColorFilter(SUBTLE)
            layoutParams = LinearLayout.LayoutParams(dp(20f), dp(20f)).apply { rightMargin = dp(12f) }
        })
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        stack.addView(TextView(this).apply {
            text = if (b.kind == ActionKind.SHORTCUT) ShortcutProvider.displayTitle(b, null) else b.title
            setTextColor(ROW_LABEL); textSize = 14.5f; maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        stack.addView(TextView(this).apply {
            text = kindLabel(b.kind); setTextColor(SUBTLE); textSize = 11.5f
            setPadding(0, dp(2f), 0, 0)
        })
        row.addView(stack)
        // Trailing on/off switch: a SOFT disable for the home Actions zone that KEEPS the button in
        // action_buttons (so SHORTCUT/HASS args survive and it can be re-enabled from here, which has no
        // live scanner) and is orthogonal to drag order. It's the only clickable child, at the trailing
        // edge, so a touch on it toggles while the handle + label still start a drag (they're non-clickable,
        // so their ACTION_DOWN falls through to the DragList container). Uniform row height is unaffected —
        // the 25dp switch is shorter than the two-line label stack.
        // "Off home" has two sources: this soft disable AND the home long-press "Hide". The switch is the
        // single on/off control, so it reflects EITHER being off and, when turned back on, clears BOTH —
        // otherwise a hidden button would show ON here yet stay off home and the switch couldn't restore it.
        val offHome = Prefs.isActionDisabled(this, b.key) || Prefs.isHidden(this, b.key)
        stack.alpha = if (offHome) 0.45f else 1f
        row.addView(toggle(initial = !offHome) { checked ->
            Prefs.setActionDisabled(this, b.key, !checked)
            if (checked) Prefs.setHidden(this, b.key, false)
            stack.alpha = if (checked) 1f else 0.45f
        }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.leftMargin = dp(10f)
        })
        return row
    }

    /**
     * The reorder column. All touch handling lives here (container-level) — see the class KDoc for why.
     * On drop, [persist] writes the live child order back to Prefs, which is exactly the home order.
     */
    private inner class DragList(context: Context) : LinearLayout(context) {

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val handler = Handler(Looper.getMainLooper())
        private val longPressMs = 300L

        private var candidate: View? = null
        private var dragging: View? = null
        private var downRawY = 0f
        private var lastRawY = 0f
        private val startDrag = Runnable { candidate?.let { lift(it) } }

        init {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        private fun childUnder(y: Float): View? {
            for (i in 0 until childCount) {
                val c = getChildAt(i)
                if (y >= c.top && y <= c.bottom) return c
            }
            return null
        }

        // Rows aren't clickable, so ACTION_DOWN is not consumed by any child and lands here; returning
        // true makes the container the touch target for the whole gesture. The ScrollView can still
        // intercept later MOVEs (sending us CANCEL) until we disallow it on lift.
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    candidate = childUnder(ev.y)
                    downRawY = ev.rawY
                    lastRawY = ev.rawY
                    dragging = null
                    if (candidate != null) handler.postDelayed(startDrag, longPressMs)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging == null) {
                        // A pre-lift move beyond slop is a scroll, not a drag: drop the pending pick-up.
                        // The ScrollView will intercept and take over (we'll receive CANCEL).
                        if (Math.abs(ev.rawY - downRawY) > touchSlop) handler.removeCallbacks(startDrag)
                        return true
                    }
                    val d = dragging!!
                    d.translationY += ev.rawY - lastRawY
                    lastRawY = ev.rawY
                    maybeSwap(d)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(startDrag)
                    dragging?.let { drop(it) }
                    candidate = null
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }

        private fun lift(v: View) {
            dragging = v
            candidate = null
            lastRawY = downRawY   // anchor deltas to where the finger has been resting
            v.alpha = 0.9f
            v.scaleX = 1.02f; v.scaleY = 1.02f
            v.translationZ = dp(8f).toFloat()
            // Stop the ScrollView from stealing the gesture now that we own it.
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        private fun maybeSwap(d: View) {
            val idx = indexOfChild(d)
            val center = d.top + d.height / 2f + d.translationY
            if (idx > 0) {
                val prev = getChildAt(idx - 1)
                if (center < prev.top + prev.height / 2f) { swap(d, idx, idx - 1); return }
            }
            if (idx < childCount - 1) {
                val next = getChildAt(idx + 1)
                if (center > next.top + next.height / 2f) { swap(d, idx, idx + 1); return }
            }
        }

        // Swap [d] with the neighbour at [to], keeping [d] visually pinned under the finger. Rows are
        // uniform height, so [d]'s layout top shifts by exactly (neighbour height + its bottom margin);
        // compensate translationY by that so there's no jump and no swap ping-pong.
        private fun swap(d: View, from: Int, to: Int) {
            val neighbour = getChildAt(to)
            val shift = (neighbour.height + dp(8f)).toFloat()
            removeViewAt(from)
            addView(d, to)
            d.translationY += if (to > from) -shift else shift
        }

        private fun drop(d: View) {
            d.animate().translationY(0f).setDuration(120).start()
            d.alpha = 1f
            d.scaleX = 1f; d.scaleY = 1f
            d.translationZ = 0f
            dragging = null
            parent?.requestDisallowInterceptTouchEvent(false)
            persist()
        }

        private fun persist() {
            val ordered = (0 until childCount).mapNotNull { getChildAt(it).tag as? ActionButton }
            Prefs.setActionButtons(this@ArrangeActivity, ordered)
        }
    }
}
