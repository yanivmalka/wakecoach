package com.wakecoach.core

import com.wakecoach.core.update.UpdateCheck
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateCheckTest {
    private val apk = listOf("wakecoach.apk" to "https://example.test/wakecoach.apk")

    @Test fun readsVersionCodeFromBody() {
        val r = UpdateCheck.parse("v0.2.15", "versionCode: 115\n\n- Wheel picker\n- Dark theme", apk)!!
        assertEquals(115, r.versionCode)
        assertEquals("0.2.15", r.versionName)
        assertEquals("- Wheel picker\n- Dark theme", r.notes)
        assertEquals("https://example.test/wakecoach.apk", r.apkUrl)
    }

    @Test fun fallsBackToTagRunNumber() {
        assertEquals(107, UpdateCheck.parse("v0.2.7", "no code here", apk)!!.versionCode)
    }

    @Test fun rejectsReleaseWithoutApkOrVersion() {
        assertNull(UpdateCheck.parse("v0.2.7", "", listOf("notes.txt" to "x")))
        assertNull(UpdateCheck.parse("nightly", "", apk))
    }

    @Test fun newerAndAutoCheckWindow() {
        val r = UpdateCheck.parse("v0.2.3", null, apk)!!
        assertTrue(UpdateCheck.isNewer(r, 2))
        assertFalse(UpdateCheck.isNewer(r, 103))
        val h = 60 * 60 * 1000L
        assertFalse(UpdateCheck.dueForAutoCheck(lastCheckMs = 0, nowMs = 5 * h))
        assertTrue(UpdateCheck.dueForAutoCheck(lastCheckMs = 0, nowMs = 6 * h))
        assertTrue(UpdateCheck.dueForAutoCheck(lastCheckMs = 10 * h, nowMs = h)) // clock moved back
    }
}
