package com.companionstudio.nyx.tracking

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

private const val SYSTEM_UI = "com.android.systemui"

/**
 * Apps whose time never counts (A-20, D-69): Nyx itself, System UI and every home-screen app.
 * Android's fallback home screen is left out of that list: it lives in the Settings app (seen on the API 37
 * emulator and a Pixel 7a), and Settings time should count. It's the only home handler with a negative priority.
 */
fun excludedPackages(packageManager: PackageManager, ownPackage: String): Set<String> =
    setOf(ownPackage, SYSTEM_UI) + homeApps(packageManager)

/** Needs the HOME `<intent>` in the manifest's `<queries>`, or third-party launchers aren't visible. */
fun homeApps(packageManager: PackageManager): Set<String> =
    homeActivities(packageManager)
        .filter { it.priority >= 0 }
        .map { it.activityInfo.packageName }
        .toSet()

private fun homeActivities(packageManager: PackageManager): List<ResolveInfo> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, 0)
    }
}
