package rocks.stump.roost

/**
 * Action buttons are pluggable by provider. Each button has an icon + short title and, when tapped,
 * does one thing. Adding a provider = a new [ActionKind] + a scanner (for the picker) + an invoker.
 * The first two providers are Android app-shortcuts and Home Assistant scenes.
 */
enum class ActionKind { SHORTCUT, HASS_SCENE }

/**
 * A single enabled action button. [a]/[b] are kind-specific args:
 *  - SHORTCUT   : a = package, b = shortcutId
 *  - HASS_SCENE : a = HassAccount.id, b = scene entity_id
 */
data class ActionButton(
    val kind: ActionKind,
    val key: String,
    val title: String,
    val a: String,
    val b: String
)

/** A Home Assistant instance the user has connected. [token] is a long-lived access token. */
data class HassAccount(
    val id: String,
    val name: String,
    val url: String,
    val token: String
)
