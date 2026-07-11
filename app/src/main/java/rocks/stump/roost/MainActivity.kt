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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
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
    private var rxRate = 0L
    private var txRate = 0L

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
            rateHandler.postDelayed(this, 1000L)
        }
    }

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
            if (launchAgent()) return
        }

        render()
        registerBattery()
        startRatePoll()
    }

    override fun onPause() {
        super.onPause()
        unregisterBattery()
        stopRatePoll()
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
        col.addView(spacer(dp(24f)))
        col.addView(utilityGrid())
        actionsZone()?.let { col.addView(it) }
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
        isClickable = true
        setOnClickListener { launchAgent() }
        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
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
        return if (name.isNotEmpty()) getString(R.string.greeting_named, name)
        else getString(R.string.greeting_home)
    }

    private fun statusLine(): String {
        val (pct, charging) = battery()
        val who = Prefs.agentName(this).trim().ifEmpty { "roost" }
        val word = if (charging) "docked & charging" else "on battery"
        return if (pct >= 0) "$who · $word · $pct%" else "$who · $word"
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

    // Rows of 3 using a vertical LinearLayout of horizontal rows with WEIGHTED spacers between
    // tiles (space-between) so column 1 hugs the left content edge and column 3 hugs the right.
    // GridLayout weight sizing is unreliable, so we lay out rows + spacers by hand.
    private fun utilityGrid(): View {
        val columns = 3
        val tileWidth = dp(78f)
        val agentPkg = Prefs.agentPkg(this)
        val tiles = mutableListOf<View>()

        // Featured agent — same tile size/style as the rest, marked with an accent ring.
        if (!Prefs.isHidden(this, "agent")) {
            val at = tile(
                appLabel(agentPkg) ?: "Agent",
                overrideIcon("agent") ?: appIcon(agentPkg), tileWidth, ringed = true
            ) { launchAgent() }
            tileMenu(at, "agent") { uninstallApp(agentPkg) }
            tiles.add(at)
        }

        // Installed favorites (minus the agent + hidden), alphabetical.
        Prefs.favorites(this)
            .filter { it != agentPkg && !Prefs.isHidden(this, "app:$it") }
            .mapNotNull { pkg -> appLabel(pkg)?.let { pkg to it } }
            .sortedBy { it.second.lowercase() }
            .forEach { (pkg, label) ->
                val t = tile(label, overrideIcon("app:$pkg") ?: appIcon(pkg), tileWidth) { launchPackage(pkg) }
                tileMenu(t, "app:$pkg") { uninstallApp(pkg) }
                tiles.add(t)
            }

        // Web apps — fullscreen WebView tiles.
        Prefs.webApps(this)
            .filter { !Prefs.isHidden(this, "web:${it.url}") }
            .forEach { wa ->
                val ov = overrideIcon("web:${wa.url}")
                val t = tile(wa.name, ov ?: webIcon(), tileWidth, iconTint = if (ov != null) null else WEB_ICON_TINT) {
                    openWebApp(wa.url)
                }
                tileMenu(t, "web:${wa.url}") { Prefs.removeWebApp(this, wa.url) }
                tiles.add(t)
            }

        // Store — get more apps.
        tiles.add(tile(getString(R.string.store), null, tileWidth, isAdd = true) {
            openPlayStore()
        })

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        tiles.chunked(columns).forEachIndexed { rowIndex, rowTiles ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = if (rowIndex == 0) 0 else dp(6f) }
            }
            for (col in 0 until columns) {
                // Weighted spacer BETWEEN columns → space-between across the content width.
                if (col > 0) row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
                val t = rowTiles.getOrNull(col)
                if (t != null) {
                    row.addView(t)
                } else {
                    // Empty placeholder equal to the tile width keeps a lone/partial row's tiles
                    // pinned to the same column x-positions as the full rows above.
                    row.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(tileWidth, 1)
                    })
                }
            }
            container.addView(row)
        }
        return container
    }

    private fun tile(
        label: String,
        icon: Drawable?,
        widthPx: Int,
        isAdd: Boolean = false,
        ringed: Boolean = false,
        iconTint: Int? = null,
        onClick: () -> Unit
    ): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6f), 0, dp(6f))
            isClickable = true
            setOnClickListener { onClick() }
        }

        val borderColor = when {
            ringed -> accent
            isAdd -> Roost.soft(accent)
            else -> Roost.HAIRLINE
        }
        val surface = FrameLayout(this).apply {
            background = Roost.rounded(Roost.TILE, dp(16f).toFloat(), borderColor, dp(if (ringed) 2f else 1f))
            val s = dp(66f)
            layoutParams = LinearLayout.LayoutParams(s, s)
        }
        surface.addView(ImageView(this).apply {
            if (isAdd) {
                setImageResource(R.drawable.ic_plus)
                setColorFilter(accent)
                val p = dp(11f); setPadding(p, p, p, p)
            } else {
                setImageDrawable(icon)
                iconTint?.let { setColorFilter(it) }
                val p = dp(9f); setPadding(p, p, p, p)
            }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        })
        cell.addView(surface)

        cell.addView(TextView(this).apply {
            text = label
            setTextColor(Roost.MUTED)
            textSize = 12.5f
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(7f), 0, 0)
        })

        cell.layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
        return cell
    }

    private fun appsSettingsLink(topPx: Int): TextView = TextView(this).apply {
        text = getString(R.string.apps_and_settings)
        setTextColor(Roost.MUTED)
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(0, topPx, 0, 0)
        setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
    }

    private fun spacer(heightPx: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, heightPx)
    }

    /** Expands to fill leftover space, pushing "Apps & settings" to the bottom (with fillViewport). */
    private fun weightedSpacer(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
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
        chip.text = if (up)
            "$base  ↓${formatRate(rxRate)} ↑${formatRate(txRate)}" else base
        chip.setTextColor(if (up) accent else Roost.MUTED)
        chip.background = Roost.rounded(
            if (up) Roost.soft(accent) else Roost.TILE, dp(20f).toFloat(),
            if (up) Roost.soft(accent) else Roost.HAIRLINE, dp(1f)
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

    private fun tileMenu(anchor: View, key: String, onDelete: () -> Unit) {
        anchor.setOnLongClickListener {
            val p = PopupMenu(this, anchor)
            p.menu.add(0, 1, 0, getString(R.string.tile_hide))
            p.menu.add(0, 2, 1, getString(R.string.tile_delete))
            p.menu.add(0, 3, 2, getString(R.string.tile_change_icon))
            if (Prefs.iconOverride(this, key) != null) {
                p.menu.add(0, 4, 3, getString(R.string.tile_reset_icon))
            }
            p.setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    1 -> { Prefs.setHidden(this, key, true); render(); true }
                    2 -> { onDelete(); render(); true }
                    3 -> { openIconPicker(key); true }
                    4 -> { Prefs.setIconOverride(this, key, null); render(); true }
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

    // --- Actions zone (pluggable — SPEC-0001 / SPEC-0002) -----------------------------------

    // A dedicated "Actions" band below the app grid: a mono uppercase header + "+ new" link, then a
    // vertical column of ActionTileView tiles (one per enabled ActionButton). HTTP and HASS_SCENE
    // buttons fire through the on-tile state machine; SHORTCUT buttons launch on tap. No actions → no
    // zone (the whole band is null).
    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Actions zone placement"
    private fun actionsZone(): View? {
        val buttons = Prefs.actionButtons(this).filter { !Prefs.isHidden(this, it.key) }
        if (buttons.isEmpty()) return null

        val zone = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24f), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2f), 0, dp(2f), dp(12f))
        }
        head.addView(TextView(this).apply {
            text = "ACTIONS"
            setTextColor(0xFF6E665B.toInt())
            textSize = 10f
            letterSpacing = 0.15f
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(TextView(this).apply {
            text = "+ new"
            setTextColor(accent)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, EndpointsActivity::class.java)) }
        })
        zone.addView(head)

        buttons.forEach { b ->
            val http = if (b.kind == ActionKind.HTTP) Prefs.httpAction(this, b.a) else null
            val isTask = http != null && HttpActionClient.hostOf(http.url).contains("switchboard")
            val host = http?.let { HttpActionClient.hostOf(it.url) } ?: ""
            val tile = ActionTileView(this, accent).apply {
                bind(
                    title = b.title.substringAfterLast(" · "),
                    idleIcon = overrideIcon(b.key) ?: actionIcon(b),
                    isTask = isTask,
                    host = host
                )
                onFire = { invokeAction(b, this) }
            }
            tileMenu(tile, b.key) { Prefs.setActionEnabled(this, b, false) }
            zone.addView(tile, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10f) })
        }
        return zone
    }

    // Governing: ADR-0002 (pluggable action-button providers), SPEC-0002 REQ "General HTTP-action definition"
    private fun actionIcon(b: ActionButton): Drawable? = when (b.kind) {
        ActionKind.SHORTCUT -> ShortcutProvider.icon(this, b.a, b.b) ?: appIcon(b.a)
        ActionKind.HASS_SCENE ->
            try { resources.getDrawable(R.drawable.ic_scene, theme)?.mutate() } catch (e: Exception) { null }
        ActionKind.HTTP ->
            try { resources.getDrawable(R.drawable.ic_scene, theme)?.mutate() } catch (e: Exception) { null }
    }

    // Drives the tapped tile's on-tile state machine instead of a Toast (ADR-0004). All network runs
    // off-thread; results marshal back with runOnUiThread.
    // Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "Threading and error handling"
    private fun invokeAction(b: ActionButton, tile: ActionTileView) {
        if (tile.isPending()) return
        when (b.kind) {
            ActionKind.SHORTCUT ->
                if (ShortcutProvider.invoke(this, b.a, b.b)) tile.flashSuccess()
                else tile.showError("ERROR", "couldn't start shortcut")

            ActionKind.HASS_SCENE -> {
                val acct = Prefs.hassAccounts(this).find { it.id == b.a }
                if (acct == null) { tile.showError("ERROR", "account missing"); return }
                tile.showPending()
                Thread {
                    val ok = runCatching { Hass.activateScene(acct, b.b) }.isSuccess
                    runOnUiThread { if (ok) tile.showSuccess(200) else tile.showError("ERROR", "scene failed") }
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
        private val WEB_ICON_TINT = 0xFFD6CDBF.toInt()
        private const val WIREGUARD_PKG = "com.wireguard.android"
    }
}
