"""Dice notation + deterministic RNG.

Grammar (a deliberate subset of Roll20 notation — everything a 5E sheet needs, nothing
that invites a keyboard):

    expr   := term (("+" | "-") term)*
    term   := dice | INT
    dice   := [INT] "d" INT [("kh" | "kl") INT]      e.g. 2d20kh1, d6, 8d6, 1d8+3

- Whitespace is ignored. Case-insensitive.
- Sides ∈ {2,3,4,6,8,10,12,20,100}; 1 ≤ count ≤ 100; keep ≤ count.
- `adv`/`dis` are NOT tokens: the caller rewrites the d20 term to 2d20kh1 / 2d20kl1
  (see `with_advantage`). This keeps the grammar tiny and the display honest.

RNG: mulberry32 (32-bit), the same generator Tyler's Sudoku tool ported bit-for-bit, so
Kotlin can reproduce every fixture with plain `Int` arithmetic:

    a = (a + 0x6D2B79F5) | 0
    t = imul(a ^ (a >>> 15), 1 | a)
    t = (t + imul(t ^ (t >>> 7), 61 | t)) ^ t
    return (t ^ (t >>> 14)) >>> 0            # uint32
    die = floor(uint32 / 2^32 * sides) + 1

Kotlin translation notes: `>>>` is `ushr`; `imul` is plain `Int` multiplication (wraps);
`>>> 0` means "treat as unsigned" → `toUInt().toLong()` or `toLong() and 0xFFFFFFFFL`.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

MASK32 = 0xFFFFFFFF
VALID_SIDES = (2, 3, 4, 6, 8, 10, 12, 20, 100)
MAX_DICE = 100


class Mulberry32:
    """Deterministic 32-bit PRNG. `seed` is any int; only the low 32 bits matter."""

    def __init__(self, seed: int):
        self.state = seed & MASK32

    def next_u32(self) -> int:
        self.state = (self.state + 0x6D2B79F5) & MASK32
        a = self.state
        t = ((a ^ (a >> 15)) * (1 | a)) & MASK32
        t = ((t + ((t ^ (t >> 7)) * (61 | t))) ^ t) & MASK32
        return (t ^ (t >> 14)) & MASK32

    def die(self, sides: int) -> int:
        return (self.next_u32() * sides >> 32) + 1  # == floor(u32/2^32 * sides) + 1, exactly, no floats


@dataclass(frozen=True)
class DiceTerm:
    count: int
    sides: int
    keep: int | None = None  # number kept
    keep_high: bool = True
    sign: int = 1

    def text(self) -> str:
        s = f"{self.count}d{self.sides}"
        if self.keep is not None:
            s += ("kh" if self.keep_high else "kl") + str(self.keep)
        return s


@dataclass(frozen=True)
class ConstTerm:
    value: int
    sign: int = 1


@dataclass
class Roll:
    expression: str
    terms: list  # DiceTerm | ConstTerm
    rolls: list[list[int]] = field(default_factory=list)  # per dice term, raw dice in roll order
    kept: list[list[int]] = field(default_factory=list)   # per dice term, kept dice
    total: int = 0

    @property
    def is_d20(self) -> bool:
        return any(isinstance(t, DiceTerm) and t.sides == 20 for t in self.terms)

    @property
    def natural(self) -> int | None:
        """The kept d20 value for a single-d20 check (for crit / fumble display)."""
        for t, k in zip((t for t in self.terms if isinstance(t, DiceTerm)), self.kept):
            if t.sides == 20 and len(k) == 1:
                return k[0]
        return None


class DiceError(ValueError):
    pass


_TERM_RE = re.compile(r"^(?:(\d+)?d(\d+)(?:(kh|kl)(\d+))?|(\d+))$", re.IGNORECASE)


def parse(expression: str) -> list:
    text = expression.replace(" ", "").lower()
    if not text:
        raise DiceError("empty expression")
    # Split into signed chunks: "-1d4+3" → ["-1d4", "+3"]. Rejects "++", trailing "+", etc.
    tokens = re.findall(r"[+-]?[^+-]+", text)
    if "".join(tokens) != text or not tokens:
        raise DiceError(f"malformed expression {expression!r}")
    chunks: list[tuple[int, str]] = []
    for tok in tokens:
        sgn = -1 if tok.startswith("-") else 1
        chunks.append((sgn, tok.lstrip("+-")))
    terms: list = []
    for sgn, chunk in chunks:
        m = _TERM_RE.match(chunk)
        if not m:
            raise DiceError(f"bad term {chunk!r} in {expression!r}")
        if m.group(5) is not None:
            terms.append(ConstTerm(int(m.group(5)), sgn))
            continue
        count = int(m.group(1)) if m.group(1) else 1
        sides = int(m.group(2))
        if sides not in VALID_SIDES:
            raise DiceError(f"unsupported die d{sides}")
        if not 1 <= count <= MAX_DICE:
            raise DiceError(f"dice count {count} out of range 1..{MAX_DICE}")
        keep = None
        keep_high = True
        if m.group(3):
            keep = int(m.group(4))
            keep_high = m.group(3) == "kh"
            if not 1 <= keep <= count:
                raise DiceError(f"keep {keep} out of range 1..{count}")
        terms.append(DiceTerm(count, sides, keep, keep_high, sgn))
    return terms


def with_advantage(expression: str, mode: str) -> str:
    """Rewrite the first plain 1d20 term as 2d20kh1 (adv) or 2d20kl1 (dis)."""
    if mode not in ("adv", "dis"):
        return expression
    terms = parse(expression)
    out = []
    done = False
    for t in terms:
        if isinstance(t, DiceTerm) and t.sides == 20 and t.count == 1 and t.keep is None and not done:
            t = DiceTerm(2, 20, 1, mode == "adv", t.sign)
            done = True
        out.append(t)
    if not done:
        raise DiceError("no 1d20 term to apply advantage to")
    return render(out)


def with_critical(expression: str) -> str:
    """Critical hit damage: roll all of the attack's damage DICE twice, add modifiers once
    (SRD 5.1 "Critical Hits"). Implemented as a rewrite doubling every dice term's count:
    "1d8+3" → "2d8+3", "2d6+1d4+2" → "4d6+2d4+2". Keep terms are doubled in both count and
    keep so `kh`/`kl` semantics survive ("2d20kh1" → "4d20kh2")."""
    out = []
    for t in parse(expression):
        if isinstance(t, DiceTerm):
            if t.count * 2 > MAX_DICE:
                raise DiceError("critical rewrite exceeds the dice cap")
            t = DiceTerm(t.count * 2, t.sides, None if t.keep is None else t.keep * 2, t.keep_high, t.sign)
        out.append(t)
    return render(out)


def roll_many(expressions: list[str], seed: int) -> list["Roll"]:
    """Roll several expressions from ONE Mulberry32(seed) stream, in order — the attack
    (to-hit, then damage) pair on the Turn screen uses this so a single seed reproduces
    the whole tap in tests."""
    rng = Mulberry32(seed)
    return [roll(e, seed, rng) for e in expressions]


def render(terms: list) -> str:
    parts = []
    for i, t in enumerate(terms):
        body = t.text() if isinstance(t, DiceTerm) else str(t.value)
        if i == 0:
            parts.append(("-" if t.sign < 0 else "") + body)
        else:
            parts.append(("-" if t.sign < 0 else "+") + body)
    return "".join(parts)


def roll(expression: str, seed: int, rng: Mulberry32 | None = None) -> Roll:
    """Roll `expression` with a fresh Mulberry32(seed) (or a caller-supplied stream)."""
    terms = parse(expression)
    r = rng or Mulberry32(seed)
    result = Roll(expression=render(terms), terms=terms)
    total = 0
    for t in terms:
        if isinstance(t, ConstTerm):
            total += t.sign * t.value
            continue
        dice = [r.die(t.sides) for _ in range(t.count)]
        if t.keep is None:
            kept = list(dice)
        else:
            ordered = sorted(dice, reverse=t.keep_high)
            kept = ordered[: t.keep]
        result.rolls.append(dice)
        result.kept.append(kept)
        total += t.sign * sum(kept)
    result.total = total
    return result


def bounds(expression: str) -> tuple[int, int]:
    lo = hi = 0
    for t in parse(expression):
        if isinstance(t, ConstTerm):
            lo += t.sign * t.value
            hi += t.sign * t.value
        else:
            n = t.keep if t.keep is not None else t.count
            a, b = n * 1, n * t.sides
            if t.sign < 0:
                a, b = -b, -a
            lo += a
            hi += b
    return lo, hi


def average(expression: str) -> float:
    """Expected value, ignoring keep-high/low (used for 'take the average' HP)."""
    total = 0.0
    for t in parse(expression):
        if isinstance(t, ConstTerm):
            total += t.sign * t.value
        else:
            n = t.keep if t.keep is not None else t.count
            total += t.sign * n * (t.sides + 1) / 2
    return total
