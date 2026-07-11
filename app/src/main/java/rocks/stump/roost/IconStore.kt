package rocks.stump.roost

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches and caches tile icons from remote collections. Framework-only per ADR-0001 / ADR-0003:
 * raster sources (selfh.st PNG/WebP) decode via BitmapFactory. SVG sources (Simple Icons, Iconify)
 * are a follow-up that will render `<path>` data through an in-house SvgPath parser (ADR-0003).
 *
 * Governing: ADR-0003 (icon rendering strategy), Gitea issue #3.
 */
object IconStore {

    /** selfh.st icon names via the jsDelivr flat file index (robust to the exact JSON shape).
     *  Uses the default branch (`@master`); `@latest` fails as the repo exceeds jsDelivr's 50MB cap. */
    fun selfhstIndex(): List<String> {
        val json = httpText("https://data.jsdelivr.com/v1/packages/gh/selfhst/icons@master?structure=flat")
            ?: return emptyList()
        return Regex("/png/([^\"/]+)\\.png").findAll(json)
            .map { it.groupValues[1] }
            .distinct()
            .sorted()
            .toList()
    }

    fun selfhstPngUrl(name: String): String = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/$name.png"

    /** Download a raster icon and cache it locally. Returns the cached file, or null on failure. */
    fun cacheRaster(context: Context, url: String): File? {
        val bytes = httpBytes(url) ?: return null
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val dir = File(context.filesDir, "icons").apply { mkdirs() }
        val file = File(dir, "ic_" + Integer.toHexString(url.hashCode()) + ".png")
        return try {
            FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            file
        } catch (e: Exception) {
            null
        }
    }

    // --- SVG sources via Iconify (Simple Icons, Heroicons) — ADR-0003 in-house renderer ---

    /** Icon names in an Iconify collection (e.g. `simple-icons`, `heroicons-solid`). */
    fun iconifyIndex(prefix: String): List<String> {
        val json = httpText("https://api.iconify.design/collection?prefix=$prefix") ?: return emptyList()
        return try {
            val o = JSONObject(json)
            val names = mutableListOf<String>()
            o.optJSONArray("uncategorized")?.let { for (k in 0 until it.length()) names.add(it.getString(k)) }
            o.optJSONObject("categories")?.let { cats ->
                val keys = cats.keys()
                while (keys.hasNext()) {
                    val cat = keys.next()
                    cats.optJSONArray(cat)?.let { arr -> for (k in 0 until arr.length()) names.add(arr.getString(k)) }
                }
            }
            names.distinct().sorted()
        } catch (e: Exception) { emptyList() }
    }

    fun iconifyUrl(prefix: String, name: String): String = "https://api.iconify.design/$prefix/$name.svg"

    /** Fetch an SVG, render its `<path>`s (filled, or stroked for outline sets) tinted, and cache. */
    fun cacheSvg(context: Context, url: String, tint: Int): File? {
        val svg = httpText(url) ?: return null
        val paths = Regex("<path\\b[^>]*\\bd=\"([^\"]+)\"", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(svg).map { it.groupValues[1] }.toList()
        if (paths.isEmpty()) return null
        val vb = Regex("viewBox=\"([-\\d.\\s]+)\"").find(svg)?.groupValues?.get(1)?.trim()?.split(Regex("\\s+"))
        val vbx = vb?.getOrNull(0)?.toFloatOrNull() ?: 0f
        val vby = vb?.getOrNull(1)?.toFloatOrNull() ?: 0f
        val vbw = vb?.getOrNull(2)?.toFloatOrNull() ?: 24f
        val vbh = vb?.getOrNull(3)?.toFloatOrNull() ?: 24f
        val outline = svg.contains("fill=\"none\"") && svg.contains("stroke=\"")
        val strokeW = Regex("stroke-width=\"([\\d.]+)\"").find(svg)?.groupValues?.get(1)?.toFloatOrNull() ?: 2f
        val evenOdd = svg.contains("evenodd")

        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val scale = size / maxOf(vbw, vbh)
        canvas.translate((size - vbw * scale) / 2f - vbx * scale, (size - vbh * scale) / 2f - vby * scale)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint }
        if (outline) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeW
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
        } else {
            paint.style = Paint.Style.FILL
        }
        for (d in paths) {
            val p = SvgPath.parse(d)
            p.fillType = if (evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
            canvas.drawPath(p, paint)
        }
        val dir = File(context.filesDir, "icons").apply { mkdirs() }
        val file = File(dir, "ic_" + Integer.toHexString((url + tint).hashCode()) + ".png")
        return try {
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            file
        } catch (e: Exception) { null }
    }

    fun drawableFor(context: Context, path: String): Drawable? {
        val f = File(path)
        if (!f.exists()) return null
        val bmp = BitmapFactory.decodeFile(path) ?: return null
        return BitmapDrawable(context.resources, bmp)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "*/*")
        }

    private fun httpText(url: String): String? = try {
        val c = open(url)
        c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    } catch (e: Exception) { null }

    private fun httpBytes(url: String): ByteArray? = try {
        val c = open(url)
        c.inputStream.use { it.readBytes() }.also { c.disconnect() }
    } catch (e: Exception) { null }
}
