---
name: playtest
description: "Walk a scenario through the UI spec and the reference engine as a player would, counting taps and keyboard trips and reporting rule bugs, spec gaps, friction, and ethos flags — \"/playtest level-5 cleric: three rounds, drop to 0, get healed, short rest\""
disable-model-invocation: true
argument-hint: "<scenario>"
context: fork
agent: playtester
---

Play this scenario end to end and report per the `playtester` agent's format:

$ARGUMENTS

Start from the closest character in `fixtures/characters/` (or describe the one you
construct). Use the compendium MCP tools for every state change so the expected numbers
are the oracle's. Finish with the tap/keyboard totals versus `docs/PRD.md` §5 and the
list of fixture scenarios worth adding to `pipeline/reference/fixtures.py`.
