package com.companionstudio.nyx.tracking

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R-10 on a real device: the manifest's `<queries>` entry for launcher apps is enough to list other apps.
 * Without it, Android 11+ would hide them and the list would hold only system packages visible to everyone.
 */
@RunWith(AndroidJUnit4::class)
class US04_InstalledAppsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val apps = runBlocking { InstalledApps(context.packageManager, context.packageName).list() }

    // Settings is no proof: it runs as the system user, which every app can see. Chrome is an ordinary app that
    // Android 11+ hides unless `<queries>` asks for it, and it's on the emulator image and the Pixel 7a.
    // Checked: with the `<queries>` entry removed, this test fails
    @Test
    fun listsOtherAppsWithALauncherIcon() {
        assertTrue("Chrome is missing from ${apps.map { it.packageName }}", apps.any { it.packageName == "com.android.chrome" })
        assertTrue(apps.all { it.label.isNotBlank() })
    }

    @Test
    fun leavesOutNyx_andListsEachPackageOnce() {
        assertFalse(apps.any { it.packageName == context.packageName })
        assertTrue(apps.map { it.packageName }.let { it.size == it.toSet().size })
    }
}
