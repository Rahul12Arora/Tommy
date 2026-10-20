package com.companionstudio.nyx.service

import com.companionstudio.nyx.setup.SetupPermission

/** Without these the pet can't work at all (D-66). Notifications and battery are asked for, but not watched. */
val WATCHED_PERMISSIONS = listOf(SetupPermission.USAGE_ACCESS, SetupPermission.OVERLAY)

sealed interface PermissionAlert {
    /** Post (or update) N-03 listing [missing], in S-03's order. */
    data class Notify(val missing: List<SetupPermission>) : PermissionAlert
    data object Cancel : PermissionAlert
    data object None : PermissionAlert
}

/**
 * What to do with N-03, given which watched permissions were missing at the previous check ([before]; `null` at
 * service start) and which are missing now. Only a newly missing one notifies, so the notification isn't repeated
 * at every tick; a service start counts as "all granted before".
 */
fun permissionAlert(before: Set<SetupPermission>?, now: Set<SetupPermission>): PermissionAlert {
    val watchedNow = now.intersect(WATCHED_PERMISSIONS.toSet())
    val watchedBefore = before.orEmpty().intersect(WATCHED_PERMISSIONS.toSet())
    return when {
        (watchedNow - watchedBefore).isNotEmpty() -> PermissionAlert.Notify(WATCHED_PERMISSIONS.filter { it in watchedNow })
        // At service start this also clears an N-03 left over from before, once all is well
        watchedNow.isEmpty() && (before == null || watchedBefore.isNotEmpty()) -> PermissionAlert.Cancel
        else -> PermissionAlert.None
    }
}
