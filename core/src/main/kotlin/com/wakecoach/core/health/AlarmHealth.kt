package com.wakecoach.core.health

/** Things on the phone that can stop an alarm from ringing, in the order they are shown. */
enum class HealthItem(
    /** A failing critical item means alarms may not ring at all; others make them easy to miss. */
    val critical: Boolean,
) {
    NOTIFICATIONS(critical = true),
    FULL_SCREEN(critical = true),
    EXACT_ALARMS(critical = true),
    NEXT_ALARM_REGISTERED(critical = true),
    BATTERY_OPTIMIZATION(critical = true),
    BACKGROUND_RESTRICTED(critical = true),
    SAMSUNG_SLEEPING(critical = true),
    ALARM_VOLUME(critical = false),
    DND_SILENCE(critical = false),
    /** Step access lets the back-to-sleep check tell walking from lying in bed. */
    STEP_ACCESS(critical = false),
}

data class HealthCheck(val item: HealthItem, val ok: Boolean)

data class HealthSummary(val problems: Int, val critical: Int) {
    val allGood: Boolean get() = problems == 0
}

object AlarmHealth {
    fun summarize(checks: List<HealthCheck>): HealthSummary {
        val failing = checks.filterNot { it.ok }
        return HealthSummary(problems = failing.size, critical = failing.count { it.item.critical })
    }

    /** Failing items first (critical before minor), then the rest, each group in declaration order. */
    fun ordered(checks: List<HealthCheck>): List<HealthCheck> =
        checks.sortedWith(compareBy<HealthCheck>({ it.ok }, { !it.item.critical }, { it.item.ordinal }))
}
