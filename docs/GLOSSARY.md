# Glossary

| Term | Meaning here |
|---|---|
| **5E compatible** | One of the two permitted compatibility phrases for SRD-based works ("compatible with fifth edition" / "5E compatible", WotC SRD legal page). We never say "D&D". |
| **SRD 5.1** | System Reference Document 5.1 — the 2014 rules subset WotC licenses under CC-BY-4.0. Our only bundled content. |
| **Compendium** | The bundled SRD data (22 kinds) imported into Room; also the reader screens. |
| **Counter** | `{value, max, reset}` — the one primitive for limited-use resources (ADR-0003). |
| **Ledger** | The pure functions that apply in-play events (damage, heal, rest…) to a character. |
| **Derived** | Sheet numbers computed from stored state (mods, saves, AC, current HP, DC, slot maxima). Never stored. |
| **Oracle / fixtures** | `pipeline/reference/` (Python) is the oracle; `fixtures/*.json` are its golden outputs; Kotlin replays them. |
| **Turn screen** | The combat-mode list: attacks, spells with slot pips, counters — one tap rolls. |
| **Pip** | A filled/hollow glyph representing one slot or use. |
| **Roster** | A recency-sorted list of journal entities (People/Places/Quests/Loot) used to pick links. |
| **Session / entry** | Journal spine and its timestamped one-line items (kinds: met, went, quest, got, learned, rumor, note). |
| **Wheel** | The LP3's clickable side scroll wheel; key codes 317/318/319 in the SDK. |
| **Grid unit** | 1/27 of the screen width (`LightGrid`); ≈ 40 px on the LP3. |
| **Vetting one-pager** | `docs/VETTING-DEFENSE.md` — the Tool Library defense kept current with the design. |
| **Plan of record** | `CLAUDE.md` — milestones with checkboxes, verified facts, decisions, open questions. |
| **light-workspace** | Tyler's shared repo: reusable CI, templates, cross-tool skills (`new-light-tool`, `run-light-tool`, `release-tool`, `sync-resolve`). |
