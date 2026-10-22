package com.companionstudio.nyx.config

/** Rule IDs, shared with the backend and the fixture file names (D-53). Rules: docs/ARCHITECTURE.md §14.2. */
object ConfigRule {
    /** Everything JSON Schema can express: types, required fields, bounds, id pattern, non-blank text. */
    const val SCHEMA = "schema"
    const val SETTING_RANGE = "setting-range"
    const val TICK_ORDER = "tick-order"
    const val DUPLICATE_ID = "duplicate-id"
    const val MOOD_BANDS_COVERAGE = "mood-bands-coverage"
    const val PET_FACES = "pet-faces"
    const val LINE_REFERENCES = "line-references"
    const val LINE_COVERAGE = "line-coverage"
    const val LINE_PLACEHOLDERS = "line-placeholders"
    const val NOTHING_ENABLED = "nothing-enabled"
}

private const val MOOD_MAX = 100
private val ID_PATTERN = Regex("^[a-z0-9_]+$")

// Anything in braces, so a typo such as {App} is caught as well as an unknown name
private val PLACEHOLDER = Regex("\\{([^{}]*)\\}")

/** Returns every problem found, not just the first, so the console can show them all (US-19). */
fun validateConfig(config: NyxConfig): List<ConfigError> = ConfigChecks().run {
    checkNumbers(config)
    checkTexts(config)
    checkMood(config.mood)
    checkPetsAndPersonalities(config)
    checkLines(config)
    errors
}

private class ConfigChecks {
    val errors = mutableListOf<ConfigError>()

    private fun fail(rule: String, path: String, message: String) {
        errors += ConfigError(rule, path, message)
    }

    private fun number(path: String, value: Int, bounds: IntRange) {
        if (value !in bounds) fail(ConfigRule.SCHEMA, path, "$value is outside ${bounds.first}..${bounds.last}")
    }

    private fun setting(path: String, setting: Setting, bounds: IntRange) {
        number("$path.default", setting.default, bounds)
        number("$path.min", setting.min, bounds)
        number("$path.max", setting.max, bounds)
        // Also catches min > max: the range is then empty
        if (setting.default !in setting.min..setting.max) {
            fail(ConfigRule.SETTING_RANGE, path, "default ${setting.default} is not within min ${setting.min}..max ${setting.max}")
        }
    }

    private fun text(path: String, value: String) {
        if (value.isBlank()) fail(ConfigRule.SCHEMA, path, "must not be blank")
    }

    private fun id(path: String, value: String) {
        if (!ID_PATTERN.matches(value)) fail(ConfigRule.SCHEMA, path, "'$value' must match ${ID_PATTERN.pattern}")
    }

    private fun <T> uniqueIds(path: String, items: List<T>, idOf: (T) -> String) {
        items.groupingBy(idOf).eachCount().filterValues { it > 1 }.keys.forEach {
            fail(ConfigRule.DUPLICATE_ID, path, "id '$it' is used more than once")
        }
    }

    fun checkNumbers(config: NyxConfig) = with(config) {
        if (schemaVersion != SCHEMA_VERSION) fail(ConfigRule.SCHEMA, "schemaVersion", "$schemaVersion is not $SCHEMA_VERSION")
        if (configVersion < 1) fail(ConfigRule.SCHEMA, "configVersion", "$configVersion must be at least 1")

        setting(SettingPaths.RESET_HOUR, day.resetHour, 0..23)
        for ((category, limit) in limits.categories) {
            setting(SettingPaths.categoryDailyMinutes(category), limit.dailyMinutes, 0..1440)
        }
        setting(SettingPaths.TOTAL_DAILY_MINUTES, limits.totalDailyMinutes, 0..1440)
        setting(SettingPaths.STRETCH_MINUTES, limits.stretch.minutes, 1..1440)
        setting(SettingPaths.NAG_REPEAT_MINUTES, nags.repeatMinutes, 1..240)
        setting(SettingPaths.SNOOZE_MINUTES, nags.snoozeMinutes, 1..240)
        setting(SettingPaths.BREAK_AFTER_MINUTES, breaks.afterMinutes, 1..1440)
        setting(SettingPaths.FOCUS_DURATION_MINUTES, focus.durationMinutes, 1..1440)
        setting(SettingPaths.FOCUS_CHECKIN_MINUTES, focus.checkinMinutes, 1..240)
        setting(SettingPaths.GOAL_FOCUS_SESSIONS, goals.focusSessionsPerDay, 0..20)
        setting(SettingPaths.GOAL_PRODUCTIVE_MINUTES, goals.productiveMinutesPerDay, 0..1440)

        number("tracking.fastTickSeconds", tracking.fastTickSeconds, 1..60)
        number("tracking.slowTickSeconds", tracking.slowTickSeconds, 1..60)
        if (tracking.fastTickSeconds > tracking.slowTickSeconds) {
            fail(ConfigRule.TICK_ORDER, "tracking", "fastTickSeconds ${tracking.fastTickSeconds} is above slowTickSeconds ${tracking.slowTickSeconds}")
        }
        number("nags.maxSnoozesPerDay", nags.maxSnoozesPerDay, 0..100)
        number("breaks.breakMinutes", breaks.breakMinutes, 1..60)
        number("breaks.repeatMinutes", breaks.repeatMinutes, 1..240)
        number("breaks.obstructive.buttonDelaySeconds", breaks.obstructive.buttonDelaySeconds, 0..60)
        number("breaks.obstructive.snoozeMinutes", breaks.obstructive.snoozeMinutes, 1..60)
        number("breaks.obstructive.maxSnoozes", breaks.obstructive.maxSnoozes, 0..10)
        number("breaks.obstructive.returnAfterTakeBreakSeconds", breaks.obstructive.returnAfterTakeBreakSeconds, 10..600)
        number("focus.minDoneMinutes", focus.minDoneMinutes, 1..60)
        number("focus.abandonAfterMinutes", focus.abandonAfterMinutes, 5..240)
        number("bubbles.minGapSeconds", bubbles.minGapSeconds, 0..3600)
        number("bubbles.showSeconds", bubbles.showSeconds, 1..60)
        number("idle.everyMinutes", idle.everyMinutes, 1..1440)
        number("hide.minutes", hide.minutes, 1..1440)
    }

    fun checkTexts(config: NyxConfig) = with(config) {
        pets.forEachIndexed { i, pet ->
            id("pets[$i].id", pet.id)
            text("pets[$i].name", pet.name)
            pet.faces.forEach { (band, face) -> text("pets[$i].faces.$band", face) }
        }
        personalities.forEachIndexed { i, personality ->
            id("personalities[$i].id", personality.id)
            text("personalities[$i].name", personality.name)
            text("personalities[$i].previewLine", personality.previewLine)
        }
        for ((personality, triggers) in lines) {
            for ((trigger, pools) in triggers) {
                for ((pool, poolLines) in pools) {
                    poolLines.forEachIndexed { i, line -> text("lines.$personality.$trigger.$pool[$i]", line) }
                }
            }
        }
    }

    fun checkMood(mood: MoodConfig) {
        number("mood.start", mood.start, 0..MOOD_MAX)
        with(mood.weights) {
            listOf(
                "focus_completed" to focusCompleted, "goal_met" to goalMet, "break_taken" to breakTaken,
                "doomscroll_10min" to doomscroll10Min, "limit_exceeded" to limitExceeded, "snoozed" to snoozed,
            ).forEach { (name, weight) -> number("mood.weights.$name", weight, -100..100) }
        }
        mood.bands.forEachIndexed { i, band ->
            id("mood.bands[$i].id", band.id)
            number("mood.bands[$i].from", band.from, 0..MOOD_MAX)
            number("mood.bands[$i].to", band.to, 0..MOOD_MAX)
        }
        uniqueIds("mood.bands", mood.bands) { it.id }

        // Sorted by start, each band must begin right after the previous one ends, covering 0..100 exactly
        var next = 0
        for (band in mood.bands.sortedBy { it.from }) {
            when {
                band.from > band.to -> fail(ConfigRule.MOOD_BANDS_COVERAGE, "mood.bands", "band '${band.id}' starts after it ends")
                band.from > next -> fail(ConfigRule.MOOD_BANDS_COVERAGE, "mood.bands", "no band covers $next..${band.from - 1}")
                band.from < next -> fail(ConfigRule.MOOD_BANDS_COVERAGE, "mood.bands", "band '${band.id}' overlaps at ${band.from}")
            }
            next = maxOf(next, band.to + 1)
        }
        if (next <= MOOD_MAX) fail(ConfigRule.MOOD_BANDS_COVERAGE, "mood.bands", "no band covers $next..$MOOD_MAX")
    }

    fun checkPetsAndPersonalities(config: NyxConfig) = with(config) {
        uniqueIds("pets", pets) { it.id }
        uniqueIds("personalities", personalities) { it.id }
        val bandIds = mood.bands.map { it.id }
        pets.forEachIndexed { i, pet ->
            bandIds.filterNot { it in pet.faces }.forEach { fail(ConfigRule.PET_FACES, "pets[$i].faces", "no face for band '$it'") }
        }
        if (pets.none { it.enabled }) fail(ConfigRule.NOTHING_ENABLED, "pets", "no pet is enabled")
        if (personalities.none { it.enabled }) fail(ConfigRule.NOTHING_ENABLED, "personalities", "no personality is enabled")
    }

    fun checkLines(config: NyxConfig) = with(config) {
        val personalityIds = personalities.map { it.id }.toSet()
        val poolKeys = mood.bands.map { it.id }.toSet() + ANY_POOL
        // Unknown trigger names are fine (D-52), but their pool keys still have to make sense
        for ((personality, triggers) in lines) {
            if (personality !in personalityIds) fail(ConfigRule.LINE_REFERENCES, "lines.$personality", "no personality '$personality'")
            for ((trigger, pools) in triggers) {
                pools.keys.filterNot { it in poolKeys }.forEach {
                    fail(ConfigRule.LINE_REFERENCES, "lines.$personality.$trigger.$it", "'$it' is neither '$ANY_POOL' nor a mood band")
                }
            }
        }
        for (personality in personalityIds) {
            for (trigger in BubbleTrigger.entries) {
                if (lines[personality]?.get(trigger.id)?.get(ANY_POOL).isNullOrEmpty()) {
                    fail(ConfigRule.LINE_COVERAGE, "lines.$personality.${trigger.id}", "needs at least one '$ANY_POOL' line")
                }
            }
        }
        checkPlaceholders(lines)
    }

    // Only for triggers this build knows: a newer config may use placeholders a newer trigger allows (D-52)
    private fun checkPlaceholders(lines: Map<String, Map<String, Map<String, List<String>>>>) {
        for ((personality, triggers) in lines) {
            for (trigger in BubbleTrigger.entries) {
                for ((pool, poolLines) in triggers[trigger.id].orEmpty()) {
                    poolLines.forEachIndexed { i, line ->
                        PLACEHOLDER.findAll(line).map { it.groupValues[1] }.filterNot { it in trigger.placeholders }.forEach {
                            val allowed = trigger.placeholders.joinToString { name -> "{$name}" }.ifEmpty { "none" }
                            fail(
                                ConfigRule.LINE_PLACEHOLDERS,
                                "lines.$personality.${trigger.id}.$pool[$i]",
                                "{$it} can't be used in ${trigger.id} (allowed: $allowed)",
                            )
                        }
                    }
                }
            }
        }
    }
}
