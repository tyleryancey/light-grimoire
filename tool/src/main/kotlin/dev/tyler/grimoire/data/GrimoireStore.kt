package dev.tyler.grimoire.data

import android.os.SystemClock
import android.util.Log
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.buildDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The process-wide owner of the character database, mirroring `CompendiumStore`: one [GrimoireDb] built on
 * first use, one save loop over its own IO scope, and [SealedLightContext] taken as a parameter and retained
 * nowhere — the database is built over the application context, and nothing else of the context outlives
 * this call.
 *
 * **There is no Ready gate here, unlike the compendium.** The compendium needs one because an empty table
 * means "the import has not finished", which is a lie to draw a screen from. This database has no import:
 * `buildDatabase` opens the file lazily on the first query, and an empty table means the honest thing —
 * a new player with no characters yet. A gate would only add a state the screens have to wait through.
 *
 * **[characters] returns the same instance every time, and that is load-bearing.** The repository owns the
 * pending-write buffer; a per-screen instance would give every screen its own, so a value debounced on the
 * sheet screen would be invisible to Home's list a moment later and a screen pop would flush a buffer that
 * no longer had the write in it — silently defeating both the coalescing and the read-through that
 * `DebouncedSaver` exists for.
 *
 * The two clocks are deliberate. Deadlines run on [SystemClock.elapsedRealtime], which only ever moves
 * forward, so a debounced write cannot be stranded in the future by the phone correcting its wall clock;
 * `updatedAt` is wall-clock milliseconds, because it is what the Home list orders by and what a future
 * export would print.
 */
object GrimoireStore {
    private const val TAG = "Grimoire"

    /**
     * The process's writer scope: never cancelled, so everything launched on it finishes. Both the save loop
     * and the repository's two un-buffered writes ([DbCharacterRepository.create] and its `delete`) are
     * children of it, which is what keeps a write alive when the view model that asked for it is destroyed
     * mid-call.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Written once under [characters]'s monitor; `@Volatile` so a read on any thread sees that write. */
    @Volatile
    private var db: GrimoireDb? = null

    @Volatile
    private var repository: CharacterRepository? = null

    @Synchronized
    fun characters(ctx: SealedLightContext): CharacterRepository {
        repository?.let { return it }
        val database = db ?: ctx.buildDatabase(GrimoireDb::class.java, GrimoireDb.FILE_NAME).also { db = it }
        val dao = database.characters()
        val saver = DebouncedSaver(
            scope = scope,
            now = SystemClock::elapsedRealtime,
            log = { Log.i(TAG, it) },
        ) { save ->
            if (!DbCharacterRepository.write(dao, save)) {
                Log.i(TAG, "character save dropped, no such row: ${save.character.id}")
            }
        }
        return DbCharacterRepository(dao, saver, scope, System::currentTimeMillis).also { repository = it }
    }
}
