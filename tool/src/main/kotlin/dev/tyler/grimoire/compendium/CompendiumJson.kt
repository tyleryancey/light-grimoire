package dev.tyler.grimoire.compendium

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * The one codec for everything under assets/compendium (plan D2). Strict on purpose: an unknown key is a
 * pipeline field the Kotlin model does not know yet, and the JVM gate (RecordsDecodeTest) has to fail until
 * the model gains it — never loosen this to `ignoreUnknownKeys`. `explicitNulls = false` lets the emitted
 * `"field": null` and an absent key both read as null. Nothing here is ever encoded (the `json` column is
 * the raw asset slice), so `encodeDefaults` is moot and kept off.
 */
val CompendiumJson: Json = Json {
    ignoreUnknownKeys = false
    explicitNulls = false
    encodeDefaults = false
}

/** One entry of `index.files`: the emitted file's size, sha256 and record count (pipeline/emit.py). */
@Serializable
data class FileMeta(val bytes: Int, val count: Int, val sha256: String)

/**
 * assets/compendium/index.json — the importer's only source of truth for what ships (ADR-0002, plan D5).
 * `bundleSha256` feeds the DataStore stamp; [total] is the row count a ready database must hold.
 */
@Serializable
data class CompendiumIndex(
    val schemaVersion: Int,
    val edition: String,
    val srdVersion: String,
    val license: String,
    val attribution: String,
    val bundleSha256: String,
    val files: Map<String, FileMeta>,
    val sources: JsonObject,
) {
    /** Σ files[*].count — the number of `records` rows a complete import produces. */
    val total: Int
        get() = files.values.sumOf { it.count }
}
