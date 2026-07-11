package rocks.stump.roost

import android.app.ActivityManager
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Agent (ADR-0005): the agent's display name (inline-commit text input), the featured agent app (a real
 * APP PICKER, no longer a raw package string), and "Restart agent app".
 *
 * Governing: ADR-0005 (settings navigation IA)
 */
class AgentActivity : SettingsScreen() {

    override fun screenTitle(): String = "Agent"

    override fun buildContent(body: LinearLayout) {
        // --- Agent name (inline commit — no Save button) ---
        body.addView(sectionHeader("Agent name", firstOnScreen = true))
        body.addView(textInput(Prefs.agentName(this), getString(R.string.agent_name_hint)) {
            Prefs.setAgentName(this, it)
        })
        body.addView(hint("Drives the greeting and status line on the home screen. Saves as you type — no button."))

        // --- Featured agent app (app picker, not a package field) ---
        body.addView(sectionHeader("Featured agent app"))
        val pkg = Prefs.agentPkg(this)
        body.addView(navRowDrawable(appIcon(pkg), appLabel(pkg) ?: pkg, "the app Roost boots & features") {
            startActivity(Intent(this, AppPickerActivity::class.java)
                .putExtra(AppPickerActivity.EXTRA_MODE, AppPickerActivity.MODE_FEATURED))
        })
        body.addView(hint("Pick from installed apps — no more typing a package name."))

        // --- Restart agent app ---
        body.addView(sectionHeader("Lifecycle"))
        val restartRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(12f), dp(14f), dp(12f))
        }
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        stack.addView(android.widget.TextView(this).apply {
            text = "Restart agent app"; setTextColor(ROW_LABEL); textSize = 14.5f
        })
        stack.addView(android.widget.TextView(this).apply {
            text = "Force-stop background processes & relaunch"
            setTextColor(SUBTLE); textSize = 11.5f; setPadding(0, dp(2f), 0, 0)
        })
        restartRow.addView(stack)
        restartRow.addView(accentPill("Restart") { restartAgent() })
        body.addView(card(restartRow))
    }

    private fun restartAgent() {
        val pkg = Prefs.agentPkg(this)
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch == null) {
            Toast.makeText(this, getString(R.string.not_installed, pkg), Toast.LENGTH_SHORT).show()
            return
        }
        // Best framework-only "force stop": kill the app's background processes, then relaunch it.
        runCatching {
            (getSystemService(ACTIVITY_SERVICE) as? ActivityManager)?.killBackgroundProcesses(pkg)
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(launch)
        Toast.makeText(this, "Restarting ${appLabel(pkg) ?: pkg}…", Toast.LENGTH_SHORT).show()
    }

    private fun appIcon(pkg: String): Drawable? =
        try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    private fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { null }

    override fun onResume() {
        super.onResume()
        rebuild()   // reflect a newly-picked featured app / name
    }
}
