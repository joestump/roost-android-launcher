package rocks.stump.claudelauncher

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue

/**
 * "Roost" design tokens + small drawable helpers. Warm neutral base with one themeable accent.
 * Everything here is framework-only (colors, GradientDrawable) — no raster assets, no libraries.
 */
object Roost {
    const val DOCK = 0xFF14110D.toInt()       // dock black (background base)
    const val DOCK_TOP = 0xFF1D1912.toInt()   // radial highlight toward the mascot
    const val PANEL = 0xFF1C1813.toInt()       // cards / featured panel
    const val TILE = 0xFF2A241C.toInt()        // utility tile surface
    const val TEXT = 0xFFF3EEE4.toInt()        // primary text (warm cream)
    const val MUTED = 0xFFA29A8C.toInt()       // muted text (warm taupe)
    const val HAIRLINE = 0x14FFFFFF            // ~8% white borders

    const val DEFAULT_ACCENT = 0xFFE7A44E.toInt() // Honey

    /** Themeable accent options offered in Settings. */
    val ACCENTS: List<Pair<String, Int>> = listOf(
        "Honey" to 0xFFE7A44E.toInt(),
        "Slate" to 0xFF7FA6C9.toInt(),
        "Sage" to 0xFF93B98C.toInt(),
        "Violet" to 0xFFB79BE0.toInt()
    )

    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /** Accent at ~16% — used for chips, selected states, soft glows. */
    fun soft(accent: Int): Int = withAlpha(accent, 0x29)

    fun dp(c: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.resources.displayMetrics).toInt()

    /** Radial dock background, brightest near the mascot and falling off to dock black. */
    fun dockBackground(c: Context): GradientDrawable = GradientDrawable().apply {
        gradientType = GradientDrawable.RADIAL_GRADIENT
        colors = intArrayOf(DOCK_TOP, DOCK)
        gradientRadius = c.resources.displayMetrics.heightPixels * 0.62f
        setGradientCenter(0.5f, 0.24f)
    }

    fun rounded(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    fun medium(): Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /**
     * Paint a wallpaper that matches the dock background, so the surfaces the launcher can't
     * cover (Recents/overview, app-transition animations) share Roost's warm-dark look instead
     * of the stock photo wallpaper. Sets both home and lock. Returns true on success.
     */
    fun applyWallpaper(c: Context): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(c)
            val dm = c.resources.displayMetrics
            val w = wm.desiredMinimumWidth.takeIf { it > 0 } ?: dm.widthPixels
            val h = wm.desiredMinimumHeight.takeIf { it > 0 } ?: dm.heightPixels
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(DOCK)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = RadialGradient(
                w * 0.5f, h * 0.28f, h * 0.62f,
                DOCK_TOP, DOCK, Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            wm.setBitmap(bmp)
            bmp.recycle()
            true
        } catch (e: Exception) {
            Log.w("Roost", "applyWallpaper failed", e)
            false
        }
    }
}
