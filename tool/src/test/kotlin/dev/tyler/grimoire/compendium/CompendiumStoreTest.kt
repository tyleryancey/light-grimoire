package dev.tyler.grimoire.compendium

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The process singleton itself, as far as the JVM can drive it: it starts Idle and `reader()` refuses until the
 * gate is Ready (the device path — `ensureImported` over a `SealedLightContext` — is verified on the LP3). This pins
 * the contract task-2 screens inherit: a reader is never handed out over an empty or half-filled table.
 */
class CompendiumStoreTest {
    @Test
    fun readerRefusesUntilTheStoreIsReadyAndLeavesTheStateAlone() {
        assertEquals(ImportState.Idle, CompendiumStore.state.value, "nothing has called ensureImported on the JVM")
        val e = assertFailsWith<IllegalStateException>("no reader before Ready") { CompendiumStore.reader() }
        assertTrue("Idle" in e.message.orEmpty(), "the current state is named: ${e.message}")
        assertEquals(ImportState.Idle, CompendiumStore.state.value, "reader() does not move the state")
    }
}
