package rocks.stump.roost

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * A minimal, framework-only flow container (ADR-0001: no AndroidX/Compose/Flexbox).
 *
 * Lays children out left-to-right, wrapping to a new line whenever the next child would exceed the
 * available width. Horizontal and vertical gaps between children are configurable. Used for the
 * action-button pills so they wrap onto multiple lines instead of scrolling off-screen.
 */
class FlowLayout(
    context: Context,
    private val hGap: Int = 0,
    private val vGap: Int = 0
) : ViewGroup(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        // When width is unconstrained, fall back to a large bound so we don't wrap prematurely.
        val maxWidth = if (widthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else widthSize

        val childWidthSpec = if (maxWidth == Int.MAX_VALUE)
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        else
            MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
        val childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        val contentWidth = maxWidth - paddingLeft - paddingRight

        var lineWidth = 0
        var lineHeight = 0
        var totalHeight = 0
        var maxLineWidth = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            child.measure(childWidthSpec, childHeightSpec)
            val cw = child.measuredWidth
            val ch = child.measuredHeight

            if (lineWidth > 0 && lineWidth + hGap + cw > contentWidth) {
                // Wrap to the next line.
                maxLineWidth = maxOf(maxLineWidth, lineWidth)
                totalHeight += lineHeight + vGap
                lineWidth = cw
                lineHeight = ch
            } else {
                lineWidth += (if (lineWidth > 0) hGap else 0) + cw
                lineHeight = maxOf(lineHeight, ch)
            }
        }
        maxLineWidth = maxOf(maxLineWidth, lineWidth)
        totalHeight += lineHeight

        val resolvedWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            else -> maxLineWidth + paddingLeft + paddingRight
        }
        val resolvedHeight = totalHeight + paddingTop + paddingBottom
        setMeasuredDimension(resolvedWidth, resolvedHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val contentWidth = (r - l) - paddingLeft - paddingRight
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0
        var lineStart = true

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val cw = child.measuredWidth
            val ch = child.measuredHeight

            if (!lineStart && (x - paddingLeft) + hGap + cw > contentWidth) {
                // Wrap.
                x = paddingLeft
                y += lineHeight + vGap
                lineHeight = 0
                lineStart = true
            }
            if (!lineStart) x += hGap
            child.layout(x, y, x + cw, y + ch)
            x += cw
            lineHeight = maxOf(lineHeight, ch)
            lineStart = false
        }
    }
}
