# Grimoire

> **Draft.** The prose here must end up in Tyler's own voice before submission —
> `light-workspace/docs/README-CHECKLIST.md` explains what each section is for. The
> "Why this is a clean tool to vet" section is mirrored from `docs/VETTING-DEFENSE.md`
> and must stay identical.

A player companion for 5E-compatible tabletop games, for the Light Phone 3.

Your paper sheet stays the record of who your character is. Grimoire keeps the parts that
change every round and the words you keep having to look up: hit points, spell slots,
feature uses, conditions, coin; one-tap rolls for attacks, checks and saves; the open rules
text (System Reference Document 5.1) with search; and a session journal you can fill in
with the scroll wheel between turns. It works with the radios off and asks for no
permissions.

## What it does

- **Sheet hub** — AC, initiative, speed, proficiency, inspiration, HP and slots at a glance.
- **Turn** — your attacks and prepared spells grouped by action / bonus / reaction; tap to
  roll to-hit and damage together; spells spend a slot as you cast.
- **HP** — damage, healing and temporary HP with the wheel or ±1/5/10; death saves appear
  when you drop to 0.
- **Checks & saves** — every ability, save and skill, one tap each; long-press for
  advantage or disadvantage.
- **Spells, features, conditions, rest** — slot pips, feature counters that reset on the
  right rest, the fifteen conditions with their one-line rules, exhaustion, concentration.
- **Compendium** — spells, conditions, rules, classes, races, equipment, magic items and
  creatures from the SRD 5.1, searchable, readable with the wheel.
- **Journal** — sessions and one-line entries: met, went, quest, got, learned, rumor,
  note. Pick names from rosters instead of typing them.
- **Dice** — a plain roller for everything else.

Screenshots: TODO (use the awesome-light hero-image formatter).

## Why this is a clean tool to vet

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

## Licences and attribution

Grimoire is 5E compatible. It bundles rules text from the System Reference Document 5.1
published by Wizards of the Coast under the Creative Commons Attribution 4.0 International
License (CC-BY-4.0). The text has been reorganized into a searchable database for display
on a small screen: entries were split into fields and paragraphs, and cross-references were
converted to keys. No rules wording was changed.

This work includes material taken from the System Reference Document 5.1 ("SRD 5.1") by Wizards of the Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.

The structured JSON this tool was built from comes from the open-source project
5e-bits/5e-database (MIT-licensed code; the rules text it carries is the SRD 5.1 under
CC-BY-4.0). Its record structure was used; its text is the SRD's.

The full text of CC-BY-4.0 is included as LICENSE-CC-BY-4.0.txt. The bundled data is
provided "as is"; see Section 5 of CC-BY-4.0 for the disclaimer of warranties and
limitation of liability.

Application source code is licensed under the MIT License (see LICENSE in the repository).
This tool is a derivative of lightphone/light-sdk, also MIT-licensed.

## Building

```
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :tool:assembleDebug
```
`tool/src/main/assets/compendium/` is generated by `pipeline/` on a desktop and committed;
see `pipeline/README.md`.
