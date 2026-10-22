package com.companionstudio.nyx.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the shared fixtures don't show well. Every fixture rule is also covered by US21_ConfigFixturesTest. */
class ConfigValidatorTest {

    private val minimal = SharedFiles.minimalConfig

    private fun errorsOf(config: NyxConfig) = validateConfig(config).map { it.rule to it.path }

    @Test
    fun minimalConfig_hasNoErrors() {
        assertEquals(emptyList<ConfigError>(), validateConfig(minimal))
    }

    @Test
    fun severalProblems_areAllReported_withTheirPaths() {
        val config = minimal.copy(
            nags = minimal.nags.copy(snoozeMinutes = Setting(default = 40, min = 5, max = 30, userEditable = true)),
            tracking = minimal.tracking.copy(slowTickSeconds = 90),
            pets = minimal.pets.map { it.copy(faces = it.faces - "fine") },
        )

        assertEquals(
            listOf(
                ConfigRule.SETTING_RANGE to "nags.snoozeMinutes",
                ConfigRule.SCHEMA to "tracking.slowTickSeconds",
                ConfigRule.PET_FACES to "pets[0].faces",
            ),
            errorsOf(config),
        )
    }

    @Test
    fun bounds_exactlyAtTheEdges_pass_andOnePast_fails() {
        val atEdges = minimal.copy(
            day = minimal.day.copy(resetHour = Setting(default = 23, min = 0, max = 23, userEditable = true)),
            tracking = minimal.tracking.copy(fastTickSeconds = 60, slowTickSeconds = 60),
            bubbles = minimal.bubbles.copy(minGapSeconds = 0, showSeconds = 60),
            mood = minimal.mood.copy(start = 100, weights = minimal.mood.weights.copy(focusCompleted = 100, snoozed = -100)),
        )
        assertEquals(emptyList<ConfigError>(), validateConfig(atEdges))

        val past = atEdges.copy(day = atEdges.day.copy(resetHour = Setting(default = 23, min = 0, max = 24, userEditable = true)))
        assertEquals(listOf(ConfigRule.SCHEMA to "day.resetHour.max"), errorsOf(past))
    }

    @Test
    fun obstructiveBounds_atTheEdgesPass_andOnePastFails() {
        val edges = minimal.breaks.obstructive.copy(buttonDelaySeconds = 60, snoozeMinutes = 60, maxSnoozes = 10, returnAfterTakeBreakSeconds = 10)
        assertEquals(emptyList<ConfigError>(), validateConfig(minimal.copy(breaks = minimal.breaks.copy(obstructive = edges))))

        val past = edges.copy(buttonDelaySeconds = 61, snoozeMinutes = 0, maxSnoozes = 11, returnAfterTakeBreakSeconds = 601)
        assertEquals(
            listOf(
                ConfigRule.SCHEMA to "breaks.obstructive.buttonDelaySeconds",
                ConfigRule.SCHEMA to "breaks.obstructive.snoozeMinutes",
                ConfigRule.SCHEMA to "breaks.obstructive.maxSnoozes",
                ConfigRule.SCHEMA to "breaks.obstructive.returnAfterTakeBreakSeconds",
            ),
            errorsOf(minimal.copy(breaks = minimal.breaks.copy(obstructive = past))),
        )
    }

    @Test
    fun D120_focusBounds_atTheEdgesPass_andOnePastFails() {
        val edges = minimal.focus.copy(minDoneMinutes = 60, abandonAfterMinutes = 5)
        assertEquals(emptyList<ConfigError>(), validateConfig(minimal.copy(focus = edges)))
        assertEquals(emptyList<ConfigError>(), validateConfig(minimal.copy(focus = edges.copy(minDoneMinutes = 1, abandonAfterMinutes = 240))))

        val past = edges.copy(minDoneMinutes = 0, abandonAfterMinutes = 241)
        assertEquals(
            listOf(ConfigRule.SCHEMA to "focus.minDoneMinutes", ConfigRule.SCHEMA to "focus.abandonAfterMinutes"),
            errorsOf(minimal.copy(focus = past)),
        )
    }

    @Test
    fun minAboveMax_isASettingRangeError() {
        val config = minimal.copy(focus = minimal.focus.copy(checkinMinutes = Setting(default = 10, min = 20, max = 10, userEditable = true)))

        assertEquals(listOf(ConfigRule.SETTING_RANGE to "focus.checkinMinutes"), errorsOf(config))
    }

    @Test
    fun configVersionZero_andAnotherSchemaVersion_areSchemaErrors() {
        val config = minimal.copy(configVersion = 0, schemaVersion = 2)

        assertEquals(listOf(ConfigRule.SCHEMA to "schemaVersion", ConfigRule.SCHEMA to "configVersion"), errorsOf(config))
    }

    @Test
    fun noEnabledPersonality_isNothingEnabled() {
        val config = minimal.copy(personalities = minimal.personalities.map { it.copy(enabled = false) })

        assertEquals(listOf(ConfigRule.NOTHING_ENABLED to "personalities"), errorsOf(config))
    }

    @Test
    fun duplicatePersonality_isReported() {
        val config = minimal.copy(personalities = minimal.personalities + minimal.personalities)

        assertEquals(listOf(ConfigRule.DUPLICATE_ID to "personalities"), errorsOf(config))
    }

    @Test
    fun duplicateMoodBand_isReported_andAlsoOverlaps() {
        val bands = minimal.mood.bands
        val config = minimal.copy(mood = minimal.mood.copy(bands = bands + bands.last()))

        assertEquals(
            listOf(ConfigRule.DUPLICATE_ID to "mood.bands", ConfigRule.MOOD_BANDS_COVERAGE to "mood.bands"),
            errorsOf(config),
        )
    }

    @Test
    fun bandEndingBefore100_orStartingAfterItsEnd_isACoverageError() {
        val short = minimal.copy(mood = minimal.mood.copy(bands = listOf(MoodBand("ran_away", 0, 0), MoodBand("fine", 1, 99))))
        assertEquals(listOf(ConfigRule.MOOD_BANDS_COVERAGE to "mood.bands"), errorsOf(short))

        val backwards = minimal.copy(mood = minimal.mood.copy(bands = listOf(MoodBand("ran_away", 0, 0), MoodBand("fine", 100, 1))))
        assertTrue(errorsOf(backwards).all { it.first == ConfigRule.MOOD_BANDS_COVERAGE })
    }

    @Test
    fun poolKeyThatIsNeitherAnyNorABand_isALineReference() {
        val lines = minimal.lines.mapValues { (_, triggers) ->
            triggers + ("idle" to (triggers.getValue("idle") + ("gret" to listOf("Typo in the band id."))))
        }

        assertEquals(listOf(ConfigRule.LINE_REFERENCES to "lines.plain.idle.gret"), errorsOf(minimal.copy(lines = lines)))
    }

    @Test
    fun blankTexts_andBadIds_areSchemaErrors() {
        val config = minimal.copy(
            pets = minimal.pets.map { it.copy(faces = it.faces + ("fine" to " ")) },
            personalities = minimal.personalities.map { it.copy(id = "Plain", name = "", previewLine = "\t") },
        )

        assertEquals(
            listOf(
                ConfigRule.SCHEMA to "pets[0].faces.fine",
                ConfigRule.SCHEMA to "personalities[0].id",
                ConfigRule.SCHEMA to "personalities[0].name",
                ConfigRule.SCHEMA to "personalities[0].previewLine",
                // The lines still belong to "plain", which no longer exists
                ConfigRule.LINE_REFERENCES to "lines.plain",
            ),
            errorsOf(config).filterNot { it.first == ConfigRule.LINE_COVERAGE },
        )
    }

    @Test
    fun linePlaceholders_onlyThoseTheTriggerAllows_andTyposAreCaught() {
        fun withLine(trigger: BubbleTrigger, line: String) = minimal.copy(
            lines = mapOf("plain" to minimal.lines.getValue("plain") + (trigger.id to mapOf(ANY_POOL to listOf("Fine.", line)))),
        )

        assertEquals(emptyList<ConfigError>(), validateConfig(withLine(BubbleTrigger.LIMIT_REACHED, "{limit} of {category} in {app}.")))
        assertEquals(emptyList<ConfigError>(), validateConfig(withLine(BubbleTrigger.FOCUS_CHECKIN, "{minutes} minutes in.")))
        assertEquals(
            listOf(ConfigRule.LINE_PLACEHOLDERS to "lines.plain.break_due.any[1]"),
            errorsOf(withLine(BubbleTrigger.BREAK_DUE, "Close {app} for {minutes} minutes.")),
        )
        assertEquals(
            listOf(ConfigRule.LINE_PLACEHOLDERS to "lines.plain.focus_drift.any[1]"),
            errorsOf(withLine(BubbleTrigger.FOCUS_DRIFT, "{App} isn't on the plan.")),
        )
        assertEquals(
            listOf(ConfigRule.LINE_PLACEHOLDERS to "lines.plain.idle.any[1]", ConfigRule.LINE_PLACEHOLDERS to "lines.plain.idle.any[1]"),
            errorsOf(withLine(BubbleTrigger.IDLE, "{} and {name}")),
        )
    }

    @Test
    fun linePlaceholders_inUnknownTriggers_areIgnored_D52() {
        val config = minimal.copy(
            lines = mapOf("plain" to minimal.lines.getValue("plain") + ("future_trigger" to mapOf(ANY_POOL to listOf("{whatever}")))),
        )

        assertEquals(emptyList<ConfigError>(), validateConfig(config))
    }

    @Test
    fun knownDifferencesFromJsonSchema_D54() {
        // kotlinx reads "3" as 3 and "true" as true, which JSON Schema rejects, and rejects 3.0, which JSON
        // Schema accepts. Neither can reach a phone (D-54). This pins the behaviour so a kotlinx upgrade that
        // changes it is noticed
        val text = SharedFiles.fixture("valid", "minimal")
        fun parse(from: String, to: String) = parseConfig(text.replace(from, to).also { check(it != text) })

        assertTrue(parse("\"maxSnoozesPerDay\": 3", "\"maxSnoozesPerDay\": \"3\"") is ConfigResult.Valid)
        assertTrue(parse("\"focus\": true", "\"focus\": \"true\"") is ConfigResult.Valid)
        assertTrue(parse("\"maxSnoozesPerDay\": 3", "\"maxSnoozesPerDay\": 3.0") is ConfigResult.Invalid)
    }
}
