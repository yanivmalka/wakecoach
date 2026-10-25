package com.wakecoach.core.story

import com.wakecoach.core.message.Lang
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * "What happens if you stay in bed" (requirement 10): a short, vivid story told when the user
 * reaches for snooze, built from today's real stakes when we know them.
 */
class ReverseStory(private val lang: Lang, private val random: Random = Random.Default) {

    fun tell(eventTime: LocalTime?, eventTitle: String?, priority: String?): String {
        val hhmm = DateTimeFormatter.ofPattern("HH:mm")
        val opening = pick(
            "בוא נדמיין רגע שלא קמת עכשיו.",
            "רק עוד חמש דקות? בוא נראה איך זה נגמר.",
            "Let's imagine you don't get up now.",
            "Just five more minutes? Let's see how that ends.",
        )
        val middle = when {
            eventTime != null && !eventTitle.isNullOrBlank() -> {
                val t = eventTime.format(hhmm)
                pick(
                    "פתחת עיניים, והשעון כבר מראה $t. $eventTitle התחיל בלעדיך, והטלפון מלא הודעות \"איפה אתה?\".",
                    "רצת החוצה בלי ארוחת בוקר, עמדת בפקק, והגעת ל$eventTitle כשכולם כבר מסכמים. $t עבר מזמן.",
                    "You open your eyes and it's already $t. $eventTitle started without you, and your phone is full of \"where are you?\".",
                    "You rush out without breakfast, hit traffic, and walk into $eventTitle as everyone is wrapping up. $t is long gone.",
                )
            }
            !priority.isNullOrBlank() -> pick(
                "אתמול בלילה אמרת שהיום חשוב לך: $priority. עכשיו זה נדחה שוב, כמו בפעם הקודמת.",
                "\"$priority\" חיכה לך היום. בסוף היום הוא עדיין מחכה.",
                "Last night you said today was about: $priority. Now it slips again, like last time.",
                "\"$priority\" was waiting for you today. By tonight it's still waiting.",
            )
            else -> pick(
                "קמת בבהלה, רצת לאוטובוס, והוא נסע לך מול העיניים. כל היום רדפת אחרי הזמן.",
                "הבוקר נעלם, הקפה התקרר, וכל היום הרגשת שאתה מאחר בחצי שעה.",
                "You jump up in a panic, run for the bus, and watch it pull away. All day you chase the clock.",
                "The morning vanishes, the coffee goes cold, and all day you feel half an hour behind.",
            )
        }
        val close = pick(
            "או שאתה קם עכשיו, וזה בכלל לא קורה.",
            "הבחירה שלך. קום עכשיו, והיום הזה שלך.",
            "Or you get up now, and none of it happens.",
            "Your call. Get up now and the day is yours.",
        )
        return "$opening $middle $close"
    }

    /** Two Hebrew variants then two English variants, in that order. */
    private fun pick(he1: String, he2: String, en1: String, en2: String): String {
        val first = random.nextBoolean()
        return if (lang == Lang.HE) (if (first) he1 else he2) else (if (first) en1 else en2)
    }
}
