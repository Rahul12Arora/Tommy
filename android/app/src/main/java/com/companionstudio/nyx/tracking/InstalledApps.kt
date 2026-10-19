package com.companionstudio.nyx.tracking

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app with a launcher icon. [androidCategory] is `ApplicationInfo.category`. */
data class InstalledApp(val packageName: String, val label: String, val androidCategory: Int)

/**
 * The apps the user can see in their launcher, found through the `<queries>` entry in the manifest
 * instead of the restricted QUERY_ALL_PACKAGES permission (R-10). Nyx itself is left out (A-30).
 */
class InstalledApps(private val packageManager: PackageManager, private val ownPackage: String) {

    suspend fun list(): List<InstalledApp> = withContext(Dispatchers.IO) {
        launcherActivities()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ownPackage }
            .map { InstalledApp(it.packageName, it.loadLabel(packageManager).toString(), it.category) }
            .sortedBy { it.label.lowercase() }
    }

    /** `null` if the app was uninstalled since the list was read. Call off the main thread. */
    fun icon(packageName: String): Drawable? = try {
        packageManager.getApplicationIcon(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
        null // the row just shows no icon; the next list won't contain the app
    }

    /** Android's category for any app Nyx can see; `CATEGORY_UNDEFINED` (→ Neutral) for one it can't. */
    fun androidCategory(packageName: String): Int = try {
        packageManager.getApplicationInfo(packageName, 0).category
    } catch (_: PackageManager.NameNotFoundException) {
        ApplicationInfo.CATEGORY_UNDEFINED
    }

    private fun launcherActivities(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }
}
