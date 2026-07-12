package rocks.stump.roost

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Remote icon picker (Gitea issue #3, ADR-0003). Three sources: selfh.st (raster PNG via
 * BitmapFactory) and Simple Icons + Heroicons (SVG, rendered by the in-house [SvgPath] parser).
 * Search a name, tap to fetch + cache the icon and set it as the tile's icon override.
 *
 * Governing: ADR-0003 (icon rendering strategy), Gitea issue #3.
 */
class IconPickerActivity : Activity() {

    private data class Source(val label: String, val svg: Boolean, val prefix: String)

    private val sources = listOf(
        Source("selfh.st", false, ""),
        Source("Simple Icons", true, "simple-icons"),
        Source("Heroicons", true, "heroicons-solid")
    )

    private var current = 0
    private val indices = HashMap<Int, List<String>>()
    private var query = ""

    private lateinit var key: String
    private lateinit var sourceRow: LinearLayout
    private lateinit var status: TextView
    private lateinit var results: LinearLayout

    private val accent get() = Prefs.accent(this)
    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        key = intent.getStringExtra(EXTRA_KEY) ?: run { finish(); return }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(28f), dp(22f), dp(28f))
        }
        col.addView(TextView(this).apply {
            text = getString(R.string.icon_picker_title)
            setTextColor(Roost.TEXT); textSize = 22f; typeface = Roost.medium()
        })

        sourceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14f), 0, dp(10f))
        }
        col.addView(sourceRow)

        val search = EditText(this).apply {
            hint = getString(R.string.icon_search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Roost.TEXT); setHintTextColor(Roost.MUTED)
        }
        col.addView(search)
        status = TextView(this).apply {
            setTextColor(Roost.MUTED); textSize = 13f; setPadding(0, dp(10f), 0, dp(6f))
        }
        col.addView(status)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(results)

        setContentView(ScrollView(this).apply {
            background = Roost.dockBackground(this@IconPickerActivity)
            addView(col)
        })

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { query = s?.toString()?.trim().orEmpty(); renderResults() }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        buildSourceChips()
        loadIndex(0)
    }

    private fun buildSourceChips() {
        sourceRow.removeAllViews()
        sources.forEachIndexed { i, src ->
            val selected = i == current
            sourceRow.addView(TextView(this).apply {
                text = src.label
                textSize = 12.5f
                gravity = Gravity.CENTER
                setTextColor(if (selected) Roost.DOCK else Roost.TEXT)
                background = Roost.rounded(
                    if (selected) accent else Roost.TILE, dp(18f).toFloat(),
                    if (selected) accent else Roost.HAIRLINE, dp(1f)
                )
                setPadding(dp(12f), dp(7f), dp(12f), dp(7f))
                setOnClickListener { if (current != i) { current = i; buildSourceChips(); loadIndex(i) } }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(8f) })
        }
    }

    private fun loadIndex(idx: Int) {
        results.removeAllViews()
        indices[idx]?.let { renderResults(); return }
        status.text = getString(R.string.icon_loading)
        val src = sources[idx]
        Thread {
            val list = runCatching {
                if (src.svg) IconStore.iconifyIndex(src.prefix) else IconStore.selfhstIndex()
            }.getOrDefault(emptyList())
            runOnUiThread {
                if (current == idx) {
                    indices[idx] = list
                    status.text = if (list.isEmpty()) getString(R.string.icon_none)
                    else getString(R.string.icon_search_ready)
                    renderResults()
                }
            }
        }.start()
    }

    private fun renderResults() {
        results.removeAllViews()
        val names = indices[current] ?: return
        if (query.length < 2) {
            status.text = getString(R.string.icon_search_ready)
            return
        }
        val matches = names.filter { it.contains(query, ignoreCase = true) }.take(60)
        status.text = if (matches.isEmpty()) getString(R.string.icon_none) else ""
        for (name in matches) {
            results.addView(TextView(this).apply {
                text = name
                setTextColor(Roost.TEXT); textSize = 15f
                setPadding(0, dp(11f), 0, dp(11f)); isClickable = true
                setOnClickListener { apply(name) }
            })
        }
    }

    private fun apply(name: String) {
        status.text = getString(R.string.icon_applying)
        val src = sources[current]
        Thread {
            val file: File? = if (src.svg) IconStore.cacheSvg(this, IconStore.iconifyUrl(src.prefix, name), SVG_TINT)
            else IconStore.cacheRaster(this, IconStore.selfhstPngUrl(name))
            runOnUiThread {
                if (file != null) {
                    // src.svg == true is exactly the two monochrome slug sets (Simple Icons, Heroicons);
                    // selfh.st is the raster branch — so mono is a precise, source-derived discriminator.
                    Prefs.setIconOverride(this, key, file.path, mono = src.svg)
                    Toast.makeText(this, R.string.icon_applied, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    status.text = getString(R.string.icon_fetch_failed)
                }
            }
        }.start()
    }

    companion object {
        const val EXTRA_KEY = "key"
        private val SVG_TINT = 0xFFE8E1D5.toInt()
    }
}
