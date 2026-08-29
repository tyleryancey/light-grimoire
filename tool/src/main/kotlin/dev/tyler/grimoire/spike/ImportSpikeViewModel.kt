package dev.tyler.grimoire.spike

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.buildDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ImportSpikeViewModel(private val ctx: SealedLightContext) : LightViewModel<Unit>() {
    val status = MutableStateFlow("importing…")
    private var started = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (started) return // onScreenShow also fires on onResume
        started = true
        viewModelScope.launch(Dispatchers.IO) { run() }
    }

    private suspend fun run() {
        val t0 = System.nanoTime()
        val index = Json.parseToJsonElement(ctx.readAsset("compendium/index.json").decodeToString()).jsonObject
        val files = index.getValue("files").jsonObject.keys
        val rows = ArrayList<CompendiumRow>(2_500)
        for (file in files) {
            val kind = file.removeSuffix(".json")
            for (el in Json.parseToJsonElement(ctx.readAsset("compendium/$file").decodeToString()).jsonArray) {
                val obj = el.jsonObject
                val key = obj.getValue("key").jsonPrimitive.content
                val name = obj["name"]?.jsonPrimitive?.content ?: key
                rows += CompendiumRow(kind, key, name, el.toString())
            }
        }
        val t1 = System.nanoTime()
        val db = ctx.buildDatabase(SpikeDb::class.java, "spike-$t0.db") // fresh file per run = cold import
        db.dao().insertAll(rows)
        val t2 = System.nanoTime()
        val hits = db.dao().ftsCount("fire*")
        val count = db.dao().count()
        val t3 = System.nanoTime()
        val line = "rows=$count decode=${ms(t0, t1)}ms insert+fts=${ms(t1, t2)}ms query=${ms(t2, t3)}ms hits=$hits total=${ms(t0, t3)}ms"
        Log.i("Spike", line)
        status.value = line
    }

    private fun ms(a: Long, b: Long): Long = (b - a) / 1_000_000
}
