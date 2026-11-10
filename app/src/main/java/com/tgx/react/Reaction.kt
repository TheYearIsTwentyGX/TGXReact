package com.tgx.react

import org.json.JSONArray
import org.json.JSONObject

/** One reaction image, stored as [file] inside the app's reactions directory. */
data class Reaction(val id: String, val file: String, val mime: String)

/** Pure list operations and (de)serialisation, kept free of Android so they can be unit-tested. */
object ReactionList {
    fun move(list: List<Reaction>, from: Int, to: Int): List<Reaction> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun moveById(list: List<Reaction>, fromId: String, toId: String): List<Reaction> =
        move(list, list.indexOfFirst { it.id == fromId }, list.indexOfFirst { it.id == toId })

    fun remove(list: List<Reaction>, ids: Set<String>): List<Reaction> = list.filterNot { it.id in ids }

    /** Re-inserts [removed] (original index to item), lowest index first, so positions are restored. */
    fun restore(list: List<Reaction>, removed: List<Pair<Int, Reaction>>): List<Reaction> {
        val out = list.toMutableList()
        removed.sortedBy { it.first }.forEach { (i, r) ->
            if (out.none { it.id == r.id }) out.add(i.coerceIn(0, out.size), r)
        }
        return out
    }

    fun toJson(list: List<Reaction>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("file", it.file).put("mime", it.mime)) }
    }.toString()

    fun fromJson(text: String): List<Reaction> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            val file = o.optString("file")
            if (id.isEmpty() || file.isEmpty()) null
            else Reaction(id, file, o.optString("mime").ifEmpty { "image/png" })
        }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}
