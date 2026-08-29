# Research index (29 Aug 2026)

Five reports, ~30,000 words, every non-obvious claim sourced. Read the executive summary of
each before touching the area it covers; cite them in ADRs rather than re-deriving.

| # | Report | Read it when |
|---|---|---|
| 01 | [Player-side apps survey](research/01-player-apps.md) — D&D Beyond, Roll20, Foundry, FG, Fight Club 5e, Dicecloud, Shard, Demiplane, Alchemy, DM's Vault, Aurora, Avrae, Beyond20, Pathbuilder, paper sheets, MPMB, dice apps; feature matrix; in-play frequency ranking; constrained-screen prior art; cut/keep list | designing any sheet or tracker screen; arguing scope |
| 02 | [Campaign documentation tools](research/02-campaign-tools.md) — Obsidian TTRPG, Kanka, World Anvil, LegendKeeper, Scabard, Campaign Logger, Notion, paper journals, VTT journals, AI recap tools, calendars; the player's minimum entity model; fast capture; export shape | designing the journal or its export |
| 03 | [Corpus & licensing](research/03-corpus-licensing.md) — SRD 5.1/5.2.1 licence text and counts, CC-BY obligations, trademark posture, Open5e/5e-bits/Foundry/other datasets with measured sizes, why 5e.tools is forbidden, third-party CC/ORC content, homebrew formats, pipeline design, ready-to-paste attribution | adding any content source; writing About/README legal text |
| 04 | [Light SDK state, verified against source](research/04-light-sdk-state.md) — commit `3df3c24`, deltas vs July, complete `sdk:ui` inventory, client/storage facts, examples map, hardware keys, Tool Library status with quoted criteria, ecosystem collisions (none), LP3 hardware facts, open questions | asserting anything about the SDK; before M0 |
| 05 | [Claude Code workspace formats](research/05-claude-code-formats.md) — CLAUDE.md, agents, skills, hooks, permissions, MCP, plugins as documented Aug 2026 | editing `.claude/` |

Decisions taken from them: `adr/`. Summary of what they changed: `PRD.md` §2.
