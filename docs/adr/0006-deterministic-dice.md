# ADR-0006 — Dice use mulberry32 with a seed, so results are reproducible in tests

**Status:** accepted

## Context
A dice roller is trivially correct until it isn't (keep-highest bugs, off-by-one on
`floor(u/2^32 * sides) + 1`). Tyler's Sudoku tool already ported mulberry32 bit-for-bit
from JavaScript; the Python reference emits seeded fixtures.

## Decision
`rules/Dice.kt` implements mulberry32 exactly as `pipeline/reference/dice.py` (Int
arithmetic, `ushr`, unsigned multiply via `Long`), seeded from `System.nanoTime()` in
play and from fixture seeds in tests. The grammar is the Roll20 subset
(`NdS`, `kh/kl`, `±k`), with adv/dis as a rewrite to `2d20kh1/kl1` so the displayed
expression is honest.

## Consequences
`fixtures/rng.json` and `dice.json` pin the generator and the evaluation order (dice are
consumed left to right, term by term). No `java.util.Random`, no `kotlin.random` in `rules/`.
