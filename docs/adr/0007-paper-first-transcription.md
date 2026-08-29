# ADR-0007 — "From paper" transcription is the creation path; no full builder

**Status:** accepted · **Decided by:** Tyler ("paper sheets")

## Context
Tyler's table keeps paper sheets. Indie sheet apps that demand data entry are punished in
reviews; D&D Beyond's own answer for a constrained surface is a five-picker Quickbuilder.
The LP3's text entry is a full-screen editor. SRD 5.1 has only one background and one feat,
so a "builder" would be incomplete for most real characters anyway.

## Decision
Creation = transcribe the numbers that matter from the sheet (scores, saves/skills, AC, HP
max, speed, attacks, prepared spells, level/class/subclass/race) with wheel-driven pickers
and at most two keyboard trips (character name, custom names). Class-table counters and hit
dice are seeded automatically. A Quick build from SRD picks is a later addition.

## Consequences
A character is useful when half-entered. Non-SRD subclasses/spells/items are names +
numbers, never typed rules text (ADR-0003). Level-up is "edit level + HP max" in v1.
