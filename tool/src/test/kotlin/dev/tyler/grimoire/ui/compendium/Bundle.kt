package dev.tyler.grimoire.ui.compendium

import dev.tyler.grimoire.Fixtures
import dev.tyler.grimoire.compendium.CompendiumReader
import dev.tyler.grimoire.compendium.FakeCompendiumDao
import dev.tyler.grimoire.compendium.ImportContext
import dev.tyler.grimoire.compendium.JsonArraySplit
import dev.tyler.grimoire.compendium.Kind
import dev.tyler.grimoire.compendium.Rows

/**
 * The whole bundle as the two Room rows the importer would write, built once for the compendium screens'
 * tests: every count and every order below is the sha256-pinned assets rather than a sample, and
 * [FakeCompendiumDao] answers the same queries the device's SQL does.
 */
internal object Bundle {
    val built: List<Rows.Built> by lazy {
        var ctx = ImportContext.EMPTY
        Kind.entries.flatMap { kind ->
            val text = Fixtures.compendium(kind.file)
            val slices = JsonArraySplit.elements(text)
            val records = kind.decodeAll(text)
            if (kind == Kind.RULES) ctx = ImportContext.from(records)
            records.indices.map { Rows.of(kind, it, slices[it], records[it], ctx) }
        }
    }

    fun dao(): FakeCompendiumDao = FakeCompendiumDao(built)

    fun reader(dao: FakeCompendiumDao = dao()): CompendiumReader = CompendiumReader(dao)
}
