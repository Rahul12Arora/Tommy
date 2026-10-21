package com.companionstudio.nyx.debug

import com.companionstudio.nyx.tracking.UsageEvent
import com.companionstudio.nyx.tracking.UsageEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant

class ScenarioTest {

    private val start = Instant.parse("2026-10-21T10:00:00Z")

    // Unit tests run with the module folder as the working directory
    private val scenarioFiles = File("src/debug/assets/scenarios").listFiles { file -> file.extension == "txt" }.orEmpty()

    @Test
    fun everyShippedScenario_parses() {
        assertTrue("no scenario files found", scenarioFiles.size >= 3)
        for (file in scenarioFiles) {
            assertTrue(file.name, parseScenario(file.readText()).isNotEmpty())
        }
    }

    @Test
    fun steps_durations_andComments() {
        val steps = parseScenario(
            """
            # a comment
            app com.instagram.android 65m   # trailing comment
            idle 90s

            screen_off 2h
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                ScenarioStep.App("com.instagram.android", Duration.ofMinutes(65)),
                ScenarioStep.Idle(Duration.ofSeconds(90)),
                ScenarioStep.ScreenOff(Duration.ofHours(2)),
            ),
            steps,
        )
    }

    @Test
    fun aLineItDoesntUnderstand_rejectsTheFile_withItsNumber() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseScenario("app com.instagram.android 5m\napp com.instagram.android 5 minutes")
        }
        assertTrue(error.message!!, error.message!!.contains("line 2"))
        assertThrows(IllegalArgumentException::class.java) { parseScenario("app com.instagram.android 0m") }
        assertThrows(IllegalArgumentException::class.java) { parseScenario("idle 5d") }
        assertThrows(IllegalArgumentException::class.java) { parseScenario("dance 5m") }
    }

    @Test
    fun events_areTimedFromTheStart() {
        val steps = listOf(
            ScenarioStep.App("com.instagram.android", Duration.ofMinutes(20)),
            ScenarioStep.ScreenOff(Duration.ofMinutes(6)),
            ScenarioStep.Idle(Duration.ofMinutes(1)),
            ScenarioStep.App("com.android.chrome", Duration.ofMinutes(5)),
        )

        val events = scenarioEvents(steps, start)

        assertEquals(
            listOf(
                UsageEvent(UsageEventType.SCREEN_ON, start),
                UsageEvent(UsageEventType.APP_RESUMED, start, "com.instagram.android", "scenario"),
                UsageEvent(UsageEventType.APP_PAUSED, start.plusSeconds(20 * 60), "com.instagram.android", "scenario"),
                UsageEvent(UsageEventType.SCREEN_OFF, start.plusSeconds(20 * 60)),
                UsageEvent(UsageEventType.SCREEN_ON, start.plusSeconds(26 * 60)),
                UsageEvent(UsageEventType.APP_RESUMED, start.plusSeconds(27 * 60), "com.android.chrome", "scenario"),
                UsageEvent(UsageEventType.APP_PAUSED, start.plusSeconds(32 * 60), "com.android.chrome", "scenario"),
            ),
            events,
        )
    }

    @Test
    fun source_returnsOnlyEventsInTheWindow() {
        val events = scenarioEvents(listOf(ScenarioStep.App("com.instagram.android", Duration.ofMinutes(10))), start)
        val source = ScenarioUsageSource(events)

        // The screen-on and the resume at the start; the pause at exactly +10 min isn't included
        assertEquals(2, source.events(start, start.plusSeconds(600)).size)
        assertEquals(3, source.events(start, start.plusSeconds(601)).size)
        assertEquals(0, source.events(start.plusSeconds(1), start.plusSeconds(600)).size)
    }
}
