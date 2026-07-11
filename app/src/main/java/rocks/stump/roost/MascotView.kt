package rocks.stump.roost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * The "presence" motif: a tiny LED-eyed robot — a rounded-rect head, two glowing rounded-rect eyes,
 * and a little antenna. Pure Canvas, no raster assets (ADR-0001).
 *
 * States, per the Roost home mockup:
 *   idle  — dim resting glow (~5px), a slow blink every few seconds.
 *   awake — brighter eyes with a gentle glow pulse (~16px), and a quicker blink.
 *
 * A single Handler-driven frame loop animates blink + pulse. It runs ONLY while the view is attached
 * to a window and is removed on detach, so it never ticks off-screen and naturally pauses with the
 * activity (no ValueAnimator/library needed).
 */
class MascotView(context: Context) : View(context) {
    var accent: Int = Roost.DEFAULT_ACCENT
        set(value) { field = value; invalidate() }
    var awake: Boolean = false
        set(value) { field = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val startedAt = SystemClock.uptimeMillis()

    private val frameHandler = Handler(Looper.getMainLooper())
    private val frame = object : Runnable {
        override fun run() {
            invalidate()
            frameHandler.postDelayed(this, FRAME_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        frameHandler.postDelayed(frame, FRAME_MS)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        frameHandler.removeCallbacks(frame)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val t = (SystemClock.uptimeMillis() - startedAt)

        // Antenna: stem + glowing tip dot.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = w * 0.018f
        paint.color = Roost.withAlpha(accent, 0xCC)
        canvas.drawLine(cx, h * 0.17f, cx, h * 0.26f, paint)
        drawTipDot(canvas, cx, h * 0.135f, w * 0.026f, w * 0.05f)

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

        // Blink: a quick eyelid close near the end of each period (faster when awake).
        val period = if (awake) BLINK_AWAKE_MS else BLINK_IDLE_MS
        val phase = (t % period.toLong()).toFloat() / period       // 0..1 within the blink cycle
        val closeAmt = ((BLINK_HALFWIDTH - abs(phase - BLINK_CENTER)) / BLINK_HALFWIDTH).coerceIn(0f, 1f)
        val eyeScaleY = 1f - 0.92f * closeAmt                       // 1 = open, ~0.08 = shut

        // Glow: a small resting halo when idle; a gentle pulse between ~9–15% when awake.
        val eyeR = w * 0.052f
        val glowExtra = if (awake) {
            val pulse = 0.5f + 0.5f * sin(t / PULSE_MS * 2f * Math.PI.toFloat())
            w * (0.09f + 0.06f * pulse)
        } else {
            // Idle "breathing": a slow softpulse (~4.2s period) so the resting halo reads alive,
            // mirroring the mockup's idle softpulse instead of a static halo.
            val breathe = 0.5f + 0.5f * sin(t / IDLE_BREATHE_MS * 2f * Math.PI.toFloat())
            w * (0.035f + 0.03f * breathe)
        }
        val glowR = eyeR + glowExtra

        val eyeY = h * 0.50f
        drawEye(canvas, w * 0.42f, eyeY, eyeR, glowR, eyeScaleY)
        drawEye(canvas, w * 0.58f, eyeY, eyeR, glowR, eyeScaleY)
    }

    /** The glowing antenna tip: soft halo + a small solid dot. */
    private fun drawTipDot(canvas: Canvas, cx: Float, cy: Float, r: Float, glowR: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, glowR,
            Roost.withAlpha(accent, 0x88), Roost.withAlpha(accent, 0x00), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowR, paint)
        paint.shader = null
        paint.color = accent
        canvas.drawCircle(cx, cy, r, paint)
    }

    /** A glowing LED eye: a soft radial halo behind a rounded-rect lens, scaled in Y to blink. */
    private fun drawEye(canvas: Canvas, cx: Float, cy: Float, r: Float, glowR: Float, eyeScaleY: Float) {
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, glowR,
            Roost.withAlpha(accent, 0x88), Roost.withAlpha(accent, 0x00), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowR, paint)
        paint.shader = null

        // The lens: a vertical rounded-rect (taller than wide), scaled down in Y to blink.
        paint.color = accent
        val halfW = r * 0.92f
        val halfH = r * 1.18f * eyeScaleY.coerceAtLeast(0.06f)
        val lens = RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
        canvas.drawRoundRect(lens, halfW, halfW, paint)

        // Tiny specular highlight so the eye reads as a lit lens (hidden while mostly shut).
        if (eyeScaleY > 0.4f) {
            paint.color = Roost.withAlpha(0xFFFFFFFF.toInt(), 0x99)
            canvas.drawCircle(cx - halfW * 0.30f, cy - halfH * 0.30f, r * 0.26f, paint)
        }
    }

    companion object {
        private const val FRAME_MS = 40L           // ~25fps; only ticks while attached
        private const val BLINK_IDLE_MS = 5400f    // slow, resting blink
        private const val BLINK_AWAKE_MS = 2600f   // quicker blink when working
        private const val BLINK_CENTER = 0.94f     // blink near the end of each cycle
        private const val BLINK_HALFWIDTH = 0.03f
        private const val PULSE_MS = 4200f         // gentle awake glow pulse period
        private const val IDLE_BREATHE_MS = 4200f  // slow idle halo breathing period
    }
}
