package com.companionstudio.nyx.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The config shape this build understands. A document with another schema version is rejected (§5). */
const val SCHEMA_VERSION = 1

/**
 * The one config document (docs/ARCHITECTURE.md §14.2), mirroring `shared/config.schema.json`.
 * Every field is required on purpose: a missing field must be a validation error, not a silent default.
 */
@Serializable
data class NyxConfig(
    val schemaVersion: Int,
    val configVersion: Int,
    val features: Features,
    val day: DayConfig,
    val tracking: TrackingConfig,
    val limits: LimitsConfig,
    val nags: NagsConfig,
    val breaks: BreaksConfig,
    val focus: FocusConfig,
    val bubbles: BubblesConfig,
    val idle: IdleConfig,
    val hide: HideConfig,
    val mood: MoodConfig,
    val pets: List<Pet>,
    val personalities: List<Personality>,
    /** Personality id → trigger → mood band id or [ANY_POOL] → lines. */
    val lines: Map<String, Map<String, Map<String, List<String>>>>,
    /** Package name → category (US-04). */
    val categoryDefaults: Map<String, Category>,
    /** Android's app category name → ours. */
    val androidCategoryMap: Map<String, Category>,
)

/** A number the admin tunes; a locked one is simply `userEditable = false`. */
@Serializable
data class Setting(val default: Int, val min: Int, val max: Int, val userEditable: Boolean)

@Serializable
data class Features(val focus: Boolean, val breaks: Boolean, val idleLines: Boolean, val trends: Boolean, val stats: Boolean)

@Serializable
data class DayConfig(val resetHour: Setting)

@Serializable
data class TrackingConfig(val fastTickSeconds: Int, val slowTickSeconds: Int, val doomscrollCategories: List<Category>)

@Serializable
data class LimitsConfig(
    /** A category with no entry has no daily limit. */
    val categories: Map<Category, CategoryLimit>,
    val totalDailyMinutes: Setting,
    val stretch: StretchLimit,
)

@Serializable
data class CategoryLimit(val dailyMinutes: Setting)

@Serializable
data class StretchLimit(val minutes: Setting, val categories: List<Category>)

@Serializable
data class NagsConfig(val repeatMinutes: Setting, val snoozeMinutes: Setting, val maxSnoozesPerDay: Int)

@Serializable
data class BreaksConfig(
    val afterMinutes: Setting,
    val breakMinutes: Int,
    val repeatMinutes: Int,
    /** Added in M7 with a default, so a document without it stays valid and `schemaVersion` stays 1 (§14.7). */
    val obstructive: ObstructiveConfig = ObstructiveConfig(),
)

/** The break screen users can opt into (CR-10, D-88, D-98). [allowed] `false` turns the option off for everyone. */
@Serializable
data class ObstructiveConfig(
    val allowed: Boolean = true,
    val buttonDelaySeconds: Int = 10,
    val snoozeMinutes: Int = 5,
    val maxSnoozes: Int = 3,
    val returnAfterTakeBreakSeconds: Int = 60,
)

@Serializable
data class FocusConfig(
    val durationMinutes: Setting,
    val checkinMinutes: Setting,
    /** Added in M8 with defaults, like [ObstructiveConfig] (§14.8). Focus minutes before Done counts (D-113). */
    val minDoneMinutes: Int = 5,
    /** A session with no focus time for this long ends early by itself (D-114). */
    val abandonAfterMinutes: Int = 30,
)

@Serializable
data class BubblesConfig(val minGapSeconds: Int, val showSeconds: Int)

@Serializable
data class IdleConfig(val everyMinutes: Int)

@Serializable
data class HideConfig(val minutes: Int)

@Serializable
data class MoodConfig(val start: Int, val weights: MoodWeights, val bands: List<MoodBand>)

@Serializable
data class MoodWeights(
    @SerialName("focus_completed") val focusCompleted: Int,
    @SerialName("goal_met") val goalMet: Int,
    @SerialName("break_taken") val breakTaken: Int,
    @SerialName("doomscroll_10min") val doomscroll10Min: Int,
    @SerialName("limit_exceeded") val limitExceeded: Int,
    @SerialName("snoozed") val snoozed: Int,
)

@Serializable
data class MoodBand(val id: String, val from: Int, val to: Int)

@Serializable
data class Pet(
    val id: String,
    val name: String,
    val enabled: Boolean,
    /** Mood band id → emoji. */
    val faces: Map<String, String>,
)

@Serializable
data class Personality(val id: String, val name: String, val enabled: Boolean, val previewLine: String)

/** Fixed on purpose (D-51): adding a category is a breaking change and bumps [SCHEMA_VERSION]. */
@Serializable
enum class Category {
    @SerialName("social") SOCIAL,
    @SerialName("games") GAMES,
    @SerialName("entertainment") ENTERTAINMENT,
    @SerialName("productive") PRODUCTIVE,
    @SerialName("neutral") NEUTRAL,
}

/** The category's name in the JSON document, e.g. `social`. */
val Category.id: String get() = Category.serializer().descriptor.getElementName(ordinal)
