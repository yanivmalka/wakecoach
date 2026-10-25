package com.wakecoach.core

import com.wakecoach.core.content.Quote
import com.wakecoach.core.content.QuoteCategory
import com.wakecoach.core.content.QuotePicker
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuotePickerTest {
    private val quotes = listOf(
        Quote(QuoteCategory.FAITH, "a", ""), Quote(QuoteCategory.FAITH, "b", ""), Quote(QuoteCategory.JOKES, "c", ""),
    )

    @Test fun respectsCategoriesAndAvoidsRecent() {
        val p = QuotePicker(Random(1))
        repeat(20) {
            assertEquals("b", p.pick(quotes, setOf(QuoteCategory.FAITH), recentIds = listOf("a".hashCode()))!!.text)
        }
    }

    @Test fun fallsBackWhenAllRecentAndNullWhenNoneEnabled() {
        val p = QuotePicker(Random(1))
        val all = quotes.map { it.id }
        assertEquals(QuoteCategory.JOKES, p.pick(quotes, setOf(QuoteCategory.JOKES), all)!!.category)
        assertNull(p.pick(quotes, emptySet(), emptyList()))
    }
}
