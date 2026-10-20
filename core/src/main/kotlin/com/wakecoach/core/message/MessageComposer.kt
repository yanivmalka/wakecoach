package com.wakecoach.core.message

import com.wakecoach.core.feedback.FeedbackStats
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

enum class Lang {
    HE, EN;

    companion object {
        fun of(locale: Locale): Lang = if (locale.language in setOf("he", "iw")) HE else EN
    }
}

/**
 * Builds the encouraging lines shown on the ringing and morning screens (requirement 2),
 * grounded in the user's own history so they read as facts, not slogans.
 */
class MessageComposer(private val lang: Lang, private val random: Random = Random.Default) {

    fun morningLine(stats: FeedbackStats, tomorrowPriority: String?): String {
        val candidates = buildList {
            if (stats.noSnoozeStreak >= 2) add(t(
                "${stats.noSnoozeStreak} בקרים ברצף בלי נודניק. אל תשבור את הרצף!",
                "${stats.noSnoozeStreak} mornings in a row without snooze. Don't break the streak!",
            ))
            val ease0 = stats.avgEaseNoSnooze
            val easeS = stats.avgEaseWithSnooze
            if (ease0 != null && easeS != null && ease0 > easeS) add(t(
                "כשאתה קם בלי נודניק הבוקר שלך מקבל ${fmt(ease0)}/5, ועם נודניק ${fmt(easeS)}/5.",
                "When you get up without snoozing you rate the morning ${fmt(ease0)}/5, with snooze ${fmt(easeS)}/5.",
            ))
            stats.avgMood?.takeIf { it >= 3.5 }?.let { add(t(
                "בממוצע אתה מרגיש ${fmt(it)}/5 בבקרים. עוד בוקר טוב מחכה לך.",
                "On average you feel ${fmt(it)}/5 in the mornings. Another good one is waiting.",
            )) }
        }
        val priorityLine = tomorrowPriority?.takeIf { it.isNotBlank() }?.let {
            t("אתמול אמרת שהיום חשוב לך: $it", "Last night you said today matters for: $it")
        }
        val base = candidates.randomOrNull(random) ?: t(
            "אני מאמין בך. קום, היום שלך מתחיל עכשיו.",
            "I believe in you. Get up, your day starts now.",
        )
        return listOfNotNull(priorityLine, base).joinToString("\n")
    }

    fun bedtimeLine(minutesUntilLightsOut: Long, tomorrowPriority: String?): String {
        val p = tomorrowPriority?.takeIf { it.isNotBlank() }
        return when {
            minutesUntilLightsOut <= 0 -> t("הגיע הזמן לכבות את המסך וללכת לישון.", "Time to put the phone down and sleep.")
            p != null -> t(
                "עוד $minutesUntilLightsOut דקות לכיבוי אורות. מחר: $p",
                "$minutesUntilLightsOut minutes to lights out. Tomorrow: $p",
            )
            else -> t(
                "עוד $minutesUntilLightsOut דקות לכיבוי אורות. הבוקר של מחר מתחיל הלילה.",
                "$minutesUntilLightsOut minutes to lights out. Tomorrow morning starts tonight.",
            )
        }
    }

    private fun t(he: String, en: String) = if (lang == Lang.HE) he else en

    private fun fmt(d: Double): String {
        val r = (d * 10).roundToInt() / 10.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
    }
}

/** Phrases for the type-to-dismiss challenge. */
object Phrases {
    private val he = listOf(
        "אני קם עכשיו ומתחיל את היום",
        "כל בוקר הוא הזדמנות חדשה",
        "הרגליים על הרצפה והראש למעלה",
    )
    private val en = listOf(
        "I am getting up now and starting my day",
        "Every morning is a new chance",
        "Feet on the floor and head up high",
    )

    fun forLang(lang: Lang) = if (lang == Lang.HE) he else en
}
