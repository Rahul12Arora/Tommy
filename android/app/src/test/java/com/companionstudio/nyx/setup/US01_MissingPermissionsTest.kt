package com.companionstudio.nyx.setup

import org.junit.Assert.assertEquals
import org.junit.Test

class US01_MissingPermissionsTest {

    @Test
    fun allGranted_nothingMissing() {
        assertEquals(emptyList<SetupPermission>(), PermissionStatus(SetupPermission.entries.toSet()).missing)
    }

    @Test
    fun noneGranted_allMissing_inScreenOrder() {
        assertEquals(
            listOf(SetupPermission.USAGE_ACCESS, SetupPermission.OVERLAY, SetupPermission.NOTIFICATIONS, SetupPermission.BATTERY),
            PermissionStatus(emptySet()).missing,
        )
    }

    @Test
    fun someGranted_onlyTheOthersMissing() {
        val status = PermissionStatus(setOf(SetupPermission.BATTERY, SetupPermission.USAGE_ACCESS))

        assertEquals(listOf(SetupPermission.OVERLAY, SetupPermission.NOTIFICATIONS), status.missing)
    }
}
