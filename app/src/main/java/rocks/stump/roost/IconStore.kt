package rocks.stump.roost

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
