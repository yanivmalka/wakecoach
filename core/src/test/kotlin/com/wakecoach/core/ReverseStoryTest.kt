package com.wakecoach.core

import com.wakecoach.core.message.Lang
import com.wakecoach.core.story.ReverseStory
import java.time.LocalTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class ReverseStoryTest {
    @Test fun usesTheRealStakes() {
        repeat(10) { seed ->
            val withEvent = ReverseStory(Lang.EN, Random(seed)).tell(LocalTime.of(8, 30), "the team meeting", "gym")
            assertTrue("the team meeting" in withEvent && "08:30" in withEvent, withEvent)
            val withPriority = ReverseStory(Lang.HE, Random(seed)).tell(null, null, "מבחן בנהיגה")
            assertTrue("מבחן בנהיגה" in withPriority, withPriority)
            val generic = ReverseStory(Lang.EN, Random(seed)).tell(null, null, null)
            assertTrue(generic.split(". ").size >= 2)
        }
    }
}
