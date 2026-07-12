package rocks.stump.roost

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Framework-only client that fires any [HttpAction] — the generalization of [Hass]. Builds an
 * [HttpURLConnection] for the action's method, applies arbitrary user headers, applies the auth
 * scheme (None / Bearer / HMAC), substitutes {{var}} tokens in the body at fire time, and returns a
 * small [Result]. All calls hit the network, so run [fire] off the main thread (mirror Hass.kt).
 *
 * The one reach past plain HTTP/JSON is HMAC body signing via the platform [javax.crypto.Mac]
 * (HmacSHA256) — no third-party dependency, per ADR-0004.
 *
 * Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "General HTTP-action definition"
 */
object HttpActionClient {

    /** The outcome of a fire. [timeout] distinguishes an 8s no-response from other failures. */
    data class Result(
        val ok: Boolean,
        val timeout: Boolean,
        val code: Int,
        val body: String,
        val reason: String
    )

    private const val TIMEOUT_MS = 8000
    private const val MAX_BODY = 2048
    /** The header carrying the HMAC signature when auth == HMAC. */
    const val SIGNATURE_HEADER = "X-Signature"

    /**
     * Build the standard substitution variables. Context-dependent values (battery, agent) are read
     * here; time/device are intrinsic. Callers may overlay extras (e.g. {{prompt}}).
     */
    fun defaultVars(c: Context, extra: Map<String, String> = emptyMap()): Map<String, String> {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val vars = mutableMapOf(
            "timestamp" to iso,
            "device" to Build.MODEL,
            "battery" to (if (battery in 0..100) battery.toString() else ""),
            "agent" to Prefs.agentName(c)
        )
        vars.putAll(extra)
        return vars
    }

    /** Replace every {{name}} token with vars[name] (unknown → empty). */
    fun substitute(template: String, vars: Map<String, String>): String =
        Regex("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}").replace(template) { m ->
            vars[m.groupValues[1]] ?: ""
        }

    /** Mask any occurrence of [secret] (and generic bearer/signature values) in an echoed string. */
    fun redact(text: String, secret: String): String {
        var out = text
        if (secret.isNotEmpty()) out = out.replace(secret, "••••••")
        return out
    }

    fun hostOf(url: String): String =
        runCatching { URL(normalizeUrl(url)).host }.getOrNull().orEmpty().ifBlank { url }

    /** A bare hostname without a scheme defaults to https:// (SPEC-0002). */
    fun normalizeUrl(raw: String): String {
        val u = raw.trim()
        return when {
            u.isEmpty() -> u
            u.startsWith("http://") || u.startsWith("https://") -> u
            else -> "https://$u"
        }
    }

    /**
     * Fire [action], applying [secret] for Bearer/HMAC and substituting [vars] into the body. Never
     * throws for expected failures — network errors and non-2xx become an error [Result]; an 8s
     * no-response becomes a timeout [Result].
     */
    fun fire(action: HttpAction, secret: String, vars: Map<String, String>): Result {
        val method = action.method.trim().uppercase().ifBlank { "POST" }
        val resolvedBody = substitute(action.body, vars)
        val sendBody = method != "GET" && resolvedBody.isNotBlank()
        return try {
            val c = URL(normalizeUrl(action.url)).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            action.headers.forEach { (k, v) -> if (k.isNotBlank()) c.setRequestProperty(k, v) }
            when (action.auth) {
                HttpAuth.BEARER -> if (secret.isNotBlank())
                    c.setRequestProperty("Authorization", "Bearer ${secret.trim()}")
                HttpAuth.HMAC -> if (secret.isNotBlank())
                    c.setRequestProperty(SIGNATURE_HEADER, hmacHex(resolvedBody, secret.trim()))
                HttpAuth.NONE -> { /* nothing */ }
            }
            if (sendBody) {
                c.doOutput = true
                OutputStreamWriter(c.outputStream).use { it.write(resolvedBody) }
            }
            val code = c.responseCode
            val ok = code in 200..299
            val stream = if (ok) c.inputStream else (c.errorStream ?: c.inputStream)
            val body = runCatching { stream.bufferedReader().use { it.readText() } }.getOrDefault("")
            c.disconnect()
            Result(ok, false, code, body.take(MAX_BODY), if (ok) "" else "HTTP $code")
        } catch (e: SocketTimeoutException) {
            Result(false, true, 0, "", "no response after 8s")
        } catch (e: Exception) {
            Result(false, false, 0, "", e.message ?: "request failed")
        }
    }

    private fun hmacHex(body: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
