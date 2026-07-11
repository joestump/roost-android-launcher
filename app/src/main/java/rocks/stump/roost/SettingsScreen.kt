package rocks.stump.roost

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Shared scaffolding + a small vocabulary of reusable row/control builders for every Settings screen
 * (ADR-0005). A subclass supplies [screenTitle] and fills the body in [buildContent]; the base draws
 * the warm-dark dock background, an in-settings header (back chevron + title), and a scrolling body.
 *
 * Everything here is framework-only (LinearLayout / ScrollView / GradientDrawable / VectorDrawable),
 * so all detail screens share one calm visual language instead of drifting per-Activity.
 *
 * Governing: ADR-0005 (settings navigation IA), ADR-0001 (framework-only)
 */
abstract class SettingsScreen : Activity() {

    protected val accent: Int get() = Prefs.accent(this)
    protected fun dp(v: Float): Int = Roost.dp(this, v)

    /** Title shown in the in-settings header bar. */
    protected abstract fun screenTitle(): String

    /** Populate [body] (a vertical LinearLayout) with this screen's content. */
    protected abstract fun buildContent(body: LinearLayout)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(scaffold())
    }

    /** Rebuild the whole screen in place (used after list mutations like add/remove). */
    protected fun rebuild() {
        setContentView(scaffold())
    }

    private fun scaffold(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Roost.dockBackground(this@SettingsScreen)
        }
        root.addView(headerBar())

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(4f), dp(16f), dp(44f))
        }
        buildContent(body)

        root.addView(ScrollView(this).apply {
            isFillViewport = false
            addView(body)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    private fun headerBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6f), dp(30f), dp(14f), dp(10f))
        }
        bar.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(0xFFC9C0B2.toInt())
            val p = dp(8f); setPadding(p, p, p, p)
            isClickable = true
            background = Roost.rounded(0, dp(19f).toFloat())
            setOnClickListener { onBackPressed() }
            layoutParams = LinearLayout.LayoutParams(dp(38f), dp(38f))
        })
        bar.addView(TextView(this).apply {
            text = screenTitle()
            setTextColor(Roost.TEXT)
            textSize = 20f
            typeface = Roost.medium()
            setPadding(dp(6f), 0, 0, 0)
        })
        return bar
    }

    // ---- Tokens -------------------------------------------------------------------------------

    private fun whiteA(alpha: Int): Int = Roost.withAlpha(0xFFFFFFFF.toInt(), alpha)
    protected fun rowBg(): Int = whiteA(0x09)      // rgba(255,255,255,0.035)
    protected fun rowBorder(): Int = whiteA(0x10)  // rgba(255,255,255,0.06)
    protected fun inputBg(): Int = whiteA(0x0D)    // rgba(255,255,255,0.05)

    protected val SECTION = 0xFF6E665B.toInt()
    protected val ROW_LABEL = 0xFFE8E1D5.toInt()
    protected val SUBTLE = 0xFF8F8578.toInt()
    private val CHEVRON = 0xFF6E665B.toInt()
    private val TRACK_OFF = 0x1FFFFFFF

    // ---- Text bits ----------------------------------------------------------------------------

    /** A mono, uppercase, tracked section label — the header rhythm shared across every screen. */
    protected fun sectionHeader(text: String, firstOnScreen: Boolean = false): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextColor(SECTION)
        textSize = 10f
        letterSpacing = 0.15f
        typeface = Typeface.MONOSPACE
        setPadding(dp(4f), dp(if (firstOnScreen) 6f else 24f), dp(4f), dp(10f))
    }

    /** A soft explanatory line under a control. */
    protected fun hint(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(SUBTLE)
        textSize = 12f
        setPadding(dp(6f), dp(9f), dp(6f), 0)
    }

    // ---- Cards --------------------------------------------------------------------------------

    /** A rounded card grouping [rows] with hairline dividers between them. */
    protected fun card(rows: List<View>): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Roost.rounded(rowBg(), dp(16f).toFloat(), rowBorder(), dp(1f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) c.addView(divider())
            c.addView(r)
        }
        return c
    }

    protected fun card(vararg rows: View): LinearLayout = card(rows.toList())

    private fun divider(): View = View(this).apply {
        setBackgroundColor(0x0DFFFFFF)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f))
    }

    /** Standard section spacing before a card / control. */
    protected fun gap(px: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, px)
    }

    // ---- Rows ---------------------------------------------------------------------------------

    /** A tinted rounded-square icon holder (soft-accent fill, accent-tinted glyph). */
    private fun iconChip(res: Int, sizeDp: Float): View {
        val holder = FrameLayout(this).apply {
            background = Roost.rounded(Roost.soft(accent), dp(11f).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
                .apply { rightMargin = dp(13f) }
        }
        holder.addView(ImageView(this).apply {
            setImageResource(res)
            setColorFilter(accent)
            val p = dp(sizeDp * 0.26f); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        return holder
    }

    private fun chevron(): ImageView = ImageView(this).apply {
        setImageResource(R.drawable.ic_chevron_right)
        setColorFilter(CHEVRON)
        layoutParams = LinearLayout.LayoutParams(dp(18f), dp(18f))
    }

    /**
     * A standalone tappable category row (landing / sub-landing): icon + label + one-line summary +
     * optional trailing count + chevron. Rounded as its own card.
     */
    protected fun categoryRow(iconRes: Int, label: String, sub: String, count: String? = null, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(rowBg(), dp(16f).toFloat(), rowBorder(), dp(1f))
            setPadding(dp(15f), dp(14f), dp(15f), dp(14f))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10f) }
        }
        row.addView(iconChip(iconRes, 38f))
        row.addView(labelStack(label, sub, Roost.TEXT))
        count?.let {
            row.addView(TextView(this).apply {
                text = it
                setTextColor(SUBTLE)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(0, 0, dp(8f), 0)
            })
        }
        row.addView(chevron())
        return row
    }

    /** A label + optional sub, weighted to push trailing controls to the right edge. */
    private fun labelStack(label: String, sub: String?, labelColor: Int): LinearLayout {
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        stack.addView(TextView(this).apply {
            text = label
            setTextColor(labelColor)
            textSize = 14.5f
            maxLines = 1
        })
        if (!sub.isNullOrBlank()) stack.addView(TextView(this).apply {
            text = sub
            setTextColor(SUBTLE)
            textSize = 11.5f
            setPadding(0, dp(2f), 0, 0)
        })
        return stack
    }

    /** A toggle row for a card: label + sub on the left, a custom pill switch on the right. */
    protected fun toggleRow(label: String, sub: String, initial: Boolean, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        row.addView(labelStack(label, sub, ROW_LABEL))
        row.addView(toggle(initial, onChange))
        return row
    }

    /** The custom on/off switch: a rounded track + a round knob that snaps left/right. */
    protected fun toggle(initial: Boolean, onChange: (Boolean) -> Unit): View {
        var on = initial
        val knob = View(this).apply {
            background = Roost.rounded(if (on) Roost.DOCK else Roost.MUTED, dp(10f).toFloat())
        }
        val track = FrameLayout(this).apply {
            setPadding(dp(3f), dp(3f), dp(3f), dp(3f))
            background = Roost.rounded(if (on) accent else TRACK_OFF, dp(13f).toFloat())
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(dp(44f), dp(25f))
        }
        track.addView(knob, FrameLayout.LayoutParams(dp(19f), dp(19f)).apply {
            gravity = Gravity.CENTER_VERTICAL or (if (on) Gravity.END else Gravity.START)
        })
        fun apply() {
            track.background = Roost.rounded(if (on) accent else TRACK_OFF, dp(13f).toFloat())
            knob.background = Roost.rounded(if (on) Roost.DOCK else Roost.MUTED, dp(10f).toFloat())
            (knob.layoutParams as FrameLayout.LayoutParams).gravity =
                Gravity.CENTER_VERTICAL or (if (on) Gravity.END else Gravity.START)
            knob.requestLayout()
        }
        track.setOnClickListener { on = !on; apply(); onChange(on) }
        return track
    }

    /**
     * A value row inside a card: an optional leading icon, a label + sub, an optional trailing value,
     * and a chevron. Tapping navigates (a detail Activity, a picker, etc.).
     */
    protected fun navRow(iconRes: Int?, label: String, sub: String?, value: String? = null, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            isClickable = true
            setOnClickListener { onClick() }
        }
        iconRes?.let { row.addView(iconChip(it, 36f)) }
        row.addView(labelStack(label, sub, Roost.TEXT))
        value?.let {
            row.addView(TextView(this).apply {
                text = it
                setTextColor(SUBTLE)
                textSize = 12.5f
                maxLines = 1
                setPadding(0, 0, dp(8f), 0)
            })
        }
        row.addView(chevron())
        return row
    }

    /** A nav row whose leading icon is a real Drawable (e.g. an app icon), shown on a tile surface. */
    protected fun navRowDrawable(icon: android.graphics.drawable.Drawable?, label: String, sub: String?, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(rowBg(), dp(16f).toFloat(), rowBorder(), dp(1f))
            setPadding(dp(13f), dp(13f), dp(14f), dp(13f))
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val holder = FrameLayout(this).apply {
            background = Roost.rounded(Roost.TILE, dp(12f).toFloat(), Roost.HAIRLINE, dp(1f))
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f)).apply { rightMargin = dp(13f) }
        }
        holder.addView(ImageView(this).apply {
            setImageDrawable(icon)
            if (icon == null) { setImageResource(R.drawable.ic_cat_agent); setColorFilter(accent) }
            val p = dp(7f); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        row.addView(holder)
        row.addView(labelStack(label, sub, Roost.TEXT))
        row.addView(chevron())
        return row
    }

    /** A small accent pill button (e.g. "Apply", "Restart"). */
    protected fun accentPill(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text
        setTextColor(accent)
        textSize = 12.5f
        background = Roost.rounded(0, dp(20f).toFloat(), Roost.soft(accent), dp(1f))
        setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** A 2–3 option segmented control (pills in a rounded rail). */
    protected fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Roost.rounded(inputBg(), dp(15f).toFloat(), rowBorder(), dp(1f))
            setPadding(dp(5f), dp(5f), dp(5f), dp(5f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        options.forEachIndexed { i, opt ->
            val on = i == selected
            rail.addView(TextView(this).apply {
                text = opt
                gravity = Gravity.CENTER
                textSize = 13f
                typeface = Roost.medium()
                setTextColor(if (on) accent else Roost.MUTED)
                background = if (on) Roost.rounded(Roost.soft(accent), dp(11f).toFloat()) else null
                setPadding(0, dp(11f), 0, dp(11f))
                isClickable = true
                setOnClickListener { onSelect(i) }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (i > 0) leftMargin = dp(6f) }
            })
        }
        return rail
    }

    /**
     * An inline-commit text input: an accent-bordered field with a "saved ✓" affordance that appears
     * once the value is committed (on blur or the keyboard's Done action). No separate Save button.
     */
    protected fun textInput(initial: String, hintText: String, uri: Boolean = false, onCommit: (String) -> Unit): View {
        val holder = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(inputBg(), dp(14f).toFloat(), Roost.soft(accent), dp(1f))
            setPadding(dp(16f), dp(2f), dp(6f), dp(2f))
        }
        val saved = TextView(this).apply {
            text = "✓ saved"
            setTextColor(accent)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            visibility = View.INVISIBLE
        }
        val edit = EditText(this).apply {
            setText(initial)
            hint = hintText
            setTextColor(Roost.TEXT)
            setHintTextColor(Roost.MUTED)
            textSize = 16f
            background = null
            setPadding(0, dp(12f), 0, dp(12f))
            inputType = if (uri) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            else InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        fun commit() {
            onCommit(edit.text.toString().trim())
            saved.visibility = View.VISIBLE
        }
        edit.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) commit() else saved.visibility = View.INVISIBLE
        }
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { saved.visibility = View.INVISIBLE }
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
        })
        edit.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commit()
                (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(edit.windowToken, 0)
                edit.clearFocus()
                true
            } else false
        }
        holder.addView(edit)
        holder.addView(saved)
        return holder
    }

    /** A plain rounded field (name/URL entry in add-forms) with no inline-save affordance. */
    protected fun plainField(hintText: String, uri: Boolean = false, mono: Boolean = false): EditText = EditText(this).apply {
        hint = hintText
        setTextColor(Roost.TEXT)
        setHintTextColor(Roost.MUTED)
        textSize = 14f
        background = Roost.rounded(inputBg(), dp(13f).toFloat(), rowBorder(), dp(1f))
        setPadding(dp(14f), dp(13f), dp(14f), dp(13f))
        if (mono) typeface = Typeface.MONOSPACE
        inputType = if (uri) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        else InputType.TYPE_CLASS_TEXT
    }

    /** Default a bare host to https:// so pasted addresses "just work". */
    protected fun normalizeUrl(raw: String): String {
        val u = raw.trim()
        return when {
            u.isEmpty() -> ""
            u.startsWith("http://") || u.startsWith("https://") -> u
            else -> "https://$u"
        }
    }
}
