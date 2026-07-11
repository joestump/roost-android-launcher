package rocks.stump.roost

import android.content.Intent
import android.widget.LinearLayout

/**
 * Apps, tiles & content (ADR-0005): a sub-landing that drills into the four content managers —
 * Favorites (an app picker), Web apps, Action buttons (the existing ActionsActivity), and Hidden items.
 *
 * Governing: ADR-0005 (settings navigation IA)
 */
class AppsActivity : SettingsScreen() {

    override fun screenTitle(): String = "Apps, tiles & content"

    override fun buildContent(body: LinearLayout) {
        val favCount = Prefs.favorites(this).size.toString()
        val webCount = Prefs.webApps(this).size.toString()
        val actCount = Prefs.actionButtons(this).size.toString()
        val hidCount = Prefs.hiddenItems(this).size.toString()

        body.addView(gap(dp(4f)))
        body.addView(card(
            navRow(R.drawable.ic_star, "Favorites", "Apps on the home grid", favCount) {
                startActivity(Intent(this, AppPickerActivity::class.java)
                    .putExtra(AppPickerActivity.EXTRA_MODE, AppPickerActivity.MODE_FAVORITES))
            },
            navRow(R.drawable.ic_web, "Web apps", "Fullscreen URL tiles", webCount) {
                startActivity(Intent(this, WebAppsActivity::class.java))
            },
            navRow(R.drawable.ic_bolt, "Action buttons", "HTTP · Home Assistant · shortcuts", actCount) {
                startActivity(Intent(this, ActionsActivity::class.java))
            },
            navRow(R.drawable.ic_eye_off, "Hidden items", "Restore tiles you hid", hidCount) {
                startActivity(Intent(this, HiddenActivity::class.java))
            }
        ))
    }

    override fun onResume() {
        super.onResume()
        rebuild()   // refresh the trailing counts on return
    }
}
