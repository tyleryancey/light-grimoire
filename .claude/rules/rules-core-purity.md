---
paths:
  - "tool/src/main/kotlin/**/rules/**/*.kt"
  - "tool/src/test/kotlin/**/rules/**/*.kt"
---

# rules/ is the oracle's mirror

- Imports allowed: `kotlin.*`, `kotlinx.serialization.*`, `java.util.UUID`, `kotlin.test.*`
  (tests). Nothing from `android`, `androidx`, Compose, coroutines, IO.
- Every public function has a fixture case in `fixtures/*.json` and a replaying test; new
  behaviour is added to `pipeline/reference/` FIRST, then fixtures regenerated, then Kotlin.
- Dice use `Mulberry32` (`.claude/skills/dice-notation`); no platform RNG.
- Floor division for modifiers: `Math.floorDiv(score - 10, 2)`.
- `kotlin.test` asserts put the message LAST: `assertEquals(expected, actual, "case …")`.
