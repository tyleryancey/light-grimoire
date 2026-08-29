# ADR-0005 — Name "Grimoire", id `dev.tyler.grimoire`

**Status:** accepted 28 Aug 2026 (Tyler) — `id` is permanent from the first publish.

## Context
WotC's SRD legal page permits only "compatible with fifth edition" / "5E compatible" as
extra branding and asks for no other Wizards attribution; "D&D", "Dungeons & Dragons",
"DM" and book names are trademarks/trade dress. The community already has name collisions
(two Passes, three Bibles, a second "Ledger"), so the label should be distinctive. The
working name during design was *Familiar*.

## Decision
Label **Grimoire** — a spellbook: the thing a player opens at the table to find a rule, a
spell, a note. Generic word, no trademark, reads well in a monochrome list, and no
collision among the 35 `lightphone`-topic repos on GitHub (checked 28 Aug 2026).
`id = dev.tyler.grimoire` matches `dev.tyler.sudoku`; repo `tyleryancey/light-grimoire`.
Description: "A 5E-compatible player companion: track hit points, slots and conditions,
roll what your turn needs, look up the open rules, keep a session journal. Offline."

Known trade-off: "Grimoire" reads reference-first; the README's opening paragraph carries
the tracker/dice/journal half of the pitch.

## Alternatives
Familiar (working name; a wizard's familiar as the archetypal companion), Satchel
(adventurer's pack — slightly generic), Tavern (social connotation), Hearth (rest
connotation). All free of trademark issues.
