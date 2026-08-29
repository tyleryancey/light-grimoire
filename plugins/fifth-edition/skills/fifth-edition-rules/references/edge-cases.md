# Edge cases and their SRD citations (compendium keys)

- **Floor division for negative modifiers** — SRD 5.1 "Ability Scores and Modifiers" table
  (`rule_sections/ability-scores-and-modifiers`): 8–9 → −1, 6–7 → −2. Kotlin: `Math.floorDiv(score - 10, 2)`.
- **Temporary hit points** (`rule_sections/damage-and-healing`): "they can't be added
  together… decide which one to keep"; "when you take damage, the temporary hit points are
  lost first"; "last until they're depleted or you finish a long rest"; healing does not
  restore them.
- **Dropping to 0 / instant death / death saves** (`rule_sections/damage-and-healing`):
  massive damage rule; "When you start your turn with 0 hit points, you must make a special
  saving throw"; natural 1 / 20; stable; "If you take any damage while you have 0 hit points,
  you suffer a death saving throw failure. If the damage is from a critical hit, you suffer
  two failures instead."
- **Short rest hit dice** (`rule_sections/resting`): "regains hit points equal to the total
  rolled plus the character's Constitution modifier"; long rest "regains spent Hit Dice, up
  to a number of dice equal to half of the character's total number of them (minimum of one die)";
  "must have at least 1 hit point at the start of the rest".
- **Multiclass spellcasting** (`rule_sections/multiclassing`): "half your levels (rounded
  down) in the paladin and ranger classes"; Pact Magic separate but a warlock can use pact
  slots for spells from other classes and vice versa — the tool lets the player choose the slot.
- **Jack of All Trades** (`features/jack-of-all-trades`): half proficiency, rounded down → skill level `half`.
- **Concentration DC** (`rule_sections/casting-a-spell`): "DC equals 10 or half the damage you take, whichever number is higher".
- **Exhaustion** (`conditions/exhaustion`): six levels, cumulative; long rest reduces by 1 with food and drink.
- **Attunement** (`rule_sections/attunement` if present, else `magic_items` headline text): max three attuned items.
- **Encumbrance** — deliberately not implemented (PRD non-goal).
