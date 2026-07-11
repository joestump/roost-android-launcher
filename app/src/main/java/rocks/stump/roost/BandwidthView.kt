package rocks.stump.roost

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.view.View

/**
 * A subtle live network "heartbeat" along the bottom edge: two auto-scaling sparklines — download and
 * upload — drawn in the accent color at low alpha. Polls TrafficStats ~1/s while attached and stops
 * when detached (battery). Framework-only per ADR-0001.
 *
 * Governing: SPEC roadmap — Gitea issue #1 (bandwidth heartbeat).
 */
class BandwidthView(context: Context) : View(context) {

    var accent: Int = Roost.DEFAULT_ACCENT
    /** Called on each sample (main thread) with bytes/sec since the last tick. */
    var onSample: ((rxPerSec: Long, txPerSec: Long) -> Unit)? = null

    private val n = 96
    private val rx = FloatArray(n)
    private val tx = FloatArray(n)
    private var head = 0
    private var lastRx = TrafficStats.getTotalRxBytes()
    private var lastTx = TrafficStats.getTotalTxBytes()

    private val handler = Handler(Looper.getMainLooper())
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val tick = object : Runnable {
        override fun run() {
            sample()
            invalidate()
            handler.postDelayed(this, 1000)
        }
    }

    private fun sample() {
        val nowRx = TrafficStats.getTotalRxBytes()
        val nowTx = TrafficStats.getTotalTxBytes()
        if (nowRx < 0 || nowTx < 0) return // unsupported on this device
        val rRx = (nowRx - lastRx).coerceAtLeast(0)
        val rTx = (nowTx - lastTx).coerceAtLeast(0)
        lastRx = nowRx
        lastTx = nowTx
        rx[head] = rRx.toFloat()
        tx[head] = rTx.toFloat()
        head = (head + 1) % n
        onSample?.invoke(rRx, rTx)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastRx = TrafficStats.getTotalRxBytes()
        lastTx = TrafficStats.getTotalTxBytes()
        handler.postDelayed(tick, 1000)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        var max = 1f
        for (i in 0 until n) {
            if (rx[i] > max) max = rx[i]
            if (tx[i] > max) max = tx[i]
        }
        // download: slightly stronger; upload: dimmer — both very subtle.
        drawSeries(canvas, rx, max, w, h, Roost.withAlpha(accent, 0x38), Roost.withAlpha(accent, 0x12))
        drawSeries(canvas, tx, max, w, h, Roost.withAlpha(accent, 0x24), Roost.withAlpha(accent, 0x0A))
    }

    private fun drawSeries(canvas: Canvas, data: FloatArray, max: Float, w: Float, h: Float, line: Int, area: Int) {
        val step = w / (n - 1)
        val path = Path()
        val areaPath = Path()
        for (i in 0 until n) {
            val v = (data[(head + i) % n] / max).coerceIn(0f, 1f)
            val x = i * step
            val y = h - v * (h * 0.9f)
            if (i == 0) {
                path.moveTo(x, y)
                areaPath.moveTo(x, h)
                areaPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo(w, h)
        areaPath.close()
        fill.color = area
        canvas.drawPath(areaPath, fill)
        stroke.color = line
        stroke.strokeWidth = h * 0.018f
        canvas.drawPath(path, stroke)
    }
}
