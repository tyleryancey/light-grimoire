# ADR-0001 — Ship the 2014 rules (SRD 5.1) first; keep the schema edition-aware

**Status:** accepted (29 Aug 2026) · **Decided by:** Tyler

## Context
Tyler's table plays the 2014 rules on paper sheets. Two SRDs exist under CC-BY-4.0: 5.1
(2014, complete in every open dataset) and 5.2.1 (2024, partially covered — no open source
has structured magic items *and* spells for 5.2.1). Supporting both doubles compendium size
and branches the rules engine (weapon mastery, heroic inspiration, exhaustion rework).

## Decision
v1 bundles SRD 5.1 only. Every compendium and character record carries `edition` so a 5.2.1
pack can be added as a second edition later; the reference engine takes an `edition`
argument and implements `"2014"`.

## Consequences
Attribution is the single 5.1 sentence. Terms in the UI follow the 2014 text ("race",
"Hit Dice", "inspiration"). A 2024 pack is a roadmap item, not a schema migration.
