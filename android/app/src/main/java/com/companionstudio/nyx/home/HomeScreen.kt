package com.companionstudio.nyx.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.focus.FocusRepository
import com.companionstudio.nyx.focus.FocusSessionCard
import com.companionstudio.nyx.focus.FocusStatus
import com.companionstudio.nyx.pet.PetVisibility
import com.companionstudio.nyx.service.WATCHED_PERMISSIONS
import com.companionstudio.nyx.setup.PermissionChecks
import com.companionstudio.nyx.setup.SetupPermission
import com.companionstudio.nyx.setup.withoutItRes
import com.companionstudio.nyx.ui.theme.NyxTheme

/** Home's focus area (§14.8): the running session with Done and End, or Start ([onStart] `null` hides it, D-120). */
class HomeFocus(val running: FocusStatus?, val onStart: (() -> Unit)?, val onDone: () -> Unit = {}, val onEnd: () -> Unit = {})

@Composable
fun HomeRoute(
    summaryRepository: SummaryRepository,
    petVisibility: PetVisibility,
    focusRepository: FocusRepository,
    permissionChecks: PermissionChecks,
    onFixPermissions: () -> Unit,
    onFocus: () -> Unit,
    onTrends: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    val viewModel = viewModel { HomeViewModel(summaryRepository, petVisibility, focusRepository) }
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val petHidden by viewModel.petHidden.collectAsStateWithLifecycle()
    val focusState by viewModel.focus.collectAsStateWithLifecycle()
    // Re-read on every resume, like S-03, so the card goes once the user is back from Settings
    var missing by remember { mutableStateOf(emptyList<SetupPermission>()) }
    LifecycleResumeEffect(Unit) {
        missing = permissionChecks.current().missing.filter { it in WATCHED_PERMISSIONS }
        onPauseOrDispose {}
    }
    val focus = HomeFocus(
        running = focusState.running,
        onStart = if (focusState.enabled) onFocus else null,
        onDone = { viewModel.finishFocus(done = true) },
        onEnd = { viewModel.finishFocus(done = false) },
    )
    HomeScreen(
        summary,
        onShowPet = if (petHidden) ({ viewModel.showPet() }) else null,
        focus,
        onTrends,
        onSettings,
        permissions = HomePermissions(missing, onFixPermissions),
    )
}

/** Watched permissions that are off (US-26), with a way to S-03. Without it Home would show 0 min and no reason (E-01). */
class HomePermissions(val missing: List<SetupPermission>, val onFix: () -> Unit)

/**
 * S-08. [summary] is `null` while loading. [onShowPet] is `null` unless the pet is hidden (US-29). [onTrends] is `null`
 * when trends are switched off in config (D-153).
 */
@Composable
fun HomeScreen(
    summary: TodaySummary?,
    onShowPet: (() -> Unit)?,
    focus: HomeFocus,
    onTrends: (() -> Unit)?,
    onSettings: () -> Unit,
    permissions: HomePermissions? = null,
) {
    Scaffold { innerPadding ->
        if (summary == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(summary.petFace, style = MaterialTheme.typography.displayLarge)
            if (permissions != null && permissions.missing.isNotEmpty()) MissingPermissionsCard(permissions)
            if (onShowPet != null) {
                Text(stringResource(R.string.home_pet_hidden))
                OutlinedButton(onClick = onShowPet) { Text(stringResource(R.string.show_pet)) }
            }
            SummaryNumbers(summary)
            val running = focus.running
            if (running != null) {
                FocusSessionCard(running, focus.onDone, focus.onEnd, Modifier.fillMaxWidth())
            } else {
                focus.onStart?.let {
                    Button(onClick = it, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_start_focus)) }
                }
            }
            onTrends?.let {
                OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_trends)) }
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_settings))
            }
        }
    }
}

@Composable
private fun MissingPermissionsCard(permissions: HomePermissions) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.home_permissions_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            permissions.missing.forEach { Text("• " + stringResource(it.withoutItRes)) }
            Button(onClick = permissions.onFix, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_permissions_fix))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NyxTheme {
        HomeScreen(
            TodaySummary(
                totalSeconds = 3 * 3600 + 12 * 60,
                productiveSeconds = 3600 + 5 * 60,
                doomscrollSeconds = 47 * 60,
                goals = listOf(GoalProgress(Category.SOCIAL, usedSeconds = 47 * 60, targetSeconds = 60 * 60)),
                petFace = "😸",
            ),
            onShowPet = null,
            focus = HomeFocus(running = null, onStart = {}),
            onTrends = {},
            onSettings = {},
        )
    }
}
