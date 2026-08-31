package dev.tyler.grimoire.compendium

import java.security.MessageDigest

/**
 * Test-tree mirror of pipeline/emit.py (`emit`, the `sha256`/`bundle_hash` lines): per-file sha256 of the
 * emitted bytes, then sha256 of `"$name:$sha256"` joined over the file names in sorted order. The device
 * never hashes anything (ADR-0002, plan D5); this exists only so IndexIntegrityTest can prove index.json
 * still describes the bytes that ship beside it.
 */
object BundleHash {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun of(files: Map<String, FileMeta>): String =
        sha256(files.toSortedMap().entries.joinToString("") { (name, meta) -> "$name:${meta.sha256}" }.encodeToByteArray())
}
