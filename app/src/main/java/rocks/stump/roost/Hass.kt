package rocks.stump.roost

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Home Assistant REST client — scenes only, framework-only (HttpURLConnection + org.json),
 * no third-party library. All calls hit the network, so run them off the main thread.
 */
object Hass {

    /** (entity_id, friendly_name) for every `scene.*` entity. */
    fun scenes(account: HassAccount): List<Pair<String, String>> {
        val arr = JSONArray(httpGet(account, "/api/states"))
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val eid = o.optString("entity_id")
            if (eid.startsWith("scene.")) {
                val name = o.optJSONObject("attributes")?.optString("friendly_name").orEmpty().ifBlank { eid }
                out.add(eid to name)
            }
        }
        return out.sortedBy { it.second.lowercase() }
    }

    /** Fire a scene via `scene.turn_on`. */
    fun activateScene(account: HassAccount, entityId: String) {
        httpPost(account, "/api/services/scene/turn_on", JSONObject().put("entity_id", entityId).toString())
    }

    private fun open(account: HassAccount, path: String): HttpURLConnection {
        val base = account.url.trim().trimEnd('/')
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer ${account.token.trim()}")
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "application/json")
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return c
    }

    private fun httpGet(account: HassAccount, path: String): String {
        val c = open(account, path).apply { requestMethod = "GET" }
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun httpPost(account: HassAccount, path: String, body: String): String {
        val c = open(account, path).apply { requestMethod = "POST"; doOutput = true }
        OutputStreamWriter(c.outputStream).use { it.write(body) }
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }
}
