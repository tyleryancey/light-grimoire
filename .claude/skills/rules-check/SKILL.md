---
name: rules-check
description: Settle a 2014-rules question against the bundled SRD 5.1 text and the reference engine, with citations — "/rules-check does temp HP absorb damage before death saves?"
disable-model-invocation: true
argument-hint: "<rules question>"
allowed-tools: mcp__compendium__search, mcp__compendium__get, mcp__compendium__class_progression, mcp__compendium__spell_slots, mcp__compendium__derive, mcp__compendium__apply_events, mcp__compendium__roll, Read, Grep
---

Question: $ARGUMENTS

Use the `rules-lawyer` agent's method without delegating (this is a short, interactive
ruling):

1. Search the compendium (`search`, then `get`) for the governing rule text; quote the
   sentence(s) with `kind/key`.
2. If the question is about a number or a state change, compute it with `derive` /
   `apply_events` / `spell_slots` / `roll` on a fixture character (`fixtures/characters/`)
   so the answer is the oracle's, not a guess.
3. State the ruling in two lines, then: which function in `pipeline/reference/rules.py`
   implements it, which fixture case covers it, and whether Kotlin `rules/` already
   replays that case.
4. If the SRD is silent (PHB-only content), say so and describe how the model represents
   it (`.claude/skills/character-model`).
