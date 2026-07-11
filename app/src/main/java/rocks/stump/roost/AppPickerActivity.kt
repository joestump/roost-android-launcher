package rocks.stump.roost

import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * App picker (ADR-0005): a framework-only, searchable list of installed launchable apps — each an
 * icon (PackageManager.getApplicationIcon) + label — laid out as a filtered grid in a ScrollView
 * (no RecyclerView). Two modes:
 *  - FEATURED  : single-select → sets the featured agent app and finishes.
 *  - FAVORITES : multi-select with a check overlay → toggles Prefs.favorites live.
 *
 * Replaces the old raw featured-package field and the all-apps favorites checkbox wall.
 *
 * Governing: ADR-0005 (settings navigation IA)
 */
class AppPickerActivity : SettingsScreen() {

    private data class AppEntry(val pkg: String, val label: String, val icon: Drawable?)

    // Read straight from the intent so it is correct in screenTitle() (called before buildContent).
    private val mode: String get() = intent.getStringExtra(EXTRA_MODE) ?: MODE_FAVORITES
    private var all: List<AppEntry> = emptyList()
    private var query = ""
    private lateinit var grid: LinearLayout

    override fun screenTitle(): String =
        if (mode == MODE_FEATURED) "Choose agent app" else "Favorites"

    override fun buildContent(body: LinearLayout) {
        if (all.isEmpty()) all = loadApps()

        // Search field.
        val search = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(inputBg(), dp(13f).toFloat(), rowBorder(), dp(1f))
            setPadding(dp(14f), dp(4f), dp(14f), dp(4f))
        }
        search.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(SUBTLE)
            layoutParams = LinearLayout.LayoutParams(dp(17f), dp(17f)).apply { rightMargin = dp(9f) }
        })
        search.addView(EditText(this).apply {
            hint = "Search installed apps"
            setText(query)
            setTextColor(Roost.TEXT)
            setHintTextColor(Roost.MUTED)
            textSize = 14f
            background = null
            setPadding(0, dp(11f), 0, dp(11f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim().orEmpty(); populate() }
                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            })
        })
        body.addView(search)

        body.addView(hint(pickerHint()).apply { setPadding(dp(4f), dp(10f), dp(4f), dp(12f)) })

        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(grid)
        populate()
    }

    private fun pickerHint(): String = if (mode == MODE_FEATURED)
        "Tap an app to make it the featured agent."
    else
        "Favoriting pins an app to the home grid. ${Prefs.favorites(this).size} pinned · tap to add or remove."

    private fun populate() {
        grid.removeAllViews()
        val q = query.lowercase()
        val shown = all.filter { it.label.lowercase().contains(q) }
        val cols = 4
        shown.chunked(cols).forEach { rowApps ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(14f) }
            }
            for (c in 0 until cols) {
                val entry = rowApps.getOrNull(c)
                if (entry == null) {
                    row.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                    })
                } else {
                    row.addView(cell(entry), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
            }
            grid.addView(row)
        }
        if (shown.isEmpty()) grid.addView(TextView(this).apply {
            text = "No matching apps."
            setTextColor(SUBTLE); textSize = 13f
            setPadding(dp(4f), dp(8f), 0, 0)
        })
    }

    private fun cell(entry: AppEntry): View {
        val selected = if (mode == MODE_FEATURED) entry.pkg == Prefs.agentPkg(this)
        else Prefs.favorites(this).contains(entry.pkg)

        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            setOnClickListener { onPick(entry) }
        }
        val tileSize = dp(52f)
        val holder = FrameLayout(this).apply {
            background = Roost.rounded(
                if (selected) Roost.soft(accent) else Roost.withAlpha(0xFFFFFFFF.toInt(), 0x0D),
                dp(15f).toFloat(),
                if (selected) accent else Roost.withAlpha(0xFFFFFFFF.toInt(), 0x12),
                dp(if (selected) 2f else 1f)
            )
            layoutParams = LinearLayout.LayoutParams(tileSize, tileSize)
        }
        holder.addView(ImageView(this).apply {
            setImageDrawable(entry.icon)
            val p = dp(8f); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        // Check overlay when selected.
        if (selected) holder.addView(FrameLayout(this).apply {
            background = Roost.rounded(accent, dp(10f).toFloat(), Roost.DOCK, dp(2f))
            layoutParams = FrameLayout.LayoutParams(dp(20f), dp(20f)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = -dp(5f); rightMargin = -dp(5f)
            }
            addView(ImageView(this@AppPickerActivity).apply {
                setImageResource(R.drawable.ic_check)
                val p = dp(4f); setPadding(p, p, p, p)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            })
        })
        cell.addView(holder)
        cell.addView(TextView(this).apply {
            text = entry.label
            setTextColor(Roost.MUTED)
            textSize = 10.5f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(2f), dp(6f), dp(2f), 0)
        })
        return cell
    }

    private fun onPick(entry: AppEntry) {
        if (mode == MODE_FEATURED) {
            Prefs.setAgentPkg(this, entry.pkg)
            finish()
        } else {
            val favs = Prefs.favorites(this)
            if (favs.contains(entry.pkg)) favs.remove(entry.pkg) else favs.add(entry.pkg)
            Prefs.setFavorites(this, favs)
            populate()   // repaint check states + the "N pinned" hint stays until next open
        }
    }

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull {
                val pkg = it.activityInfo.packageName
                if (pkg == packageName) null
                else AppEntry(pkg, it.loadLabel(pm).toString(), runCatching { it.loadIcon(pm) }.getOrNull())
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_FEATURED = "featured"
        const val MODE_FAVORITES = "favorites"
    }
}
