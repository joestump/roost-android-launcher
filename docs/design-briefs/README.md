# Roost design-brief handoffs

This folder holds four **feature design briefs** for Roost — the vendor-neutral Android launcher that
turns a spare phone into a dedicated device for one AI agent. Each brief is a **self-contained handoff**:
paste it into a fresh, design-focused Claude session and that session has everything it needs — Roost's
identity, the warm-dark palette, the framework-only constraints, the exact files to read, and the one
hard UX problem to solve — with none of the build-conversation history. They are meant to be handed off
**one at a time**, each producing mockups + a component spec for a single slice of Roost. Together they
extend the two foundational briefs at the repo root (linked at the bottom) from "what Roost looks like"
into "what Roost *does*."

## The four briefs

| Brief | One line | Ideation ideas it covers | The key design problem |
| --- | --- | --- | --- |
| [`a-http-action-tile.md`](a-http-action-tile.md) — **Generic HTTP-Action Tile** | The universal "does a thing" tile that fires an HTTP request from the home surface, with live success/failure feedback. | Action buttons; Fire-a-Task (durable Switchboard jobs); folding the HASS scene button into one generic primitive; the config builder + endpoint picker. | An HTTP request is async and can fail, and the feedback has to live **on a thumbnail-sized tile** legible across a dark room: an on-tile `idle → pending → success → error (+ timeout)` state machine, not a Toast. |
| [`b-agent-presence.md`](b-agent-presence.md) — **Agent Presence & Ambient Status** | Turn the home header into a calm ambient status zone readable across a dark room. **Owns the mascot state machine.** | Mascot state system; live model chip + routing presets; the agent status beacon (current task · phase · elapsed); stack-health + spend grid. | A hierarchy of **glance distances** — across-the-room (mascot), at-a-glance (chips + beacon), on-tap (health/spend) — layering four new live signals onto a header that already has four, without turning home into a Grafana panel. |
| [`c-voice-loop.md`](c-voice-loop.md) — **The Voice Loop** | Hold-to-talk to file a note to your agent; the dock speaks replies back with a chime and a lit-up mascot. | Push-to-talk summon + recording overlay; the STT→queue pipeline as states; spoken replies (foreground service + TTS); the mic-live privacy model; "Hey Roost" wake word. | Make an **invisible, async, multi-hop, failure-prone pipeline** trustworthy and calm using only the mascot's eyes, a few shapes, and one accent color — with a mic-live indicator you can trust at a glance. |
| [`d-agent-io.md`](d-agent-io.md) — **Two-way Agent I/O** | Hand files/photos/text to the agent and see what it hands back — plus Approve/Deny human gates — in two taps. | Share-to-agent inbox (system share sheet → `~/inbox`); the outbox artifact spotlight + framework-only previewer; Approve/Deny notifications and decision cards. | Make **async, offline-ish Syncthing sync feel confident** without overclaiming (`queued / synced / seen`), stay ambient-legible in a dim room, and render markdown/logs with no rendering library. |

## Shared design language (what every brief assumes)

These four briefs are not independent products — they are rooms in the same warm-dark house. Three
cross-cutting pieces are assumed by all of them, and drift between them is the thing to watch:

- **One palette, quoted from `Roost.kt`.** Dock `#14110D` (radial → `#1D1912`), panel `#1C1813`, tile
  `#2A241C`, warm-cream text `#F3EEE4`, warm-taupe muted `#A29A8C`, hairline `0x14FFFFFF` (~8% white), and a
  **single themeable accent** — Honey `#E7A44E` by default, with Slate `#7FA6C9`, Sage `#93B98C`, Violet
  `#B79BE0`. All four briefs reproduce this exactly; a fifth brief must too. Brief B additionally proposes a
  small **fixed (non-themeable) semantic ramp** — healthy Sage `#93B98C`, degraded amber `~#D98F3C`, down
  clay-red `~#CF6B5A`, unknown = muted — so "down" reads red even when the accent is red-ish. Treat that ramp
  as shared: the moment another brief needs a status color, it comes from here.
- **The mascot state system — owned by brief B.** The LED-eyed `MascotView` graduates from a single `awake`
  boolean to a real visual state machine: `idle / working / stalled / error / asleep`, plus hook slots for
  the voice states. **Accent-following for normal states; amber/red *override* the accent for stalled/error**
  ("something is wrong" must read regardless of theme). Any brief that lights up the robot's eyes must draw
  from B's vocabulary rather than inventing its own — this is the emotional core and it has one owner.
- **The HTTP-Action primitive — owned by brief A.** Every "POST something and show whether it worked" control
  is one instance of A's tile: the async fire with an on-tile `pending → success/queued → error` read. Brief
  B's routing-preset buttons and brief D's Approve/Deny POST-back are both *instances* of it, not
  re-inventions — they defer the "how does an async POST show pending/✓/✗" mechanics to A and only design
  where the control lives.

## Suggested handoff order

Joe hands these off one at a time. Order them so each session inherits a locked contract from the one
before it, foundations first:

1. **`a-http-action-tile.md` — first.** It defines the HTTP-Action primitive *and* the canonical
   pending/success/error feedback vocabulary that B's presets and D's decision POST-back both reuse. Lock
   this component's states and colors before anything references them.
2. **`b-agent-presence.md` — second.** It owns the mascot state machine and the semantic color ramp — both
   consumed downstream. It also uses A's primitive for its routing presets, so it wants A already settled.
   After B, the robot's states and the status palette are fixed.
3. **`c-voice-loop.md` — third.** It *consumes* B's mascot states (listening/thinking/speaking) and posts via A's
   pattern. It should only run once the mascot vocabulary it depends on is nailed down, so it fills in motion
   rather than negotiating states.
4. **`d-agent-io.md` — last.** It reuses A's POST-back component (Approve/Deny must read identically to A's
   feedback) and leans on the mascot for I/O presence — so it benefits most from both A and B being locked.
   Running it last lets its shared controls inherit final vocabulary instead of drifting.

Rule of thumb: **A and B are the two foundations; C and D are the two consumers.** Hand off the foundations
first, then either consumer.

## The two foundational briefs (repo root)

- [`../DESIGN-BRIEF.md`](../DESIGN-BRIEF.md) — the original identity brief that produced Roost's warm-dark
  "portable robot home" look, the mascot, and the themeable accent.
- [`../SETTINGS-DESIGN-BRIEF.md`](../SETTINGS-DESIGN-BRIEF.md) — the Settings redesign and the shared
  component language (cards, rows, inputs, pickers) that every builder/config screen in these four briefs
  should match.
