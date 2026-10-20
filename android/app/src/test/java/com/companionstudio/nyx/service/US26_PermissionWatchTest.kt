package com.companionstudio.nyx.service

import com.companionstudio.nyx.setup.SetupPermission.BATTERY
import com.companionstudio.nyx.setup.SetupPermission.NOTIFICATIONS
import com.companionstudio.nyx.setup.SetupPermission.OVERLAY
import com.companionstudio.nyx.setup.SetupPermission.USAGE_ACCESS
import org.junit.Assert.assertEquals
import org.junit.Test

class US26_PermissionWatchTest {

    @Test
    fun newlyMissing_notifies() {
        assertEquals(PermissionAlert.Notify(listOf(USAGE_ACCESS)), permissionAlert(before = emptySet(), now = setOf(USAGE_ACCESS)))
    }

    @Test
    fun stillMissing_doesntRepeat() {
        assertEquals(PermissionAlert.None, permissionAlert(before = setOf(USAGE_ACCESS), now = setOf(USAGE_ACCESS)))
    }

    @Test
    fun anotherOneGoesMissing_notifiesWithBoth_inScreenOrder() {
        assertEquals(
            PermissionAlert.Notify(listOf(USAGE_ACCESS, OVERLAY)),
            permissionAlert(before = setOf(OVERLAY), now = setOf(OVERLAY, USAGE_ACCESS)),
        )
    }

    @Test
    fun grantedAgain_cancels() {
        assertEquals(PermissionAlert.Cancel, permissionAlert(before = setOf(OVERLAY), now = emptySet()))
    }

    @Test
    fun oneOfTwoGrantedAgain_keepsTheNotification() {
        assertEquals(PermissionAlert.None, permissionAlert(before = setOf(OVERLAY, USAGE_ACCESS), now = setOf(OVERLAY)))
    }

    @Test
    fun missingAtServiceStart_notifiesOnce() {
        val first = permissionAlert(before = null, now = setOf(OVERLAY))
        val second = permissionAlert(before = setOf(OVERLAY), now = setOf(OVERLAY))

        assertEquals(PermissionAlert.Notify(listOf(OVERLAY)), first)
        assertEquals(PermissionAlert.None, second)
    }

    @Test
    fun allGrantedAtServiceStart_clearsALeftoverNotification() {
        assertEquals(PermissionAlert.Cancel, permissionAlert(before = null, now = emptySet()))
        assertEquals(PermissionAlert.None, permissionAlert(before = emptySet(), now = emptySet()))
    }

    @Test
    fun D66_onlyUsageAccessAndOverlayAreWatched() {
        assertEquals(PermissionAlert.None, permissionAlert(before = emptySet(), now = setOf(NOTIFICATIONS, BATTERY)))
    }
}
