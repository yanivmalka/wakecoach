package com.wakecoach.core.content

import kotlin.random.Random

enum class QuoteCategory(val key: String) {
    FAITH("faith"), PHILOSOPHY("philosophy"), FACTS("facts"), JOKES("jokes");

    companion object {
        fun of(key: String): QuoteCategory? = entries.firstOrNull { it.key == key }
    }
}

data class Quote(val category: QuoteCategory, val text: String, val source: String) {
    /** Stable id for "recently shown" tracking; texts are unique within a language file. */
    val id: Int get() = text.hashCode()
}

/** Picks the morning line from the enabled categories without repeating the last few. */
class QuotePicker(private val random: Random = Random.Default) {

    fun pick(all: List<Quote>, enabled: Set<QuoteCategory>, recentIds: List<Int>, avoidLast: Int = 10): Quote? {
        val pool = all.filter { it.category in enabled }
        if (pool.isEmpty()) return null
        val recent = recentIds.takeLast(avoidLast).toSet()
        val fresh = pool.filter { it.id !in recent }
        return (fresh.ifEmpty { pool }).random(random)
    }
}
