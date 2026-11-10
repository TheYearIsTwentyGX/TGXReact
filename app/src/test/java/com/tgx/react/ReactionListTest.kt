package com.tgx.react

import org.junit.Assert.assertEquals
import org.junit.Test

class ReactionListTest {
    private val a = Reaction("a", "a.png", "image/png")
    private val b = Reaction("b", "b.gif", "image/gif")
    private val c = Reaction("c", "c.jpg", "image/jpeg")
    private val list = listOf(a, b, c)

    @Test fun moveForwardAndBack() {
        assertEquals(listOf(b, c, a), ReactionList.move(list, 0, 2))
        assertEquals(listOf(c, a, b), ReactionList.move(list, 2, 0))
        assertEquals(list, ReactionList.move(list, 5, 0))
    }

    @Test fun moveById() = assertEquals(listOf(b, a, c), ReactionList.moveById(list, "a", "b"))

    @Test fun removeAndRestore() {
        val removedIdx = listOf(0 to a, 2 to c)
        val left = ReactionList.remove(list, setOf("a", "c"))
        assertEquals(listOf(b), left)
        assertEquals(list, ReactionList.restore(left, removedIdx))
    }

    @Test fun jsonRoundTrip() {
        assertEquals(list, ReactionList.fromJson(ReactionList.toJson(list)))
        assertEquals(emptyList<Reaction>(), ReactionList.fromJson("garbage"))
    }
}
