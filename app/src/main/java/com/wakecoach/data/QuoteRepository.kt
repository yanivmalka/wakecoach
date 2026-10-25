package com.wakecoach.data

import android.content.Context
import com.wakecoach.core.content.Quote
import com.wakecoach.core.content.QuoteCategory
import com.wakecoach.core.content.QuotePicker
import com.wakecoach.core.message.Lang
import org.json.JSONObject

/** Morning lines from assets/quotes_<lang>.json; remembers recent picks to avoid repeats. */
class QuoteRepository(private val context: Context, private val settings: SettingsRepository) {
    private val cache = mutableMapOf<Lang, List<Quote>>()
    private val picker = QuotePicker()

    suspend fun next(lang: Lang): Quote? {
        val s = settings.current()
        val enabled = s.quoteCategories.mapNotNull(QuoteCategory::of).toSet()
        val quote = picker.pick(load(lang), enabled, s.recentQuoteIds) ?: return null
        settings.update { it.copy(recentQuoteIds = (it.recentQuoteIds + quote.id).takeLast(20)) }
        return quote
    }

    private fun load(lang: Lang): List<Quote> = cache.getOrPut(lang) {
        val file = if (lang == Lang.HE) "quotes_he.json" else "quotes_en.json"
        val json = JSONObject(context.assets.open(file).bufferedReader().use { it.readText() })
        QuoteCategory.entries.flatMap { cat ->
            val arr = json.optJSONArray(cat.key) ?: return@flatMap emptyList()
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Quote(cat, o.getString("text"), o.optString("source"))
            }
        }
    }
}
