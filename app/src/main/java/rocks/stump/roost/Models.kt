package rocks.stump.roost

/**
 * Action buttons are pluggable by provider. Each button has an icon + short title and, when tapped,
 * does one thing. Adding a provider = a new [ActionKind] + a scanner (for the picker) + an invoker.
 * Providers today: Android app-shortcuts, Home Assistant scenes, and the generalized HTTP action.
 *
 * Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "General HTTP-action definition"
 */
enum class ActionKind { SHORTCUT, HASS_SCENE, HTTP }

/**
 * A single enabled action button. [a]/[b] are kind-specific args:
 *  - SHORTCUT   : a = package, b = shortcutId
 *  - HASS_SCENE : a = HassAccount.id, b = scene entity_id
 *  - HTTP       : a = HttpAction.id, b = "" (the full request lives in the http_actions collection,
 *                 exactly as HASS_SCENE keys a = HassAccount.id — ADR-0002/ADR-0004)
 */
data class ActionButton(
    val kind: ActionKind,
    val key: String,
    val title: String,
    val a: String,
    val b: String
)

/**
 * How the home "Actions" zone renders its action tiles — one setting for the whole zone. All three
 * densities share the same on-tile firing state machine (idle → pending → success/queued → error →
 * timeout) and the fixed Sage/Amber/Clay ramp; only the disc size, spacing, and chrome change.
 *  - SLIM    : a compact per-state-tinted card row (24dp disc + label + terse mono code); a vertical list
 *  - REGULAR : the default card (36dp disc + label-over-status + a "task" chip); a vertical list
 *  - RICH    : a tall grid card (42dp disc on top + label + "METHOD · host" line + bottom status), laid
 *              two-up into a grid by MainActivity.actionsZone
 *
 * Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "On-tile firing state machine"
 */
enum class ActionDensity { SLIM, REGULAR, RICH }

/** A Home Assistant instance the user has connected. [token] is a long-lived access token. */
data class HassAccount(
    val id: String,
    val name: String,
    val url: String,
    val token: String
)

/** Authentication scheme applied by [HttpActionClient] when firing an [HttpAction]. */
enum class HttpAuth { NONE, BEARER, HMAC }

/**
 * A saved HTTP request definition — the whole "does a thing" primitive of ADR-0004. The secret
 * (Bearer token / HMAC shared secret) is stored separately in [Prefs], keyed by [id], so it can
 * never leak into a label, echo, or error detail.
 *
 * Governing: ADR-0004 (generalized HTTP-action provider), SPEC-0002 REQ "General HTTP-action definition"
 */
data class HttpAction(
    val id: String,
    val method: String,
    val url: String,
    val headers: List<Pair<String, String>>,
    val auth: HttpAuth,
    val body: String
)
