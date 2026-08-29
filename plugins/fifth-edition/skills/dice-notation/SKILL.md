---
name: dice-notation
description: Grimoire's dice grammar (Roll20 subset — NdS, kh/kl, ±k, adv/dis rewrite), the mulberry32 RNG contract shared bit-for-bit between the Python oracle and Kotlin, evaluation order, display rules for results (naturals, crits), and the fixtures that pin them. Load when touching rules/Dice.kt, the Turn/Checks/Dice screens, or dice fixtures.
user-invocable: false
---

# Dice

Reference: `pipeline/reference/dice.py`; fixtures `fixtures/rng.json`, `fixtures/dice.json`.

## Grammar

`expr := term (('+'|'-') term)*` · `term := [N]dS[(kh|kl)K] | INT` · S ∈ {2,3,4,6,8,10,12,20,100}
· 1 ≤ N ≤ 100 · 1 ≤ K ≤ N. Whitespace ignored, case-insensitive. Rejects `++1`, `1d20+`,
`1d7`, `0d6`, `101d6`, `2d20kh3`. Advantage/disadvantage are a **rewrite**, not tokens:
`withAdvantage("1d20+5", ADV) == "2d20kh1+5"` (first plain 1d20 term only; error if none).
Rendered expression is the normalized form (`d6` → `1d6`).

## RNG (mulberry32)

```
state = (state + 0x6D2B79F5) and 0xFFFFFFFF
t = (state xor (state ushr 15)) * (1 or state)          // 32-bit wrap
t = ((t + ((t xor (t ushr 7)) * (61 or t))) xor t)
u32 = (t xor (t ushr 14))                                 // unsigned 32-bit
die = ((u32.toLong() and 0xFFFFFFFFL) * sides ushr 32).toInt() + 1
```
Kotlin uses `Int` arithmetic (wraps identically to JS `imul`/`|0`), `ushr`, and one `Long`
multiply for the die. No floating point. Seeds: any `Int`; play seeds from `System.nanoTime()`.
`fixtures/rng.json` pins the first outputs for six seeds — the first Kotlin test to write.

## Evaluation order

Terms left to right; each dice term draws `N` dice in order from the shared stream;
`kh/kl` sorts a **copy** and keeps K; constants added last. `Roll{expression, seed,
rolls[][], kept[][], total, natural}` where `natural` is the kept d20 of a single-d20 roll
(for CRIT/MISS display). `bounds` and `average` ignore nothing except that `kh/kl` count K dice.

## Display

Total in `Subtitle`; breakdown `(13 + 5)` in `Detail`; advantage shows both d20s with the
kept one underlined; natural 20/1 → "CRIT"/"MISS" beneath in `Button` weight. No colour,
no animation, no sound. Dice history: last 10, view-model only.

## Attack rolls on the Turn screen

One tap rolls `1d20 + toHit` and the damage formula together from ONE stream
(`rollMany([toHit, damage], seed)` — `fixtures/dice.json` "pairs"). On a natural 20 the
damage expression is rewritten with `withCritical` — every dice term's count doubled,
constants untouched (`1d8+3` → `2d8+3`; fixture "critical") — which is the SRD's "roll all
of the attack's damage dice twice and add them together" with the modifier added once.
