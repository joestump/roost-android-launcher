package rocks.stump.roost

import android.widget.LinearLayout

/**
 * App shortcuts detail screen (split out of the former combined ActionsActivity — owner feedback). A
 * "Scan apps for shortcuts" row that enumerates installed apps' static/dynamic shortcuts (ADR-0002) and
 * lists each as a toggle row; enabling one adds it as an ActionButton and it appears in the home Actions
 * zone. Toggling off removes it.
 *
 * Governing: ADR-0005 (settings navigation IA), ADR-0002 (pluggable action-button providers),
 * ADR-0001 (framework-only)
 */
class ShortcutsActivity : SettingsScreen() {

    override fun screenTitle(): String = "App shortcuts"

    override fun buildContent(body: LinearLayout) {
        body.addView(sectionHeader("App shortcuts", firstOnScreen = true))
        val shortcutsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(card(listOf(navRow(R.drawable.ic_search, "Scan apps for shortcuts",
            "Add app shortcuts as one-tap buttons") { scanShortcuts(shortcutsBox) })))
        body.addView(gap(dp(10f)))
        body.addView(shortcutsBox)
    }

    private fun scanShortcuts(box: LinearLayout) {
        box.removeAllViews()
        box.addView(hint(getString(R.string.actions_scanning)))
        Thread {
            val items = runCatching { ShortcutProvider.scanAll(this) }.getOrDefault(emptyList())
            runOnUiThread {
                box.removeAllViews()
                if (items.isEmpty()) {
                    box.addView(hint(getString(R.string.actions_no_shortcuts)))
                } else {
                    box.addView(card(items.map { item ->
                        val button = item.toButton()
                        toggleRow(item.label, item.appLabel, Prefs.isActionEnabled(this, button.key)) { checked ->
                            Prefs.setActionEnabled(this, button, checked)
                        }
                    }))
                }
            }
        }.start()
    }
}
