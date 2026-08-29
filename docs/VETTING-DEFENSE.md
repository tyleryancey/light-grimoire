# Why Grimoire is a clean tool to vet

*Kept current with the design. Copied into `README.md` at submission.*

Grimoire is an offline, finite game aid for a player of a 5E-compatible tabletop game: the
mutable half of a character sheet (hit points, spell slots, feature uses, conditions, coin),
one-tap dice for the rolls a turn needs, the open-licensed rules text a player looks up
mid-session, and a short session journal.

- **Not a feed / not infinite:** every list has a fixed end. A character has a bounded set
  of trackers; the compendium is a fixed 2.6 MB of System Reference Document text that
  never updates by itself; a session log ends when the session ends; dice history is the
  last ten rolls, cleared on relaunch. Nothing refreshes, nothing counts streaks, nothing
  is designed to be checked between sessions.
- **Not browser-adjacent:** native Compose on the `sdk:ui` primitives; no WebView, no
  remote HTML, no links that open anything (the About screen prints the repository URL as
  plain text).
- **Not messaging / social:** single-user. No presence, no sharing, no accounts. Export is
  text on screen (later, QR pages) for the player's own laptop.
- **Not commercial:** open source (MIT code; CC-BY-4.0 data), no ads, no purchases, no
  telemetry, no keys, no upsell. Content is the freely licensed SRD 5.1 only.
- **Not gambling:** dice are a game mechanic — no wagering, no currency, no variable-ratio
  reward loop, no statistics.
- **Permissions:** `[]`. No network (`INTERNET` is not requested), no camera in v1, no
  location, no audio. No `capabilities` declared; `orientation = "portrait"`.
- **Dependencies:** allow-listed only — Compose, Room (+ `room-compiler` via KSP),
  kotlinx-serialization, DataStore, the Light keyboard. No additions requested.
- **Data:** nothing leaves the device. Characters and the journal live in the tool's Room
  database; the compendium is bundled and imported locally.
- **Content licence & branding:** bundled text is SRD 5.1 under CC-BY-4.0 with the
  prescribed attribution (About screen + README). The tool is described as "5E compatible"
  and never uses Wizards' trademarks in its name, description, or UI.
- **Ethos fit:** the phone replaces the eraser and the index, not the table. It is opened
  to answer one question (what do I roll, how many slots are left, what does *grappled*
  do), then put down. Text entry is confined to naming things; play is taps and the wheel.

Reviewer questions we expect, pre-answered:

| Question | Answer |
|---|---|
| Is this niche? | The primitives (dice, counters, a reference reader) are generic game aids with a 5E skin; the compendium is open content. No tabletop aid is listed on awesome-light or GitHub (29 Aug 2026). |
| Why a bestiary in a player tool? | Druids, rangers and summoners read beast stat blocks at the table (wild shape, familiars, conjured animals). It is SRD text, read-only, and the same reader as spells. |
| Does it replace the sheet? | No — the paper sheet stays the record; the tool tracks what changes during play and holds the text. |
| Is the journal a notes app? | A structured, bounded log (seven entry kinds, one-line entries) for one campaign; long-form writing is explicitly pushed to the laptop via export. |
| Hardware wheel use? | If wheel turns reach tools on retail LightOS (M0 test), used only to nudge numbers and scroll lists while the tool is in the foreground, which suppresses LightOS brightness control only while Grimoire is open; unhandled keys are forwarded to LightOS as the SDK intends. If that is unwelcome, every wheel job has a tap target already. |
