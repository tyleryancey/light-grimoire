package dev.tyler.grimoire.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.tyler.grimoire.compendium.ImportMarker
import kotlinx.coroutines.flow.first

/**
 * The tool's preferences over the SDK's `SealedLightContext.dataStore` (docs/ARCHITECTURE.md `data/`).
 * Settings are near-empty by design; today the only key is the compendium stamp (plan D5). DataStore does
 * not run on the JVM, so this class stays a thin, obviously-correct wrapper and the device checks cover it.
 */
class Prefs(private val store: DataStore<Preferences>) {
    companion object {
        /** `"$SCHEMA_VERSION.$FORMAT:$bundleSha256"` of the last complete compendium import. */
        val COMPENDIUM_STAMP: Preferences.Key<String> = stringPreferencesKey("compendium.stamp")
    }

    suspend fun compendiumStamp(): String? = store.data.first()[COMPENDIUM_STAMP]

    suspend fun setCompendiumStamp(stamp: String) {
        store.edit { it[COMPENDIUM_STAMP] = stamp }
    }
}

/** The importer's marker seam over [Prefs] — what `CompendiumStore` hands `AssetImporter` on device. */
class PrefsMarker(private val prefs: Prefs) : ImportMarker {
    override suspend fun read(): String? = prefs.compendiumStamp()

    override suspend fun write(stamp: String) = prefs.setCompendiumStamp(stamp)
}
