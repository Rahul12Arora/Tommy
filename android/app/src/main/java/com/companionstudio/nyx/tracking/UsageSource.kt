package com.companionstudio.nyx.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import java.time.Instant

/** Where usage events come from: Android's event log, or a debug scenario (D-67). */
fun interface UsageSource {
    /** Events with `from ≤ at < to`, oldest first. */
    fun events(from: Instant, to: Instant): List<UsageEvent>
}

/** Android's usage event log. Needs usage access; without it the log reads as empty. */
class SystemUsageSource(private val usageStats: UsageStatsManager) : UsageSource {

    override fun events(from: Instant, to: Instant): List<UsageEvent> {
        val log = usageStats.queryEvents(from.toEpochMilli(), to.toEpochMilli()) ?: return emptyList()
        val event = UsageEvents.Event()
        return buildList {
            while (log.hasNextEvent()) {
                log.getNextEvent(event)
                val type = usageEventType(event.eventType) ?: continue
                add(UsageEvent(type, Instant.ofEpochMilli(event.timeStamp), event.packageName.orEmpty(), event.className.orEmpty()))
            }
        }
    }
}

/** Android's event type → ours; `null` for the types tracking ignores. */
fun usageEventType(androidType: Int): UsageEventType? = when (androidType) {
    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.APP_RESUMED
    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventType.APP_PAUSED
    UsageEvents.Event.SCREEN_INTERACTIVE -> UsageEventType.SCREEN_ON
    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_OFF
    UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventType.DEVICE_SHUTDOWN
    UsageEvents.Event.DEVICE_STARTUP -> UsageEventType.DEVICE_STARTUP
    else -> null
}
