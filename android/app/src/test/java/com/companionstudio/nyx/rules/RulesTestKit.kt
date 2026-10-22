package com.companionstudio.nyx.rules

import com.companionstudio.nyx.config.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Placeholder names without Android resources. */
object TestLabels : NagLabels {
    override fun app(packageName: String) = "App($packageName)"

    override fun category(category: Category?) = category?.name ?: "screen time"
}

/** The event log in memory, in insertion order like the real DAO's id order. */
class FakePetEventDao : PetEventDao {
    val events = mutableListOf<PetEvent>()
    val deletedBefore = mutableListOf<String>()

    fun log(type: PetEventType, subject: String? = null, day: String = "2026-10-21") {
        events += PetEvent(id = events.size + 1L, day = day, atMs = 0, type = type, subject = subject)
    }

    override suspend fun insertAll(events: List<PetEvent>) {
        events.forEach { this.events += it.copy(id = this.events.size + 1L) }
    }

    // Insertion order, like the real DAO's id order
    override suspend fun eventsOn(day: String) = events.filter { it.day == day }

    override fun observeDay(day: String): Flow<List<PetEvent>> = flowOf(events.filter { it.day == day })

    override suspend fun deleteBefore(day: String) {
        deletedBefore += day
    }

    override suspend fun clearAll() = events.clear()
}

/** A clock tests move by hand. */
class TestClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}
