# ADR-0008 — Journal: six entities, timestamped one-line entries, pick-don't-type links

**Status:** accepted

## Context
Player-side journaling reduces to who / where / quest / loot / session in every tool and
guide surveyed; players capture fragments live and write prose on a laptop within 1–2
days. Campaign Logger's `@Name` sigils are the fast-capture bar, but typing sigils and
names accurately is exactly what the LP3 keyboard makes slow.

## Decision
Sessions are the spine; an entry is `{at, kind ∈ met|went|quest|got|learned|rumor|note,
links, text ≤ 120}`. The capture flow picks the kind and the linked thing from rosters
(recency-first) and asks for the keyboard only for a *new* name. Backlinks are derived.
Export is JSON (schema) and Obsidian-shaped Markdown for the laptop pass. No calendar
model, no maps, no permissions, no long descriptions on device.

## Consequences
Capture in ≤ 10 s with the wheel; the journal stays finite (one campaign, bounded rosters);
the phone never becomes the place where prose is written.
