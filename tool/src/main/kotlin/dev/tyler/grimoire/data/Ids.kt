package dev.tyler.grimoire.data

import java.util.UUID

/**
 * The tool's identifier source (docs/DATA-MODEL.md §5): character and journal ids are
 * `UUID.randomUUID().toString()`. The one exception that section names is a counter seeded from a class
 * table, which takes the compendium feature key (`channel-divinity`) instead, so a re-seed is idempotent.
 *
 * A one-line object rather than a bare function so that when a caller needs predictable ids there is one
 * place to take a `() -> String` from — the repository injects it that way, and nothing else in the tool
 * reaches for a random number to name something with.
 */
object Ids {
    fun new(): String = UUID.randomUUID().toString()
}
