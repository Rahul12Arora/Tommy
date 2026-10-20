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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.pet.PetVisibility
import com.companionstudio.nyx.ui.theme.NyxTheme

@Composable
fun HomeRoute(
    summaryRepository: SummaryRepository,
    petVisibility: PetVisibility,
    onFocus: () -> Unit,
    onTrends: () -> Unit,
    onSettings: () -> Unit,
) {
    val viewModel = viewModel { HomeViewModel(summaryRepository, petVisibility) }
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val petHidden by viewModel.petHidden.collectAsStateWithLifecycle()
    HomeScreen(summary, onShowPet = if (petHidden) ({ viewModel.showPet() }) else null, onFocus, onTrends, onSettings)
}

/** S-08. [summary] is `null` while loading. [onShowPet] is `null` unless the pet is hidden (US-29). */
@Composable
fun HomeScreen(
    summary: TodaySummary?,
    onShowPet: (() -> Unit)?,
    onFocus: () -> Unit,
    onTrends: () -> Unit,
    onSettings: () -> Unit,
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
            if (onShowPet != null) {
                Text(stringResource(R.string.home_pet_hidden))
                OutlinedButton(onClick = onShowPet) { Text(stringResource(R.string.show_pet)) }
            }
            SummaryNumbers(summary)
            Button(onClick = onFocus, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_start_focus))
            }
            OutlinedButton(onClick = onTrends, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_trends))
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_settings))
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
            onFocus = {},
            onTrends = {},
            onSettings = {},
        )
    }
}
