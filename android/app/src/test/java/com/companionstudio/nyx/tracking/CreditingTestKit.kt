package com.companionstudio.nyx.tracking

import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.data.DayClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Shared helpers for the crediting tests: UTC, apps with fixed categories, Social and Games in the stretch. */
object CreditingTestKit {
    const val INSTAGRAM = "com.instagram.android"
    const val CHROME = "com.android.chrome"
    const val DOCS = "com.google.android.apps.docs"
    const val LAUNCHER = "com.google.android.apps.nexuslauncher"

    val categories = mapOf(INSTAGRAM to Category.SOCIAL, CHROME to Category.NEUTRAL, DOCS to Category.PRODUCTIVE)

    private val dayClock = DayClock(zone = { ZoneOffset.UTC })

    /** 21 Oct 2026 at [time] UTC, e.g. `t("10:05")`. */
    fun t(time: String, date: String = "2026-10-21"): Instant = Instant.parse("${date}T$time:00Z")

    fun day(text: String): LocalDate = LocalDate.parse(text)

    fun rules(
        creditFrom: Instant = Instant.EPOCH,
        resetHour: Int = 0,
        categoryOf: Map<String, Category> = categories,
        breakMinutes: Long = 5,
    ) = CreditRules(
        creditFrom = creditFrom,
        dayOf = { dayClock.dayKeyAt(it, resetHour) },
        nextDayStart = { dayClock.nextDayStart(it, resetHour) },
        isCounted = { it != LAUNCHER },
        categoryOf = { categoryOf.getValue(it) },
        stretchCategories = setOf(Category.SOCIAL, Category.GAMES),
        breakMs = breakMinutes * 60_000,
    )

    fun resumed(packageName: String, at: Instant, activity: String = "Main") =
        UsageEvent(UsageEventType.APP_RESUMED, at, packageName, activity)

    fun paused(packageName: String, at: Instant, activity: String = "Main") =
        UsageEvent(UsageEventType.APP_PAUSED, at, packageName, activity)

    fun screenOff(at: Instant) = UsageEvent(UsageEventType.SCREEN_OFF, at)

    fun screenOn(at: Instant) = UsageEvent(UsageEventType.SCREEN_ON, at)

    /** Credited seconds by (day, package, category). */
    fun Advanced.seconds(): Map<Triple<LocalDate, String, Category>, Long> =
        credits.associate { Triple(it.day, it.packageName, it.category) to it.durationMs / 1000 }
}
