package com.tgx.react

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.UUID

/**
 * The ordered list of reactions. Images are copied into filesDir/reactions so they survive the
 * originals being deleted; the order lives in filesDir/reactions.json. Shared by the manager
 * activity and the keyboard (same process).
 */
object ReactionRepository {
    private lateinit var dir: File
    private lateinit var index: File
    private val lock = Any()
    private val _items = MutableStateFlow<List<Reaction>>(emptyList())
    val items: StateFlow<List<Reaction>> get() = _items

    fun init(context: Context) {
        dir = File(context.filesDir, "reactions").apply { mkdirs() }
        index = File(context.filesDir, "reactions.json")
        _items.value = if (index.exists()) ReactionList.fromJson(index.readText()) else emptyList()
        purgeOrphans()
    }

    fun fileFor(r: Reaction) = File(dir, r.file)

    /** Copies [uri] into app storage. Returns null if it isn't a readable image. */
    @WorkerThread
    fun import(resolver: ContentResolver, uri: Uri): Reaction? = runCatching {
        val mime = resolver.getType(uri)?.lowercase() ?: "image/png"
        if (!mime.startsWith("image/")) return null
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "img"
        val id = UUID.randomUUID().toString()
        val name = "$id.$ext"
        val out = File(dir, name)
        resolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
            ?: return null
        if (out.length() == 0L) { out.delete(); return null }
        Reaction(id, name, mime)
    }.getOrNull()

    fun add(new: List<Reaction>) = update { it + new }

    fun move(fromId: String, toId: String) = update { ReactionList.moveById(it, fromId, toId) }

    /** Removes from the list but keeps the files so the removal can be undone. */
    fun remove(ids: Set<String>): List<Pair<Int, Reaction>> {
        val current = _items.value
        val removed = current.withIndex().filter { it.value.id in ids }.map { it.index to it.value }
        update { ReactionList.remove(it, ids) }
        return removed
    }

    fun restore(removed: List<Pair<Int, Reaction>>) = update { ReactionList.restore(it, removed) }

    /** Deletes image files no longer referenced by the list. */
    fun purgeOrphans() = synchronized(lock) {
        val keep = _items.value.map { it.file }.toSet()
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    private fun update(op: (List<Reaction>) -> List<Reaction>) = synchronized(lock) {
        val next = op(_items.value)
        if (next == _items.value) return@synchronized
        _items.value = next
        val tmp = File(index.parentFile, "reactions.json.tmp")
        tmp.writeText(ReactionList.toJson(next))
        if (!tmp.renameTo(index)) { index.delete(); tmp.renameTo(index) }
    }
}
