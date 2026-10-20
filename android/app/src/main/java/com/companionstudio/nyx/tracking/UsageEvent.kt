package com.companionstudio.nyx.tracking

import java.time.Instant

/** The usage events tracking needs, in our own terms (§14.4). Android's other event types are ignored. */
enum class UsageEventType { APP_RESUMED, APP_PAUSED, SCREEN_ON, SCREEN_OFF, DEVICE_SHUTDOWN, DEVICE_STARTUP }

/** [packageName] and [className] are blank for screen and device events. */
data class UsageEvent(
    val type: UsageEventType,
    val at: Instant,
    val packageName: String = "",
    val className: String = "",
)
