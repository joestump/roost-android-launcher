package rocks.stump.roost

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * "Pick from my endpoints" — sectioned pre-wired templates that pre-fill [HttpActionActivity], plus a
 * "Raw request" entry into the empty builder. The Home Assistant scene template is the HASS authoring
 * path: it pre-fills POST {base}/api/services/scene/turn_on + Bearer + {"entity_id":…} as an HTTP action.
 *
 * Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Endpoint templates and HASS migration"
 */
class EndpointsActivity : Activity() {

    private data class Template(
        val title: String, val sub: String, val tag: String,
        val method: String, val auth: HttpAuth, val url: String, val body: String
    )

    private val accent get() = Prefs.accent(this)
    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(26f), dp(20f), dp(44f))
        }

        col.addView(TextView(this).apply {
            text = "Pick from my endpoints"
            setTextColor(Roost.TEXT); textSize = 22f; typeface = Roost.medium()
        })
        col.addView(TextView(this).apply {
            text = "Start from a pre-wired template — URL, auth and body are filled in for you. Or build a raw request from scratch."
            setTextColor(Roost.MUTED); textSize = 12.5f
            setPadding(dp(2f), dp(8f), dp(2f), dp(16f))
        })

        sections().forEach { (name, items) ->
            col.addView(sectionLabel(name))
            items.forEach { t -> col.addView(templateRow(t)) }
        }

        // Raw request → the empty builder.
        col.addView(TextView(this).apply {
            text = "+  Raw request"
            setTextColor(Roost.MUTED); textSize = 13.5f; gravity = Gravity.CENTER
            background = Roost.rounded(0, dp(14f).toFloat(), 0x29FFFFFF, dp(1f))
            setPadding(0, dp(14f), 0, dp(14f))
            isClickable = true
            setOnClickListener { startActivity(Intent(this@EndpointsActivity, HttpActionActivity::class.java)); finish() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) }
        })

        setContentView(ScrollView(this).apply {
            background = Roost.dockBackground(this@EndpointsActivity)
            addView(col)
        })
    }

    private fun sections(): List<Pair<String, List<Template>>> = listOf(
        "Switchboard · durable tasks" to listOf(
            Template("Groom PRs", "Enqueue a PR-grooming task", "HMAC", "POST", HttpAuth.HMAC,
                "https://your-switchboard.example.com/hooks/task",
                "{\n  \"task\": \"groom-prs\",\n  \"device\": \"{{device}}\"\n}"),
            Template("Sync dotfiles", "Queue a dotfiles sync", "HMAC", "POST", HttpAuth.HMAC,
                "https://your-switchboard.example.com/hooks/task",
                "{\n  \"task\": \"sync-dotfiles\"\n}"),
            Template("Run monitor", "Kick the health monitor", "HMAC", "POST", HttpAuth.HMAC,
                "https://your-switchboard.example.com/hooks/task",
                "{\n  \"task\": \"run-monitor\"\n}")
        ),
        "Known services" to listOf(
            Template("Home Assistant scene", "Fire a scene · Bearer", "Bearer", "POST", HttpAuth.BEARER,
                "https://home-assistant.example.com/api/services/scene/turn_on",
                "{\n  \"entity_id\": \"scene.movie_night\"\n}"),
            Template("LiteLLM", "Ping the proxy", "Bearer", "POST", HttpAuth.BEARER,
                "https://litellm.example.com/v1/chat/completions",
                "{\n  \"model\": \"gpt-4o-mini\"\n}"),
            Template("Gitea webhook", "Trigger a repo action", "None", "POST", HttpAuth.NONE,
                "https://gitea.example.com/api/v1/repos/OWNER/REPO/dispatches",
                "{\n  \"event_type\": \"deploy\"\n}")
        )
    )

    private fun templateRow(t: Template): View {
        val rowV = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(0x09FFFFFF, dp(15f).toFloat(), Roost.HAIRLINE, dp(1f))
            setPadding(dp(15f), dp(13f), dp(15f), dp(13f))
            isClickable = true
            setOnClickListener { open(t) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(9f) }
        }
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        labels.addView(TextView(this).apply {
            text = t.title; setTextColor(Roost.TEXT); textSize = 14.5f
        })
        labels.addView(TextView(this).apply {
            text = t.sub; setTextColor(0xFF8F8578.toInt()); textSize = 10.5f; typeface = Typeface.MONOSPACE
            setPadding(0, dp(2f), 0, 0)
        })
        rowV.addView(labels)
        val none = t.tag == "None"
        rowV.addView(TextView(this).apply {
            text = t.tag.uppercase(); textSize = 9f; typeface = Typeface.MONOSPACE
            setTextColor(if (none) 0xFF8F8578.toInt() else accent)
            background = Roost.rounded(0, dp(6f).toFloat(),
                if (none) Roost.HAIRLINE else Roost.soft(accent), dp(1f))
            setPadding(dp(7f), dp(3f), dp(7f), dp(3f))
        })
        return rowV
    }

    private fun open(t: Template) {
        startActivity(Intent(this, HttpActionActivity::class.java)
            .putExtra(HttpActionActivity.EXTRA_TITLE, t.title)
            .putExtra(HttpActionActivity.EXTRA_METHOD, t.method)
            .putExtra(HttpActionActivity.EXTRA_AUTH, t.auth.name)
            .putExtra(HttpActionActivity.EXTRA_URL, t.url)
            .putExtra(HttpActionActivity.EXTRA_BODY, t.body))
        finish()
    }

    private fun sectionLabel(t: String) = TextView(this).apply {
        text = t.uppercase(); setTextColor(0xFF6E665B.toInt()); textSize = 10f
        letterSpacing = 0.15f; typeface = Typeface.MONOSPACE
        setPadding(dp(4f), dp(6f), dp(4f), dp(10f))
    }
}
