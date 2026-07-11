package rocks.stump.roost

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Remote icon picker (Gitea issue #3, ADR-0003). v1: selfh.st raster icons — search a name, tap to
 * fetch + cache the PNG and set it as the tile's icon override. Simple Icons / Iconify (SVG) are a
 * follow-up requiring the in-house SvgPath renderer.
 *
 * Governing: ADR-0003 (icon rendering strategy), Gitea issue #3.
 */
class IconPickerActivity : Activity() {

    private lateinit var key: String
    private var names: List<String> = emptyList()
    private lateinit var results: LinearLayout
    private lateinit var status: TextView

    private fun dp(v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private val accent get() = Prefs.accent(this)

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
        col.addView(TextView(this).apply {
            text = getString(R.string.icon_source_selfhst)
            setTextColor(accent); textSize = 12f; letterSpacing = 0.08f
            typeface = Roost.medium(); setPadding(0, dp(14f), 0, dp(6f))
        })
        val search = EditText(this).apply {
            hint = getString(R.string.icon_search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Roost.TEXT); setHintTextColor(Roost.MUTED)
        }
        col.addView(search)
        status = TextView(this).apply {
            text = getString(R.string.icon_loading)
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
            override fun afterTextChanged(s: Editable?) = renderResults(s?.toString()?.trim().orEmpty())
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })

        Thread {
            val idx = runCatching { IconStore.selfhstIndex() }.getOrDefault(emptyList())
            runOnUiThread {
                names = idx
                status.text = if (idx.isEmpty()) getString(R.string.icon_none)
                else getString(R.string.icon_search_ready)
            }
        }.start()
    }

    private fun renderResults(query: String) {
        results.removeAllViews()
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
        Thread {
            val file = IconStore.cacheRaster(this, IconStore.selfhstPngUrl(name))
            runOnUiThread {
                if (file != null) {
                    Prefs.setIconOverride(this, key, file.path)
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
    }
}
