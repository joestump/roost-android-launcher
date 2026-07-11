package rocks.stump.roost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * The "presence" motif: a tiny LED-eyed robot — a rounded-rect head, two glowing circular eyes,
 * and a little antenna. Eyes widen (bigger glow) when [awake]. Pure Canvas, no raster assets.
 */
class MascotView(context: Context) : View(context) {
    var accent: Int = Roost.DEFAULT_ACCENT
        set(value) { field = value; invalidate() }
    var awake: Boolean = false
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        // Antenna: stem + glowing tip dot.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = w * 0.018f
        paint.color = Roost.withAlpha(accent, 0xCC)
        canvas.drawLine(cx, h * 0.17f, cx, h * 0.26f, paint)
        drawGlowDot(canvas, cx, h * 0.135f, w * 0.026f, w * 0.05f)

        // Head: rounded-rect panel with a faint accent-tinted outline.
        val rect = RectF(w * 0.30f, h * 0.28f, w * 0.70f, h * 0.74f)
        val rad = w * 0.14f
        paint.style = Paint.Style.FILL
        paint.color = Roost.PANEL
        canvas.drawRoundRect(rect, rad, rad, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * 0.012f
        paint.color = Roost.withAlpha(accent, 0x40)
        canvas.drawRoundRect(rect, rad, rad, paint)

        // Eyes.
        val eyeY = h * 0.50f
        val eyeR = w * 0.052f
        val glowR = eyeR + (if (awake) w * 0.11f else w * 0.05f)
        drawGlowDot(canvas, w * 0.42f, eyeY, eyeR, glowR)
        drawGlowDot(canvas, w * 0.58f, eyeY, eyeR, glowR)
    }

    private fun drawGlowDot(canvas: Canvas, cx: Float, cy: Float, r: Float, glowR: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, glowR,
            Roost.withAlpha(accent, 0x88), Roost.withAlpha(accent, 0x00), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowR, paint)
        paint.shader = null
        paint.color = accent
        canvas.drawCircle(cx, cy, r, paint)
        // Tiny specular highlight so the eye reads as a lit lens.
        paint.color = Roost.withAlpha(0xFFFFFFFF.toInt(), 0x99)
        canvas.drawCircle(cx - r * 0.28f, cy - r * 0.28f, r * 0.30f, paint)
    }
}
