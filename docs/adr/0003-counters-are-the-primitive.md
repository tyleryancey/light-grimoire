# ADR-0003 — One primitive for limited-use resources: the counter

**Status:** accepted

## Context
Fight Club 5e (`<counter reset=L|S>`), Foundry (`uses{value,max,recovery}`), D&D Beyond
("Limited Use") and Avrae (`!cc`) all model class features, item charges and homebrew
resources as a value/max pair with a reset trigger. Spell slots are the same shape per
level; hit dice are die-typed pools.

## Decision
`Character.counters[] = {id, name, value, max, reset ∈ short|long|dawn|none, source,
featureKey?, showOnTurn}`. Spell slots are stored as `slotsUsed[9]` + `pactUsed` with
maxima derived from class tables (so a level change never desynchronises them). Hit dice
keep their own `{die, total, used}` pools because rests treat them by die size.

## Consequences
Non-SRD features are fully trackable without their text (name + max + reset); the Rest
screen is a fold over `reset`; the Turn screen can show any counter flagged `showOnTurn`.
