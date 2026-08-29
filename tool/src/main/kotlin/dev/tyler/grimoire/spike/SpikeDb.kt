package dev.tyler.grimoire.spike

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "compendium_rows", primaryKeys = ["kind", "key"])
data class CompendiumRow(val kind: String, val key: String, val name: String, val json: String)

@Fts4(contentEntity = CompendiumRow::class)
@Entity(tableName = "compendium_fts")
data class CompendiumFts(val name: String, val json: String)

@Dao
interface SpikeDao {
    @Insert suspend fun insertAll(rows: List<CompendiumRow>)
    @Query("SELECT COUNT(*) FROM compendium_rows") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM compendium_fts WHERE compendium_fts MATCH :query") suspend fun ftsCount(query: String): Int
}

@Database(entities = [CompendiumRow::class, CompendiumFts::class], version = 1, exportSchema = false)
abstract class SpikeDb : RoomDatabase() {
    abstract fun dao(): SpikeDao
}
