package dev.tyler.grimoire.compendium

import android.util.Log
import androidx.room.withTransaction
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.buildDatabase
import dev.tyler.grimoire.data.Prefs
import dev.tyler.grimoire.data.PrefsMarker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * The production [CompendiumWriter] (plan D4): the importer's one transaction is Room's `withTransaction`
 * over the one DAO, and the sink's `clear`/`insert` touch both tables. The transaction function is a
 * constructor parameter so RoomWriterTest can run the mapping over [CompendiumDao] fakes on the JVM;
 * the secondary constructor is what the device uses.
 */
class RoomWriter(
    private val dao: CompendiumDao,
    private val transaction: suspend (suspend () -> Unit) -> Unit,
) : CompendiumWriter {
    constructor(db: CompendiumDb) : this(db.dao(), { block -> db.withTransaction { block() } })

    private val sink = object : ImportSink {
        override suspend fun clear() {
            dao.clearRecords()
            dao.clearSearch()
        }

        override suspend fun insert(records: List<RecordRow>, search: List<SearchRow>) {
            dao.insertRecords(records)
            dao.insertSearch(search)
        }
    }

    override suspend fun count(): Int = dao.count()

    override suspend fun searchCount(): Int = dao.searchCount()

    override suspend fun replaceAll(block: suspend (ImportSink) -> Unit) = transaction { block(sink) }
}

/**
 * The state machine behind [CompendiumStore] (plan D7, D8), kept free of Android so ImportGateTest can drive
 * it: [ensure] starts at most one run at a time and returns whether it started one; `Checking` is set
 * synchronously, progress ticks become `Importing(done, total)`, a finished run is `Ready` (sticky) and a
 * thrown run is `Failed(reason)` — the next [ensure] retries. The run executes on [scope] under
 * `NonCancellable` (CLAUDE.md's rule for every save), so a popped screen or an `onResume` cannot leave a
 * half-written transaction behind; the importer's own rollback covers process death.
 */
class ImportGate(private val scope: CoroutineScope, private val log: (String) -> Unit) {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    companion object {
        /** The one line the store logs; the `import` form is what the LP3 measurement reads. */
        fun describe(result: ImportResult): String = when (result) {
            is ImportResult.Skipped -> "compendium ready rows=${result.rows}"
            is ImportResult.Imported ->
                "compendium import rows=${result.rows} decode=${result.decodeMs}ms insert=${result.insertMs}ms total=${result.totalMs}ms"
        }
    }

    /** @return true when a run was started; false when one is in flight or the store is already Ready */
    @Synchronized
    fun ensure(run: suspend (onProgress: (Int, Int) -> Unit) -> ImportResult): Boolean {
        when (_state.value) {
            ImportState.Checking, is ImportState.Importing, ImportState.Ready -> return false
            ImportState.Idle, is ImportState.Failed -> Unit
        }
        _state.value = ImportState.Checking
        scope.launch {
            try {
                val result = withContext(NonCancellable) {
                    run { done, total -> _state.value = ImportState.Importing(done, total) }
                }
                log(describe(result))
                _state.value = ImportState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                log("compendium import failed: $reason")
                _state.value = ImportState.Failed(reason)
            }
        }
        return true
    }

    /** The read facade's guard: throws naming the current state unless it is Ready, so no reader ever sees an empty or half-filled table. */
    fun requireReady() {
        val current = _state.value
        check(current == ImportState.Ready) { "compendium is not Ready (state=$current)" }
    }
}

/**
 * The process-wide owner of the compendium database (plan D7): one [CompendiumDb] built on first use, one
 * [ImportGate] over its own IO scope, and [state] for the Home screen. [ensureImported] takes the
 * [SealedLightContext] as a parameter and keeps nothing of it beyond the import run — the database is built
 * over the application context, and the asset reader and preference marker live only as long as the
 * coroutine that uses them. `HomeViewModel.onScreenShow` calls it on every show; the gate's state is the
 * guard, so `onResume` re-entry and screen pops cost nothing once Ready.
 */
object CompendiumStore {
    private const val TAG = "Grimoire"

    private val gate = ImportGate(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { Log.i(TAG, it) }

    /** Idle → Checking → Importing(done, total)* → Ready, or Failed(reason) until the next [ensureImported]. */
    val state: StateFlow<ImportState>
        get() = gate.state

    /** Written once under [ensureImported]'s monitor; `@Volatile` so [reader] on any thread sees that write. */
    @Volatile
    private var db: CompendiumDb? = null

    @Synchronized
    fun ensureImported(ctx: SealedLightContext) {
        val database = db ?: run {
            StaleDbFiles.delete(ctx.filesDir)
            ctx.buildDatabase(CompendiumDb::class.java, CompendiumDb.FILE_NAME)
        }.also { db = it }
        val source = AssetSource(ctx::readAsset)
        val marker = PrefsMarker(Prefs(ctx.dataStore))
        gate.ensure { onProgress ->
            AssetImporter(source, marker, RoomWriter(database), onProgress = onProgress).ensure()
        }
    }

    /**
     * The typed read facade. Only valid once [state] is Ready and throws `IllegalStateException` otherwise — a
     * screen must navigate off Home only after Ready, and this makes that contract fail loudly instead of returning
     * a reader over an empty or half-filled table. Ready implies [ensureImported] ran, so [db] is set by then.
     */
    fun reader(): CompendiumReader {
        gate.requireReady()
        return CompendiumReader(checkNotNull(db) { "CompendiumStore.ensureImported has not run" }.dao())
    }
}
