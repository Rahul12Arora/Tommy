package com.companionstudio.nyx.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.companionstudio.nyx.R
import com.companionstudio.nyx.ui.theme.NyxTheme

/** S-03 wired to the system: re-checks on every resume and opens the right Settings screen per row. */
@Composable
fun PermissionsRoute(permissionChecks: PermissionChecks, onNext: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(permissionChecks.current()) }
    LifecycleResumeEffect(Unit) {
        status = permissionChecks.current()
        onPauseOrDispose {}
    }
    // After a denial the system may stop showing the dialog, so the next tap goes to Settings instead
    var notificationRequestDenied by rememberSaveable { mutableStateOf(false) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) notificationRequestDenied = true
        status = permissionChecks.current()
    }
    val brand = remember { phoneBrand() }

    PermissionsScreen(
        status = status,
        brand = brand,
        onGrant = { permission ->
            val askWithDialog = permission == SetupPermission.NOTIFICATIONS &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationRequestDenied
            if (askWithDialog) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openSettings(permissionChecks.settingsIntent(permission), fallback = permissionChecks.appDetailsIntent())
            }
        },
        onOpenAppSettings = { context.startActivity(permissionChecks.appDetailsIntent()) },
        onNext = onNext,
    )
}

/** S-03. [onNext] runs straight away when everything is granted, otherwise after "Continue anyway" (D-59). */
@Composable
fun PermissionsScreen(
    status: PermissionStatus,
    brand: PhoneBrand,
    onGrant: (SetupPermission) -> Unit,
    onOpenAppSettings: () -> Unit,
    onNext: () -> Unit,
) {
    var showMissing by rememberSaveable { mutableStateOf(false) }
    SetupStep(stringResource(R.string.permissions_title)) {
        Text(stringResource(R.string.permissions_intro))
        for (permission in SetupPermission.entries) {
            PermissionRow(permission, granted = permission in status.granted, onGrant = { onGrant(permission) }) {
                if (permission == SetupPermission.BATTERY) BrandSteps(brand, onOpenAppSettings)
            }
        }
        Button(
            onClick = { if (status.missing.isEmpty()) onNext() else showMissing = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.permissions_continue))
        }
    }
    if (showMissing) {
        MissingPermissionsDialog(
            missing = status.missing,
            onContinue = {
                showMissing = false
                onNext()
            },
            onGoBack = { showMissing = false },
        )
    }
}

@Composable
private fun PermissionRow(
    permission: SetupPermission,
    granted: Boolean,
    onGrant: () -> Unit,
    extra: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(permission.titleRes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(permission.reasonRes))
            // The state is spelled out, never shown by colour or an icon alone (§9)
            Text(
                stringResource(if (granted) R.string.permission_granted else R.string.permission_not_granted),
                style = MaterialTheme.typography.labelLarge,
            )
            if (!granted) {
                OutlinedButton(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.permission_open))
                }
            }
            extra()
        }
    }
}

/** Steps the system can't report back on, so they have no tick (D-60). Unchecked on these brands' phones (A-29). */
@Composable
private fun BrandSteps(brand: PhoneBrand, onOpenAppSettings: () -> Unit) {
    Text(stringResource(R.string.battery_brand_intro), style = MaterialTheme.typography.titleSmall)
    stringArrayResource(brand.stepsRes).forEachIndexed { index, step -> Text("${index + 1}. $step") }
    OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.battery_open_app_settings))
    }
}

/** US-01: what won't work for each step that was skipped. */
@Composable
private fun MissingPermissionsDialog(missing: List<SetupPermission>, onContinue: () -> Unit, onGoBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onGoBack,
        title = { Text(stringResource(R.string.permissions_missing_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                missing.forEach { Text("• " + stringResource(it.withoutItRes)) }
                Text(stringResource(R.string.permissions_missing_later))
            }
        },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.permissions_continue_anyway)) } },
        dismissButton = { TextButton(onClick = onGoBack) { Text(stringResource(R.string.permissions_go_back)) } },
    )
}

private val SetupPermission.titleRes: Int
    @StringRes get() = when (this) {
        SetupPermission.USAGE_ACCESS -> R.string.permission_usage_title
        SetupPermission.OVERLAY -> R.string.permission_overlay_title
        SetupPermission.NOTIFICATIONS -> R.string.permission_notifications_title
        SetupPermission.BATTERY -> R.string.permission_battery_title
    }

private val SetupPermission.reasonRes: Int
    @StringRes get() = when (this) {
        SetupPermission.USAGE_ACCESS -> R.string.permission_usage_reason
        SetupPermission.OVERLAY -> R.string.permission_overlay_reason
        SetupPermission.NOTIFICATIONS -> R.string.permission_notifications_reason
        SetupPermission.BATTERY -> R.string.permission_battery_reason
    }

// The "Without it" column of ARCHITECTURE §10
private val SetupPermission.withoutItRes: Int
    @StringRes get() = when (this) {
        SetupPermission.USAGE_ACCESS -> R.string.permission_usage_without
        SetupPermission.OVERLAY -> R.string.permission_overlay_without
        SetupPermission.NOTIFICATIONS -> R.string.permission_notifications_without
        SetupPermission.BATTERY -> R.string.permission_battery_without
    }

private val PhoneBrand.stepsRes: Int
    @ArrayRes get() = when (this) {
        PhoneBrand.XIAOMI -> R.array.battery_steps_xiaomi
        PhoneBrand.SAMSUNG -> R.array.battery_steps_samsung
        PhoneBrand.VIVO -> R.array.battery_steps_vivo
        PhoneBrand.OPPO -> R.array.battery_steps_oppo
        PhoneBrand.OTHER -> R.array.battery_steps_other
    }

private fun Context.openSettings(intent: Intent, fallback: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Some brands leave out a Settings screen; the app's own page always exists
        Log.w("Nyx", "No screen for ${intent.action}; opening the app's settings page", e)
        startActivity(fallback)
    }
}

@Preview(showBackground = true)
@Composable
private fun PermissionsScreenPreview() {
    NyxTheme {
        PermissionsScreen(
            status = PermissionStatus(setOf(SetupPermission.USAGE_ACCESS)),
            brand = PhoneBrand.SAMSUNG,
            onGrant = {},
            onOpenAppSettings = {},
            onNext = {},
        )
    }
}
