package com.wakecoach.core

import com.wakecoach.core.challenge.Challenge
import com.wakecoach.core.challenge.ChallengeGenerator
import com.wakecoach.core.message.Address
import com.wakecoach.core.message.Lang
import com.wakecoach.core.message.MessageComposer
import com.wakecoach.core.model.ChallengeType
import com.wakecoach.core.model.Intensity
import com.wakecoach.core.model.Profiles
import com.wakecoach.core.skill.Param
import com.wakecoach.core.skill.SkillConfig
import com.wakecoach.core.skill.SkillMode
import com.wakecoach.core.skill.Skills
import com.wakecoach.core.story.ReverseStory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillTest {
    private val gen = ChallengeGenerator(Random(3))

    @Test fun `config survives encoding and clamps bad values`() {
        val c = SkillConfig(ChallengeType.CATCH_SUN, mapOf(Param.ROUNDS to 8, Param.GRID to 4))
        assertEquals(c.resolved(), SkillConfig.decode(c.encode())!!.resolved())
        val wild = SkillConfig.decode("CATCH_SUN|ROUNDS=999,GRID=x,NOPE=3")!!
        assertEquals(12, wild[Param.ROUNDS])
        assertEquals(3, wild[Param.GRID])
        assertNull(SkillConfig.decode("NOT_A_SKILL|COUNT=3"))
        val list = listOf(c, Skills.defaults(ChallengeType.STEPS, Intensity.FIRM))
        assertEquals(list.map { it.resolved() }, SkillConfig.decodeList(SkillConfig.encodeList(list)).map { it.resolved() })
    }

    @Test fun `config builds the matching challenge`() {
        val sun = gen.fromConfig(SkillConfig(ChallengeType.CATCH_SUN, mapOf(Param.ROUNDS to 3, Param.GRID to 5)), emptyList())
        assertTrue(sun is Challenge.CatchSun && sun.rounds == 3 && sun.boards.all { it.columns == 5 })
        val steps = gen.fromConfig(SkillConfig(ChallengeType.STEPS, mapOf(Param.COUNT to 12)), emptyList())
        assertEquals(Challenge.Steps(12), steps)
        val breathe = gen.fromConfig(SkillConfig(ChallengeType.BREATHE, mapOf(Param.ROUNDS to 2, Param.HOLD to 0)), emptyList())
        assertEquals(Challenge.Breathe(2, 4, 0, 5), breathe)
    }

    @Test fun `suggested plans go from light to heavy and skip math and typing`() {
        Intensity.entries.forEach { level ->
            val plan = Skills.suggestedPlan(level)
            assertTrue(plan.isNotEmpty())
            assertEquals(plan.map { Skills.weight(it.type) }.sorted(), plan.map { Skills.weight(it.type) })
            assertTrue(plan.none { it.type == ChallengeType.MATH || it.type == ChallengeType.TYPE_PHRASE })
        }
        assertFalse(ChallengeType.MATH in Skills.all)
        val sorted = Skills.lightToHeavy(listOf(ChallengeType.STEPS, ChallengeType.CATCH_SUN, ChallengeType.STAND_UP)) { it }
        assertEquals(listOf(ChallengeType.CATCH_SUN, ChallengeType.STAND_UP, ChallengeType.STEPS), sorted)
    }

    @Test fun `custom profile chains its skills or escalates per snooze`() {
        val skills = listOf(
            SkillConfig(ChallengeType.CATCH_SUN, mapOf(Param.ROUNDS to 2)),
            SkillConfig(ChallengeType.STAND_UP, mapOf(Param.SECONDS to 5)),
            SkillConfig(ChallengeType.STEPS, mapOf(Param.COUNT to 20)),
        )
        val chain = Profiles.BALANCED.copy(skills = skills, skillMode = SkillMode.CHAIN)
        assertEquals(3, gen.forRing(chain, snoozeCount = 0, phrases = emptyList()).size)
        val esc = chain.copy(skillMode = SkillMode.ESCALATE)
        assertTrue(gen.forRing(esc, 0, emptyList()).single() is Challenge.CatchSun)
        assertEquals(Challenge.StandUp(5), gen.forRing(esc, 1, emptyList()).single())
        // More snoozes than skills keep the heaviest one.
        assertEquals(Challenge.Steps(20), gen.forRing(esc, 7, emptyList()).single())
        // Built-ins keep picking one at random.
        assertEquals(1, gen.forRing(Profiles.FIRM, 0, emptyList()).size)
    }

    @Test fun `hebrew follows the chosen form of address`() {
        val male = MessageComposer(Lang.HE, Random(1), Address.MALE).bedtimeLine(0, null)
        assertTrue(male.isNotBlank())
        val story = { a: Address -> ReverseStory(Lang.HE, Random(5), a).tell(null, null, null) }
        assertTrue(story(Address.FEMALE).contains("קומי") || story(Address.FEMALE).contains("קמה") || story(Address.FEMALE).contains("בואי"))
        assertFalse(story(Address.UNSET).contains("אתה") || story(Address.UNSET).contains("קומי"))
        val en = ReverseStory(Lang.EN, Random(5), Address.FEMALE).tell(null, null, null)
        assertEquals(ReverseStory(Lang.EN, Random(5), Address.MALE).tell(null, null, null), en)
    }
}
