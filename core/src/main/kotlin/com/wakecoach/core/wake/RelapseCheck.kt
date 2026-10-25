package com.wakecoach.core.wake

import com.wakecoach.core.model.Intensity
import kotlin.random.Random

/** What a surprise check a few minutes after dismissal concludes (requirement 8). */
enum class RelapseVerdict {
    /** Phone in use or the user walked: leave them alone. */
    AWAKE,
    /** Screen off and barely a step: ring again, full alarm. */
    RELAPSE,
    /** Can't tell (no step sensor or permission): a soft one-tap "still awake?" ring. */
    ASK,
}

object RelapseCheck {
    /** Steps since dismissal that count as "got up and moved". */
    const val AWAKE_STEPS = 15

    /** How many re-rings a single morning may get, so it never loops forever. */
    const val MAX_RELAPSES = 2

    /**
     * [stepsSinceDismiss] is null when steps cannot be read. [interactive] is the screen state
     * right now: someone using the phone is awake, whatever the step count says.
     */
    fun decide(stepsSinceDismiss: Int?, interactive: Boolean): RelapseVerdict = when {
        interactive -> RelapseVerdict.AWAKE
        stepsSinceDismiss == null -> RelapseVerdict.ASK
        stepsSinceDismiss >= AWAKE_STEPS -> RelapseVerdict.AWAKE
        else -> RelapseVerdict.RELAPSE
    }

    /**
     * Minutes after dismissal for the check: random so it can't be gamed, and sooner on
     * harder profiles. A gentle profile is checked later, giving more benefit of the doubt.
     */
    fun delayMinutes(intensity: Intensity, random: Random = Random.Default): Int {
        val (from, to) = when (intensity) {
            Intensity.GENTLE -> 7 to 10
            Intensity.BALANCED -> 5 to 9
            Intensity.FIRM, Intensity.HARDCORE -> 4 to 7
        }
        return random.nextInt(from, to + 1)
    }
}
