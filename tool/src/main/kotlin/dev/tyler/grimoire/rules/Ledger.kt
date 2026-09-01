package dev.tyler.grimoire.rules

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.math.max
import kotlin.math.min

/**
 * The in-play mutation vocabulary — everything with rules semantics goes through [Ledger] and has a
 * scenario in fixtures/events.json. Trivial setters (toggling a condition, inspiration, currency, what is
 * equipped) are applied by the view model directly and are not events (docs/DATA-MODEL.md).
 *
 * Temporary hit points arrive two ways and the pair must stay distinct. [Temp] is a **grant** — a spell or
 * feature conferring temp HP, which keeps the higher of the old and new numbers and never stacks (SRD 5.1).
 * [TempDelta] is a **correction** — S3's HP pad raising or lowering the number the player entered, signed and
 * clamped at 0. A grant can only ever raise the number; only a correction can take a mis-tap back down.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class Event {
    @Serializable
    @SerialName("damage")
    data class Damage(val amount: Int, val critical: Boolean = false) : Event()

    @Serializable
    @SerialName("heal")
    data class Heal(val amount: Int) : Event()

    /** A grant of temporary hit points from a spell or feature — the higher number wins, they never stack. */
    @Serializable
    @SerialName("temp")
    data class Temp(val amount: Int) : Event()

    /** A correction to the temp HP already on the sheet — signed, clamped at 0, and it does stack. */
    @Serializable
    @SerialName("tempDelta")
    data class TempDelta(val delta: Int) : Event()

    /** A death saving throw with the natural d20 the player rolled. */
    @Serializable
    @SerialName("deathSave")
    data class DeathSave(val d20: Int) : Event()

    /** A hit die spent during a short rest, with the value rolled (or the average). */
    @Serializable
    @SerialName("spendHitDie")
    data class SpendHitDie(val die: Int, val roll: Int) : Event()

    @Serializable
    @SerialName("shortRest")
    data object ShortRest : Event()

    @Serializable
    @SerialName("longRest")
    data object LongRest : Event()

    @Serializable
    @SerialName("dawn")
    data object Dawn : Event()

    @Serializable
    @SerialName("spendSlot")
    data class SpendSlot(val level: Int) : Event()

    @Serializable
    @SerialName("spendPactSlot")
    data object SpendPactSlot : Event()

    @Serializable
    @SerialName("counter")
    data class CounterDelta(val id: String, val delta: Int) : Event()
}

/**
 * The hit-point ledger, rests, death saves, slots and counters — pure functions from a [Character] to a
 * new one, a port of pipeline/reference/rules.py. Invalid input throws [RulesException] with the oracle's
 * message (the UI should have disabled the control); state bounds clamp silently.
 */
object Ledger {
    private val NO_SAVES = DeathSaves()
    private val NO_SLOTS_USED = List(9) { 0 }

    /**
     * Temporary hit points absorb first — also while at 0 HP, where damage that gets through is a
     * death-save failure (two on a critical hit). Dropping to 0 with an overflow of at least the maximum,
     * or taking that much while already at 0, is instant death.
     */
    fun damage(c: Character, amount: Int, critical: Boolean = false): Character {
        if (amount < 0) throw RulesException("damage must be ≥ 0")
        val hp = c.hp
        val current = hp.max - hp.damage
        val absorbed = min(hp.temp, amount)
        val remaining = amount - absorbed
        val afterTemp = c.copy(hp = hp.copy(temp = hp.temp - absorbed))
        if (current <= 0) {
            if (remaining <= 0) return afterTemp
            val saves = c.deathSaves
            val failures = min(3, saves.failures + if (critical) 2 else 1)
            val dead = saves.dead || remaining >= hp.max || failures >= 3
            return afterTemp.copy(deathSaves = saves.copy(failures = failures, stable = false, dead = dead))
        }
        val newCurrent = current - remaining
        if (newCurrent <= 0) {
            val overflow = -newCurrent
            return afterTemp.copy(hp = afterTemp.hp.copy(damage = hp.max), deathSaves = NO_SAVES.copy(dead = overflow >= hp.max))
        }
        return afterTemp.copy(hp = afterTemp.hp.copy(damage = hp.damage + remaining))
    }

    /** Healing cannot exceed the maximum, regaining any HP ends death saves, and it does not raise the dead. */
    fun heal(c: Character, amount: Int): Character {
        if (amount < 0) throw RulesException("healing must be ≥ 0")
        if (c.deathSaves.dead) return c
        val damage = max(0, c.hp.damage - amount)
        val healed = c.copy(hp = c.hp.copy(damage = damage))
        return if (c.hp.max - damage > 0) healed.copy(deathSaves = NO_SAVES) else healed
    }

    /** Temporary hit points never stack: the higher of the current and the new value is kept. */
    fun temp(c: Character, amount: Int): Character = c.copy(hp = c.hp.copy(temp = max(c.hp.temp, amount)))

    /**
     * Correct the temporary hit points directly: a signed delta clamped at 0, touching neither HP nor death
     * saves. This is the HP pad editing a number the player entered, not a spell or feature granting temp HP
     * — that is [temp] and its don't-stack rule. No upper clamp: the SRD puts no cap on temporary hit points.
     *
     * Deliberately the one HP-adjacent function with **no dead guard**, where [heal], [spendHitDie] and
     * [longRest] all return early: a correction is bookkeeping about what the sheet says, not a rules effect
     * on the character, and it can revive nobody because it never touches HP or the death saves. Adding a
     * guard here for symmetry with its neighbours fails the events.json scenario "temp hp correction applies
     * even to the dead" — in both languages, which is the point.
     */
    fun tempDelta(c: Character, delta: Int): Character = c.copy(hp = c.hp.copy(temp = max(0, c.hp.temp + delta)))

    /**
     * 10 or more is a success, less a failure; a natural 1 is two failures, a natural 20 regains 1 HP.
     * Three successes: stable (still at 0 HP, counters reset). Three failures: dead.
     */
    fun deathSave(c: Character, d20: Int): Character {
        val saves = c.deathSaves
        if (saves.dead || saves.stable) return c
        var out = c
        var next = when {
            d20 == 20 -> {
                out = c.copy(hp = c.hp.copy(damage = c.hp.max - 1))
                NO_SAVES
            }
            d20 == 1 -> saves.copy(failures = min(3, saves.failures + 2))
            d20 >= 10 -> saves.copy(successes = saves.successes + 1)
            else -> saves.copy(failures = saves.failures + 1)
        }
        next = when {
            next.failures >= 3 -> next.copy(dead = true)
            next.successes >= 3 -> next.copy(stable = true, successes = 0, failures = 0)
            else -> next
        }
        return out.copy(deathSaves = next)
    }

    /**
     * Short rest: one hit die at a time — regain the roll plus the CON modifier, never less than 0. A dead
     * character cannot benefit from a short rest, so the state stands and the die goes unspent, matching
     * [heal] and [longRest] rather than raising. The guard comes before the pool is looked up: spending a die
     * the dead character does not have is a no-op too, not the "no dice left" error a living one would get.
     */
    fun spendHitDie(c: Character, die: Int, roll: Int): Character {
        if (c.deathSaves.dead) return c
        val index = c.hitDice.indexOfFirst { it.die == die }
        val pool = c.hitDice.getOrNull(index)
        if (pool == null || pool.used >= pool.total) throw RulesException("no d$die hit dice left")
        val pools = c.hitDice.toMutableList().also { it[index] = pool.copy(used = pool.used + 1) }
        val regained = max(0, roll + Derive.abilityMod(c.abilities.con))
        val damage = max(0, c.hp.damage - regained)
        val out = c.copy(hitDice = pools, hp = c.hp.copy(damage = damage))
        return if (c.hp.max - damage > 0) out.copy(deathSaves = NO_SAVES) else out
    }

    /** Short rest: reset the short-rest counters and Pact Magic. Hit dice are spent one by one beforehand. */
    fun shortRest(c: Character): Character {
        val rested = resetCounters(c, setOf(ResetTrigger.SHORT))
        val sc = c.spellcasting
        return if (sc?.pactUsed != null) rested.copy(spellcasting = sc.copy(pactUsed = 0)) else rested
    }

    /**
     * Long rest (2014): full HP, temporary hit points gone, death saves cleared, hit dice back up to half
     * the total (at least one) largest dice first, every slot, short and long counters, exhaustion down
     * one. Conditions are never cleared automatically; the dead do not benefit.
     */
    fun longRest(c: Character): Character {
        if (c.deathSaves.dead) return c
        val total = c.hitDice.sumOf { it.total }
        var regain = if (total > 0) max(1, total / 2) else 0
        val pools = c.hitDice.toMutableList()
        for (index in c.hitDice.indices.sortedByDescending { c.hitDice[it].die }) {
            if (regain == 0) break
            val pool = pools[index]
            val give = min(regain, pool.used)
            pools[index] = pool.copy(used = pool.used - give)
            regain -= give
        }
        var out = c.copy(hp = c.hp.copy(damage = 0, temp = 0), deathSaves = NO_SAVES, hitDice = pools)
        out = resetCounters(out, setOf(ResetTrigger.SHORT, ResetTrigger.LONG))
        val sc = out.spellcasting
        if (sc != null) {
            out = out.copy(spellcasting = sc.copy(slotsUsed = NO_SLOTS_USED, pactUsed = if (sc.pactUsed != null) 0 else null))
        }
        return out.copy(exhaustion = max(0, c.exhaustion - 1))
    }

    /** Dawn resets only the counters that say so — offered alongside the long rest, never implied by it. */
    fun dawn(c: Character): Character = resetCounters(c, setOf(ResetTrigger.DAWN))

    /** Spend one regular slot of [level]; the maxima come from the classes, so nothing else is checked. */
    fun spendSlot(c: Character, level: Int): Character {
        if (level !in 1..9) throw RulesException("spell level must be 1..9")
        val maxima = Tables.spellSlots(c.classes).slots
        val sc = c.spellcasting ?: Spellcasting()
        val used = sc.slotsUsed
        if (used[level - 1] >= maxima[level - 1]) throw RulesException("no level-$level slots left")
        val spent = used.toMutableList().also { it[level - 1] = it[level - 1] + 1 }
        return c.copy(spellcasting = sc.copy(slotsUsed = spent))
    }

    fun spendPactSlot(c: Character): Character {
        val pact = Tables.spellSlots(c.classes).pact ?: throw RulesException("no pact magic")
        val sc = c.spellcasting ?: Spellcasting()
        val used = sc.pactUsed ?: 0
        if (used >= pact.count) throw RulesException("no pact slots left")
        return c.copy(spellcasting = sc.copy(pactUsed = used + 1))
    }

    /** Move a counter by [delta], clamped to `0..max`. */
    fun counter(c: Character, id: String, delta: Int): Character {
        val index = c.counters.indexOfFirst { it.id == id }
        if (index < 0) throw RulesException("unknown counter $id")
        val counter = c.counters[index]
        val value = max(0, min(counter.max, counter.value + delta))
        return c.copy(counters = c.counters.toMutableList().also { it[index] = counter.copy(value = value) })
    }

    fun apply(c: Character, event: Event): Character = when (event) {
        is Event.Damage -> damage(c, event.amount, event.critical)
        is Event.Heal -> heal(c, event.amount)
        is Event.Temp -> temp(c, event.amount)
        is Event.TempDelta -> tempDelta(c, event.delta)
        is Event.DeathSave -> deathSave(c, event.d20)
        is Event.SpendHitDie -> spendHitDie(c, event.die, event.roll)
        is Event.ShortRest -> shortRest(c)
        is Event.LongRest -> longRest(c)
        is Event.Dawn -> dawn(c)
        is Event.SpendSlot -> spendSlot(c, event.level)
        is Event.SpendPactSlot -> spendPactSlot(c)
        is Event.CounterDelta -> counter(c, event.id, event.delta)
    }

    fun run(c: Character, events: List<Event>): Character = events.fold(c, ::apply)

    private fun resetCounters(c: Character, triggers: Set<ResetTrigger>): Character =
        c.copy(counters = c.counters.map { if (it.reset in triggers) it.copy(value = it.max) else it })
}
