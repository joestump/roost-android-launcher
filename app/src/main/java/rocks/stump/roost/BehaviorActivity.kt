package rocks.stump.roost

import android.widget.LinearLayout

/**
 * Home & behavior (ADR-0005): the home mode segmented control + explainer, and the "while docked"
 * toggles. Every control here maps 1:1 to a Prefs field the old monolith exposed.
 *
 * Governing: ADR-0005 (settings navigation IA)
 */
class BehaviorActivity : SettingsScreen() {

    override fun screenTitle(): String = "Home & behavior"

    override fun buildContent(body: LinearLayout) {
        // --- Home mode (was: RadioGroup Curated / Appliance) ---
        body.addView(sectionHeader("Home mode", firstOnScreen = true))
        val selected = if (Prefs.mode(this) == Prefs.MODE_APPLIANCE) 1 else 0
        body.addView(segmented(listOf("Curated", "Boot-direct"), selected) { i ->
            Prefs.setMode(this, if (i == 1) Prefs.MODE_APPLIANCE else Prefs.MODE_CURATED)
            rebuild()
        })
        body.addView(hint(
            if (Prefs.mode(this) == Prefs.MODE_APPLIANCE)
                "Home opens the agent app straight away; long-press home for apps & settings."
            else
                "Home shows the mascot, the featured agent and your app grid."
        ))

        // --- While docked (behavior toggles) ---
        body.addView(sectionHeader("While docked"))
        body.addView(card(
            toggleRow(getString(R.string.behavior_boot), "Open the agent app the moment Roost starts",
                Prefs.autoLaunchOnBoot(this)) { Prefs.setAutoLaunchOnBoot(this, it) },
            toggleRow(getString(R.string.behavior_screen), "Never sleep on the charger",
                Prefs.keepScreenOn(this)) { Prefs.setKeepScreenOn(this, it) },
            toggleRow(getString(R.string.behavior_bandwidth), "A subtle network graph on the home screen",
                Prefs.bandwidthGraph(this)) { Prefs.setBandwidthGraph(this, it) }
        ))

        // --- Waking-up intro (Prefs.bootIntro) ---
        body.addView(sectionHeader("Boot intro"))
        body.addView(card(
            toggleRow("Play the waking-up intro", "A brief boot-log animation after a boot-launch (curated mode)",
                Prefs.bootIntro(this)) { Prefs.setBootIntro(this, it) }
        ))
    }
}
