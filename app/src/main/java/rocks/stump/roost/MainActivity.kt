package rocks.stump.roost

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The HOME surface, styled per the "Roost" design.
 *
 *  - Curated  : the robot mascot, a greeting, and a single aligned grid — the featured agent (accent
 *               ring) first, then utility apps, then web apps, then Add.
 *  - Appliance: an ambient "at rest" face (mascot + greeting); long-press reveals the grid.
 *
 * The greeting and the mono status line update live from a battery-change receiver, so charging state
 * and % are never stale.
 */
class MainActivity : Activity() {

    private var applianceRevealed = false

    private var greetingLabel: TextView? = null
    private var statusLabel: TextView? = null
    private var vpnChipView: TextView? = null
    private var mascotView: MascotView? = null
    private var rxRate = 0L
    private var txRate = 0L

    // "Awake / working" presence: derived from live network throughput with a short linger, so the
    // mascot brightens + the greeting flips to "working…" while the agent is actually pushing traffic,
    // then settles back to "home" once it goes quiet. Purely presentational.
    private var awake = false
    private var lastActiveAt = 0L

    // Debounce for tap-to-launch SHORTCUT actions — they fire synchronously (never PENDING), so the
    // isPending() guard can't stop a fast double-tap from launching the target twice. (Fix 9.)
    private var lastShortcutFireAt = 0L

    // Guards the on-resume synced-actions reconcile so it runs at most once per resume (ADR-0006).
    private var syncReconciling = false

    // Independent 1s traffic-rate poll so the VPN chip always shows live up/down speeds,
    // regardless of whether the "Bandwidth heartbeat" graph is enabled.
    private val rateHandler = Handler(Looper.getMainLooper())
    private var ratePollActive = false
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private val rateTick = object : Runnable {
        override fun run() {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            if (lastRxBytes > 0L && rx >= lastRxBytes) rxRate = rx - lastRxBytes
            if (lastTxBytes > 0L && tx >= lastTxBytes) txRate = tx - lastTxBytes
            lastRxBytes = rx
            lastTxBytes = tx
            refreshVpnChip()
            updateAwake()
            rateHandler.postDelayed(this, 1000L)
        }
    }

    // Drives the "waking up" boot-log line reveals; cleared on pause so it never fires off-screen.
    private val bootHandler = Handler(Looper.getMainLooper())

    private var batteryRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshStatus()
    }

    private val accent: Int get() = Prefs.accent(this)
    private fun dp(v: Float): Int = Roost.dp(this, v)

    override fun onResume() {
        super.onResume()
        applyScreenOn()

        if (!Prefs.wallpaperApplied(this)) {
            Roost.applyWallpaper(this)
            Prefs.setWallpaperApplied(this, true)
        }

        if (Prefs.pendingBootLaunch(this)) {
            Prefs.setPendingBootLaunch(this, false)
            // Optional "waking up" intro on curated boots (gated by a flag, off by default). It reveals
            // the home itself when the sequence finishes, so it never blocks a normal launch.
            if (Prefs.bootIntro(this) && Prefs.mode(this) == Prefs.MODE_CURATED) {
                registerBattery()
                startRatePoll()
                // This intro stands in for the auto-launch-on-boot; hand off to the agent when it
                // finishes instead of stranding on home (enabling the intro must not defeat auto-launch).
                renderWaking(launchAgentWhenDone = true)
                return
            }
            if (launchAgent()) return
        }

        render()
        registerBattery()
        startRatePoll()
        maybeReconcileSynced()
    }

    // If a synced folder is granted, reconcile actions.d/*.json OFF the main thread and re-render the
    // home only if something changed. Guarded so it runs at most once per resume and never blocks UI.
    // Governing: ADR-0006 (declarative action provisioning), SPEC-0003 REQ "Reconcile trigger and ordering"
    private fun maybeReconcileSynced() {
        if (Prefs.syncedFolderUri(this) == null) return
        if (syncReconciling) return
        syncReconciling = true
        Thread {
            val res = SyncedActions.reconcile(this)
            runOnUiThread {
                syncReconciling = false
                if (res.changed() && !isFinishing) render()
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        unregisterBattery()
        stopRatePoll()
        bootHandler.removeCallbacksAndMessages(null)
    }

    private fun startRatePoll() {
        if (ratePollActive) return          // guard against double-starting
        ratePollActive = true
        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()
        rateHandler.postDelayed(rateTick, 1000L)
    }

    private fun stopRatePoll() {
        rateHandler.removeCallbacks(rateTick)
        ratePollActive = false
    }

    override fun onStop() {
        super.onStop()
        applianceRevealed = false
    }

    override fun onBackPressed() { /* home is a dead end */ }

    private fun applyScreenOn() {
        if (Prefs.keepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun render() {
        val appliance = Prefs.mode(this) == Prefs.MODE_APPLIANCE
        if (appliance && !applianceRevealed) renderAmbient() else renderHome()
    }

    // --- Curated home -----------------------------------------------------------------------

    private fun renderHome() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14f), dp(46f), dp(14f), dp(28f))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        col.addView(mascot(dp(128f)))
        col.addView(greetingView())
        col.addView(statusView())
        vpnChip()?.let { col.addView(it) }
        featuredHero()?.let {
            col.addView(spacer(dp(22f)))
            col.addView(it)
        }
        col.addView(spacer(dp(20f)))
        renderHomeTiles(col)
        col.addView(weightedSpacer())
        col.addView(appsSettingsLink(dp(16f)))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(col)
        }
        setContentView(FrameLayout(this).apply {
            background = Roost.dockBackground(this@MainActivity)
            if (Prefs.bandwidthGraph(this@MainActivity)) {
                addView(
                    bandwidthView(),
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110f))
                        .apply { gravity = Gravity.BOTTOM }
                )
            }
            addView(
                scroll,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        })
    }

    private fun bandwidthView(): BandwidthView = BandwidthView(this).apply {
        accent = this@MainActivity.accent
        onSample = { rxps, txps ->
            rxRate = rxps
            txRate = txps
            refreshVpnChip()
        }
    }

    // --- Appliance "at rest" face -----------------------------------------------------------

    private fun renderAmbient() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32f), dp(32f), dp(32f), dp(32f))
        }
        col.addView(mascot(dp(150f)))
        col.addView(greetingView())
        col.addView(statusView())
        col.addView(TextView(this).apply {
            text = getString(R.string.appliance_hint)
            setTextColor(Roost.MUTED)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(0, dp(28f), 0, 0)
        })

        val root = FrameLayout(this).apply {
            background = Roost.dockBackground(this@MainActivity)
            isLongClickable = true
            setOnLongClickListener {
                applianceRevealed = true
                renderHome()
                true
            }
            addView(
                col,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER }
            )
        }
        setContentView(root)
    }

    // --- Pieces -----------------------------------------------------------------------------

    private fun mascot(sizePx: Int): MascotView = MascotView(this).apply {
        accent = this@MainActivity.accent
        awake = this@MainActivity.awake
        isClickable = true
        setOnClickListener { launchAgent() }
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        mascotView = this
    }

    private fun greetingView(): TextView = TextView(this).apply {
        text = greeting()
        setTextColor(Roost.TEXT)
        textSize = 21f
        gravity = Gravity.CENTER
        setPadding(0, dp(10f), 0, 0)
        greetingLabel = this
    }

    private fun statusView(): TextView = TextView(this).apply {
        text = statusLine()
        setTextColor(Roost.MUTED)
        textSize = 12f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(0, dp(6f), 0, 0)
        statusLabel = this
    }

    private fun greeting(): String {
        val name = Prefs.agentName(this).trim()
        return when {
            awake && name.isNotEmpty() -> getString(R.string.greeting_working_named, name)
            awake -> getString(R.string.greeting_working)
            name.isNotEmpty() -> getString(R.string.greeting_named, name)
            else -> getString(R.string.greeting_home)
        }
    }

    private fun statusLine(): String {
        val (pct, charging) = battery()
        val who = Prefs.agentName(this).trim().ifEmpty { "roost" }
        // "awake · working…" takes precedence over the power state, matching the mockup.
        val word = when {
            awake -> "awake · working…"
            charging -> "docked & charging"
            else -> "on battery"
        }
        // While working the % is noise; keep the line focused on the "working…" state.
        return if (!awake && pct >= 0) "$who · $word · $pct%" else "$who · $word"
    }

    /**
     * Recompute the "awake" presence from the current traffic rate (with a short linger so it doesn't
     * flicker), and push it to the mascot + greeting/status. Called each second by [rateTick].
     */
    private fun updateAwake() {
        val now = SystemClock.uptimeMillis()
        if (rxRate + txRate > AWAKE_THRESHOLD) lastActiveAt = now
        val nowAwake = lastActiveAt != 0L && (now - lastActiveAt) < AWAKE_LINGER_MS
        if (nowAwake != awake) {
            awake = nowAwake
            mascotView?.awake = awake
            refreshStatus()
        }
    }

    /** Returns (battery %, isOnPower). Reads the sticky ACTION_BATTERY_CHANGED intent. */
    private fun battery(): Pair<Int, Boolean> {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return pct to (plugged != 0)
    }

    private fun refreshStatus() {
        greetingLabel?.text = greeting()
        statusLabel?.text = statusLine()
    }

    private fun registerBattery() {
        if (!batteryRegistered) {
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryRegistered = true
        }
    }

    private fun unregisterBattery() {
        if (batteryRegistered) {
            try { unregisterReceiver(batteryReceiver) } catch (e: Exception) { /* not registered */ }
            batteryRegistered = false
        }
    }

    // The unified home (ADR-0007, owner feedback): EVERY tile — favorite apps, web apps, shortcuts,
    // HASS, HTTP — is one uniform density tile (SLIM/REGULAR vertical list, RICH 2-col grid), ordered by
    // the single Prefs.tileLayout, with no apps-grid-vs-actions split and no section headers. An optional
    // filter-chip row above lets the owner narrow the home to one kind; the trailing Store/Add ghost tile
    // always closes the list. The featured agent stays a separate hero above these.
    // Governing: ADR-0007 (unified tile model), SPEC-0004.
    private fun renderHomeTiles(col: LinearLayout) {
        val density = Prefs.actionDensity(this)
        val tiles = UnifiedTiles.ordered(this)
            .filter { !Prefs.isHidden(this, it.key) && !Prefs.isActionDisabled(this, it.key) }
        // Chips are built from the full (unfiltered) visible set so a chip never disappears just because
        // the active filter emptied the list — the owner must always be able to switch back to All.
        filterChipRow(tiles)?.let {
            col.addView(it)
            col.addView(spacer(dp(16f)))
        }
        // A filter whose kind no longer has any tile (its last tile was deleted) falls back to All so the
        // home can't get stuck showing nothing.
        val active = Prefs.tileFilter(this).takeIf { f -> tiles.any { it.kind.name == f } } ?: ""
        val shown = if (active.isBlank()) tiles else tiles.filter { it.kind.name == active }
        val views = shown.map { buildActionTile(it, density) } + ghostTile(density)
        col.addView(renderTiles(views, density))
    }

    /** The type (kind) filter chip row: "All" + one chip per kind that is (a) present among [tiles] and
     *  (b) not opted out via Prefs.hiddenFilterKinds. Omitted (null) when fewer than 2 kind-chips would
     *  show. Tapping a chip sets the active filter and re-renders; the active chip is accent-highlighted. */
    private fun filterChipRow(tiles: List<ActionButton>): View? {
        val hidden = Prefs.hiddenFilterKinds(this)
        // A stable, launch-first chip order regardless of tileLayout order.
        val order = listOf(
            ActionKind.APP, ActionKind.WEB, ActionKind.SHORTCUT, ActionKind.HTTP, ActionKind.HASS_SCENE
        )
        val present = order.filter { k -> tiles.any { it.kind == k } && k.name !in hidden }
        if (present.size < 2) return null
        val active = Prefs.tileFilter(this).takeIf { f -> tiles.any { it.kind.name == f } } ?: ""

        fun chip(label: String, selected: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
            text = label
            textSize = 12.5f
            typeface = Roost.medium()
            gravity = Gravity.CENTER
            setTextColor(if (selected) accent else Roost.MUTED)
            setPadding(dp(14f), dp(7f), dp(14f), dp(7f))
            background = Roost.rounded(
                if (selected) Roost.soft(accent) else Roost.TILE, dp(16f).toFloat(),
                if (selected) Roost.withAlpha(accent, 0x66) else Roost.HAIRLINE, dp(1f)
            )
            isClickable = true
            setOnClickListener { onClick() }
        }

        val chips = mutableListOf<View>()
        chips.add(chip(getString(R.string.filter_all), active.isBlank()) {
            Prefs.setTileFilter(this, ""); renderHome()
        })
        present.forEach { k ->
            chips.add(chip(k.filterLabel(), active == k.name) {
                Prefs.setTileFilter(this, k.name); renderHome()
            })
        }

        val rowInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chips.forEachIndexed { i, c ->
            rowInner.addView(c, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) leftMargin = dp(8f) })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(rowInner)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /** The trailing "Store / Add" ghost tile, styled as a density tile (faint fill + dashed accent
     *  border) so it flows with the uniform list/grid instead of reintroducing a separate zone. */
    private fun ghostTile(density: ActionDensity): View {
        val radius = when (density) {
            ActionDensity.SLIM -> 12f
            ActionDensity.REGULAR -> 16f
            ActionDensity.RICH -> 17f
        }
        fun ghostBg() = android.graphics.drawable.GradientDrawable().apply {
            setColor(Roost.withAlpha(0xFFFFFFFF.toInt(), 0x08))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1f), Roost.soft(accent), dp(3f).toFloat(), dp(3f).toFloat())
        }
        val plus = ImageView(this).apply {
            setImageResource(R.drawable.ic_plus)
            setColorFilter(accent)
        }
        val label = TextView(this).apply {
            text = getString(R.string.store)
            setTextColor(Roost.MUTED)
            textSize = if (density == ActionDensity.SLIM) 13.5f else 14.5f
            typeface = Roost.medium()
        }
        return if (density == ActionDensity.RICH) {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = ghostBg()
                minimumHeight = dp(128f)
                setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
                isClickable = true
                setOnClickListener { openPlayStore() }
                addView(plus, LinearLayout.LayoutParams(dp(30f), dp(30f)))
                addView(label.apply { setPadding(0, dp(9f), 0, 0) })
            }
        } else {
            val padV = if (density == ActionDensity.SLIM) 9f else 12f
            val disc = if (density == ActionDensity.SLIM) 24f else 36f
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ghostBg()
                setPadding(dp(if (density == ActionDensity.SLIM) 12f else 14f), dp(padV),
                    dp(14f), dp(padV))
                isClickable = true
                setOnClickListener { openPlayStore() }
                addView(plus, LinearLayout.LayoutParams(dp(disc), dp(disc)))
                addView(label.apply { setPadding(dp(12f), 0, 0, 0) })
            }
        }
    }

    // "Apps & Settings" — a gear glyph + label, centered, per the design (was text-only).
    private fun appsSettingsLink(topPx: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, topPx, 0, 0)
        isClickable = true
        setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_settings)
            setColorFilter(Roost.MUTED)
            layoutParams = LinearLayout.LayoutParams(dp(16f), dp(16f)).apply { rightMargin = dp(8f) }
        })
        addView(TextView(this@MainActivity).apply {
            text = getString(R.string.apps_and_settings)
            setTextColor(Roost.MUTED)
            textSize = 14f
        })
    }

    private fun spacer(heightPx: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, heightPx)
    }

    /** Expands to fill leftover space, pushing "Apps & settings" to the bottom (with fillViewport). */
    private fun weightedSpacer(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    }

    // --- Featured-agent hero card -----------------------------------------------------------
    // A full-width horizontal card: a ~60dp rounded app icon (the featured app's real icon, or a
    // dashed placeholder + starburst if it isn't installed), the app's display name + a mono subtitle,
    // and a "FEATURED" pill. Tapping launches the agent. This replaces the old "first grid tile with an
    // accent ring", so the agent shows exactly once (hero here, not in the grid).
    // Returns null when the agent has been hidden via the long-press menu (Prefs.isHidden "agent"),
    // mirroring the old grid tile's `if (!Prefs.isHidden(this, "agent"))` guard so Hide isn't a no-op.
    private fun featuredHero(): View? {
        if (Prefs.isHidden(this, "agent")) return null
        val agentPkg = Prefs.agentPkg(this)
        val installed = isInstalled(agentPkg)
        // Accent-washed so the agent hero pops apart from the neutral tile sections below it (ADR-0007).
        val heroFill = Roost.soft(accent)
        val heroBorder = Roost.withAlpha(accent, 0x40)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = Roost.rounded(heroFill, dp(22f).toFloat(), heroBorder, dp(1f))
            setPadding(dp(15f), dp(15f), dp(15f), dp(15f))
            isClickable = true
            setOnClickListener { launchAgent() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Icon box (60dp, radius 17).
        val box = FrameLayout(this).apply {
            val s = dp(60f)
            layoutParams = LinearLayout.LayoutParams(s, s).apply { rightMargin = dp(15f) }
        }
        val override = overrideIcon("agent")
        if (installed || override != null) {
            box.background = Roost.rounded(Roost.TILE, dp(17f).toFloat(), Roost.HAIRLINE, dp(1f))
            box.addView(ImageView(this).apply {
                setImageDrawable(override ?: appIcon(agentPkg))
                val p = dp(9f); setPadding(p, p, p, p)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            })
        } else {
            // Not installed: dashed placeholder + accent starburst, per the mockup.
            box.background = GradientDrawableCompatDashed(dp(17f).toFloat())
            box.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_agent_star)
                setColorFilter(accent)
                val p = dp(17f); setPadding(p, p, p, p)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
            })
        }
        card.addView(box)

        // Name + mono subtitle (weighted so the pill hugs the right edge).
        val mid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        mid.addView(TextView(this).apply {
            text = appLabel(agentPkg) ?: "Agent"
            setTextColor(Roost.TEXT)
            textSize = 18f
            typeface = Roost.medium()
            maxLines = 1
        })
        mid.addView(TextView(this).apply {
            text = if (installed) getString(R.string.featured_subtitle)
            else getString(R.string.featured_not_installed)
            setTextColor(0xFF8F8578.toInt())
            textSize = 10.5f
            typeface = Typeface.MONOSPACE
            maxLines = 1
            setPadding(0, dp(3f), 0, 0)
        })
        card.addView(mid)

        // "FEATURED" pill — accent text + soft-accent border.
        card.addView(TextView(this).apply {
            text = getString(R.string.featured)
            setTextColor(accent)
            textSize = 9f
            letterSpacing = 0.12f
            typeface = Typeface.MONOSPACE
            setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
            background = Roost.rounded(0, dp(20f).toFloat(), Roost.soft(accent), dp(1f))
        })

        // Preserve long-press affordances (change icon / hide) on the featured item.
        tileMenu(card, "agent") { uninstallApp(agentPkg) }
        return card
    }

    /** A rounded-rect with a dashed hairline stroke — the "no app yet" placeholder fill. */
    @Suppress("FunctionName")
    private fun GradientDrawableCompatDashed(radiusPx: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Roost.withAlpha(0xFFFFFFFF.toInt(), 0x08))
            cornerRadius = radiusPx
            setStroke(dp(1f), Roost.withAlpha(0xFFFFFFFF.toInt(), 0x28), dp(3f).toFloat(), dp(3f).toFloat())
        }

    // --- "Waking up" boot intro (framework-only; gated behind Prefs.bootIntro) --------------
    // A brief boot sequence: the awake mascot + a monospace terminal boot log that fades in line by
    // line, then "good morning.", then it reveals the curated home. Driven entirely by Handler-delayed
    // reveals + ViewPropertyAnimator fades (no animation library). Reaching home on its own means this
    // can never strand the launcher on the intro screen.
    private fun renderWaking(launchAgentWhenDone: Boolean = false) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(30f), dp(30f), dp(30f), dp(40f))
        }
        col.addView(mascot(dp(150f)).apply { awake = true })
        col.addView(spacer(dp(24f)))

        val log = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(dp(240f), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        fun logLine(res: Int, big: Boolean): TextView = TextView(this).apply {
            text = if (big) getString(res) else bootLine(getString(res))
            setTextColor(if (big) Roost.TEXT else 0xFF8F8578.toInt())
            textSize = if (big) 15f else 12f
            typeface = if (big) Typeface.DEFAULT else Typeface.MONOSPACE
            alpha = 0f
            setPadding(0, if (big) dp(8f) else dp(3f), 0, dp(3f))
        }
        val l1 = logLine(R.string.boot_line_mount, false)
        val l2 = logLine(R.string.boot_line_tunnel, false)
        val l3 = logLine(R.string.boot_line_online, false)
        val l4 = logLine(R.string.boot_good_morning, true)
        listOf(l1, l2, l3, l4).forEach { log.addView(it) }
        col.addView(log)

        setContentView(FrameLayout(this).apply {
            background = Roost.dockBackground(this@MainActivity)
            addView(
                col,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { gravity = Gravity.CENTER }
            )
        })

        fun reveal(v: View) { v.animate().alpha(1f).setDuration(400).start() }
        bootHandler.postDelayed({ reveal(l1) }, 150)
        bootHandler.postDelayed({ reveal(l2) }, 550)
        bootHandler.postDelayed({ reveal(l3) }, 950)
        bootHandler.postDelayed({ reveal(l4) }, 1450)
        // Finally, hand off: for a boot launch, actually launch the agent (consuming the pending flag
        // was already done in onResume) so the intro doesn't silently defeat auto-launch-on-boot; if the
        // agent can't launch, fall back to the curated home. For a manual/preview intro, end on home.
        bootHandler.postDelayed({
            if (launchAgentWhenDone) { if (!launchAgent()) renderHome() } else renderHome()
        }, 2900)
    }

    /** Style a "[ ok ] …" boot line so the "[ ok ]" prefix is accent-colored. */
    private fun bootLine(line: String): CharSequence {
        val end = line.indexOf(']')
        if (end < 0) return line
        val sp = SpannableString(line)
        sp.setSpan(ForegroundColorSpan(accent), 0, end + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
    }

    // --- VPN (WireGuard) --------------------------------------------------------------------

    /** A tappable "vpn up/off" pill, centered. Only shown when WireGuard is installed. */
    private fun vpnChip(): View? {
        if (!isInstalled(WIREGUARD_PKG)) return null
        val chip = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
            isClickable = true
            setOnClickListener { toggleVpn() }
        }
        vpnChipView = chip
        applyVpnChip(chip)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, 0)
            addView(chip)
        }
    }

    private fun applyVpnChip(chip: TextView) {
        val up = vpnUp()
        val base = getString(if (up) R.string.vpn_up else R.string.vpn_off)
        // When the VPN is up, ALWAYS show the up/down rates (even 0), independent of the graph toggle.
        // Lead with a status dot ("● wg up …") like the mockup's green chip; the whole chip is Sage.
        chip.text = if (up)
            "● $base  ↓${formatRate(rxRate)} ↑${formatRate(txRate)}" else base
        // "Up" uses the Sage semantic color (fill 10%, border 20%, text + dot Sage) per the mockup;
        // "down"/off stays muted. Sage is a fixed color here, deliberately NOT the themeable accent.
        chip.setTextColor(if (up) Roost.SAGE else Roost.MUTED)
        chip.background = Roost.rounded(
            if (up) Roost.withAlpha(Roost.SAGE, 0x1A) else Roost.TILE, dp(20f).toFloat(),
            if (up) Roost.withAlpha(Roost.SAGE, 0x33) else Roost.HAIRLINE, dp(1f)
        )
    }

    private fun formatRate(bps: Long): String {
        val units = arrayOf("B", "K", "M", "G")
        var v = bps.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
        return if (i == 0 || v >= 100) "${v.toInt()}${units[i]}" else String.format("%.1f%s", v, units[i])
    }

    private fun refreshVpnChip() = vpnChipView?.let { applyVpnChip(it) }

    private fun vpnUp(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    private fun toggleVpn() {
        val tunnel = Prefs.wireguardTunnel(this).trim()
        if (tunnel.isEmpty()) {
            launchPackage(WIREGUARD_PKG) // no tunnel configured → just open WireGuard
            return
        }
        val action = if (vpnUp()) "$WIREGUARD_PKG.action.SET_TUNNEL_DOWN"
        else "$WIREGUARD_PKG.action.SET_TUNNEL_UP"
        sendBroadcast(Intent(action).setPackage(WIREGUARD_PKG).putExtra("tunnel", tunnel))
        vpnChipView?.postDelayed({ refreshVpnChip() }, 1200)
    }

    private fun openPlayStore() {
        val i = packageManager.getLaunchIntentForPackage("com.android.vending")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=apps"))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.not_installed, "Play Store"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun isInstalled(pkg: String): Boolean =
        try { packageManager.getApplicationInfo(pkg, 0); true } catch (e: Exception) { false }

    // --- Long-press tile actions (Gitea issue #2) -------------------------------------------

    // [editHttpId] non-null adds an "Edit" item that opens the HTTP-action builder for that action id
    // (only HTTP action tiles are editable in the builder — SHORTCUT/HASS tiles pass null, so no Edit).
    private fun tileMenu(anchor: View, key: String, editHttpId: String? = null, onDelete: () -> Unit) {
        anchor.setOnLongClickListener {
            val p = PopupMenu(this, anchor)
            if (editHttpId != null) p.menu.add(0, 5, 0, getString(R.string.tile_edit))
            p.menu.add(0, 1, 1, getString(R.string.tile_hide))
            p.menu.add(0, 2, 2, getString(R.string.tile_delete))
            p.menu.add(0, 3, 3, getString(R.string.tile_change_icon))
            if (Prefs.iconOverride(this, key) != null) {
                p.menu.add(0, 4, 4, getString(R.string.tile_reset_icon))
            }
            p.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    1 -> { Prefs.setHidden(this, key, true); render(); true }
                    2 -> { onDelete(); render(); true }
                    3 -> { openIconPicker(key); true }
                    4 -> { Prefs.setIconOverride(this, key, null); render(); true }
                    5 -> {
                        startActivity(Intent(this, HttpActionActivity::class.java)
                            .putExtra(HttpActionActivity.EXTRA_ID, editHttpId))
                        true
                    }
                    else -> false
                }
            }
            p.show()
            true
        }
    }

    private fun uninstallApp(pkg: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.action_failed, pkg), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openIconPicker(key: String) {
        startActivity(Intent(this, IconPickerActivity::class.java).putExtra(IconPickerActivity.EXTRA_KEY, key))
    }

    /** A user-chosen icon override for [key], or null. */
    private fun overrideIcon(key: String): Drawable? {
        val path = Prefs.iconOverride(this, key) ?: return null
        return IconStore.drawableFor(this, path)
    }

    // --- Uniform tile render (pluggable — SPEC-0001 / SPEC-0002 / ADR-0007) -----------------

    /** Lay pre-built tile [tiles] out per [density]: SLIM/REGULAR a vertical list (5/9dp gaps), RICH a
     *  2-column card grid (10dp gutters). Every home tile — apps, web, shortcuts, HASS, HTTP, and the
     *  trailing Store ghost — flows through here, so they all share one presentation (owner feedback). */
    private fun renderTiles(tiles: List<View>, density: ActionDensity): View {
        val zone = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        if (density == ActionDensity.RICH) {
            tiles.chunked(2).forEachIndexed { rowIndex, pair ->
                val gridRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = if (rowIndex == 0) 0 else dp(10f) }
                }
                pair.forEachIndexed { col, tileView ->
                    // Paired cells use MATCH_PARENT height so both take the row's tallest height (a
                    // horizontal LinearLayout re-measures MATCH_PARENT children to the max), aligning a
                    // card with a subtitle line next to a shorter one; the RICH card's internal weighted
                    // spacer absorbs the slack, keeping the status pinned to the bottom. A LONE tile (odd
                    // count) has no real sibling to match — only the 1px spacer below — so MATCH_PARENT
                    // would re-measure it down to ~1px and collapse it; use WRAP_CONTENT there.
                    val cellHeight = if (pair.size == 1) ViewGroup.LayoutParams.WRAP_CONTENT
                                     else ViewGroup.LayoutParams.MATCH_PARENT
                    gridRow.addView(tileView, LinearLayout.LayoutParams(
                        0, cellHeight, 1f
                    ).apply { if (col > 0) leftMargin = dp(10f) })
                }
                // A lone last tile takes one column; a weighted spacer keeps it half-width.
                if (pair.size == 1) gridRow.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dp(10f) }
                })
                zone.addView(gridRow)
            }
        } else {
            val gap = if (density == ActionDensity.SLIM) dp(5f) else dp(9f)
            tiles.forEachIndexed { i, tileView ->
                zone.addView(tileView, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = if (i == 0) 0 else gap })
            }
        }
        return zone
    }

    /** Build one configured action tile (bind + delete-menu) for the given [density]. Each kind carries
     *  its own subtitle tagline + idle status; launch kinds (APP/WEB/SHORTCUT) show no status line while
     *  fire kinds (HTTP/HASS_SCENE) keep the on-tile firing state machine (owner feedback / ADR-0007). */
    private fun buildActionTile(b: ActionButton, density: ActionDensity): ActionTileView {
        val http = if (b.kind == ActionKind.HTTP) Prefs.httpAction(this, b.a) else null
        val isTask = http != null && HttpActionClient.hostOf(http.url).contains("switchboard")
        // Per-kind (subtitle, idleStatus, showStatus): apps are icon+name only; web shows its host;
        // shortcuts read "shortcut"; HTTP shows "METHOD · host" + a fire status; scenes read "scene".
        val (subtitle, idleStatus, showStatus) = when (b.kind) {
            ActionKind.APP -> Triple("", "", false)
            ActionKind.WEB -> Triple(HttpActionClient.hostOf(b.a), "", false)
            ActionKind.SHORTCUT -> Triple("shortcut", "", false)
            ActionKind.HTTP -> {
                val sub = if (http == null) "" else "${http.method} · ${HttpActionClient.hostOf(http.url)}"
                Triple(sub, "tap to fire", true)
            }
            ActionKind.HASS_SCENE -> Triple("scene", "tap to run", true)
        }
        val override = overrideIcon(b.key)
        // Tint only monochrome glyphs with the accent: a picked/synced override iff it's a mono slug icon
        // (Simple Icons / Heroicons), else the built-in ic_scene glyph on HTTP / HASS_SCENE. Full-color
        // launcher icons (the LAUNCH kinds SHORTCUT/APP/WEB), selfh.st logos, and picked full-color
        // overrides render untinted. (Fix 3 / ADR-0007.)
        val tintIcon = if (override != null) Prefs.iconOverrideIsMono(this, b.key)
                       else b.kind !in LAUNCH_KINDS
        val tile = ActionTileView(this, accent).apply {
            bind(
                title = tileTitle(b),
                idleIcon = override ?: actionIcon(b),
                isTask = isTask,
                subtitle = subtitle,
                density = density,
                idleStatus = idleStatus,
                showStatus = showStatus,
                tintIdleIcon = tintIcon
            )
            onFire = { invokeAction(b, this) }
        }
        // Deleting an HTTP action removes its definition + secret too (not just the button),
        // mirroring how removing a HASS account cleans up its scenes. Long-press → Edit (HTTP only)
        // reopens the builder with this action loaded (owner feedback).
        tileMenu(tile, b.key, editHttpId = if (b.kind == ActionKind.HTTP) b.a else null) { deleteTile(b) }
        return tile
    }

    /** Kinds that open something on tap and never enter the firing state machine. */
    private val LAUNCH_KINDS = setOf(ActionKind.SHORTCUT, ActionKind.APP, ActionKind.WEB)

    /** Display title for any tile — app-shortcuts read "<shortcut> in <App>", the rest use their title. */
    private fun tileTitle(b: ActionButton): String =
        if (b.kind == ActionKind.SHORTCUT) ShortcutProvider.displayTitle(b, appLabel(b.a)) else b.title

    /** Long-press "Delete" per kind: uninstall an app, forget a web app, delete an HTTP action, or just
     *  un-enable a shortcut / HASS scene (they re-appear from their provider). */
    private fun deleteTile(b: ActionButton) {
        when (b.kind) {
            ActionKind.APP -> uninstallApp(b.a)
            ActionKind.WEB -> Prefs.removeWebApp(this, b.a)
            ActionKind.HTTP -> Prefs.removeHttpAction(this, b.a)
            ActionKind.SHORTCUT, ActionKind.HASS_SCENE -> Prefs.setActionEnabled(this, b, false)
        }
    }

    // Governing: ADR-0002 (pluggable action-button providers), SPEC-0002 REQ "General HTTP-action definition"
    private fun actionIcon(b: ActionButton): Drawable? = when (b.kind) {
        ActionKind.SHORTCUT -> ShortcutProvider.icon(this, b.a, b.b) ?: appIcon(b.a)
        ActionKind.HASS_SCENE ->
            try { resources.getDrawable(R.drawable.ic_scene, theme)?.mutate() } catch (e: Exception) { null }
        ActionKind.HTTP ->
            try { resources.getDrawable(R.drawable.ic_scene, theme)?.mutate() } catch (e: Exception) { null }
        ActionKind.APP -> appIcon(b.a)
        ActionKind.WEB -> webIcon()
    }

    // Drives the tapped tile's on-tile state machine instead of a Toast (ADR-0004). All network runs
    // off-thread; results marshal back with runOnUiThread.
    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Threading and error handling"
    private fun invokeAction(b: ActionButton, tile: ActionTileView) {
        if (tile.isPending()) return
        when (b.kind) {
            // LAUNCH kinds — open something and stay quiet (no pending/success/error/timeout). ADR-0007.
            ActionKind.APP -> launchPackage(b.a)
            ActionKind.WEB -> openWebApp(b.a)

            ActionKind.SHORTCUT -> {
                // Fix 9: SHORTCUT launches synchronously (never PENDING) so isPending() can't debounce
                // it — ignore a re-tap within the debounce window so a double-tap launches once.
                val now = SystemClock.uptimeMillis()
                if (now - lastShortcutFireAt < SHORTCUT_DEBOUNCE_MS) return
                lastShortcutFireAt = now
                if (ShortcutProvider.invoke(this, b.a, b.b)) tile.flashSuccess()
                else tile.showError("ERROR", "couldn't start shortcut")
            }

            ActionKind.HASS_SCENE -> {
                val acct = Prefs.hassAccounts(this).find { it.id == b.a }
                if (acct == null) { tile.showError("ERROR", "account missing"); return }
                tile.showPending()
                Thread {
                    val ok = runCatching { Hass.activateScene(acct, b.b) }.isSuccess
                    runOnUiThread {
                        // Fix 10: onResume→renderHome may have rebuilt tiles; don't touch a detached view.
                        if (!tile.isAttachedToWindow) return@runOnUiThread
                        if (ok) tile.showSuccess(200) else tile.showError("ERROR", "scene failed")
                    }
                }.start()
            }

            ActionKind.HTTP -> {
                val action = Prefs.httpAction(this, b.a)
                if (action == null) { tile.showError("ERROR", "definition missing"); return }
                val secret = Prefs.httpSecret(this, b.a)
                val vars = HttpActionClient.defaultVars(this)
                tile.showPending()
                Thread {
                    val res = HttpActionClient.fire(action, secret, vars)
                    runOnUiThread {
                        // Fix 10: a background/return rebuilds tiles (renderHome); if this tile was
                        // detached meanwhile, skip the update so it never touches a dead view.
                        // (Full firing-state preservation across re-render is out of scope.)
                        if (!tile.isAttachedToWindow) return@runOnUiThread
                        when {
                            res.timeout -> tile.showTimeout()
                            res.ok && (tile.isTask || res.code == 202) -> tile.showQueued(res.code)
                            res.ok -> tile.showSuccess(res.code)
                            else -> tile.showError(
                                if (res.code > 0) res.code.toString() else "ERROR",
                                res.reason.ifBlank { "request failed" }
                            )
                        }
                    }
                }.start()
            }
        }
    }

    // --- Resolution / launching -------------------------------------------------------------

    private fun appIcon(pkg: String): Drawable? =
        try { packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    private fun appLabel(pkg: String): String? = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun webIcon(): Drawable? =
        try { resources.getDrawable(R.drawable.ic_web, theme)?.mutate() } catch (e: Exception) { null }

    private fun openWebApp(url: String) {
        startActivity(Intent(this, WebAppActivity::class.java).putExtra(WebAppActivity.EXTRA_URL, url))
    }

    private fun launchAgent(): Boolean = launchPackage(Prefs.agentPkg(this))

    private fun launchPackage(pkg: String): Boolean {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } else {
            Toast.makeText(this, getString(R.string.not_installed, pkg), Toast.LENGTH_SHORT).show()
            false
        }
    }

    companion object {
        private const val WIREGUARD_PKG = "com.wireguard.android"

        // "Awake" presence tuning: >3 KB/s of combined traffic counts as activity; the mascot stays
        // awake for ~6s after the last active tick so it reads as calm "working…", not a strobe.
        private const val AWAKE_THRESHOLD = 3L * 1024L
        private const val AWAKE_LINGER_MS = 6000L

        // Ignore a second SHORTCUT invoke within this window (fast double-tap → launch once). (Fix 9.)
        private const val SHORTCUT_DEBOUNCE_MS = 600L
    }
}
