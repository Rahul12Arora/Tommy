package com.companionstudio.nyx.tracking

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A-20 / D-69 on a real device: the home-screen apps are found, and the Settings app isn't mistaken for one. */
@RunWith(AndroidJUnit4::class)
class US04_ExcludedAppsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val excluded = excludedPackages(context.packageManager, context.packageName)

    @Test
    fun theDefaultLauncher_NyxAndSystemUi_areExcluded() {
        val defaultHome = context.packageManager
            .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        assertNotNull(defaultHome)

        assertTrue("$defaultHome missing from $excluded", defaultHome in excluded)
        assertTrue(context.packageName in excluded)
        assertTrue("com.android.systemui" in excluded)
    }

    // Settings holds Android's fallback home screen (priority -1000); its time must still count
    @Test
    fun theSettingsApp_isNotExcluded() {
        assertFalse(excluded.toString(), "com.android.settings" in excluded)
    }
}
