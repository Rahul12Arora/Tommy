package com.companionstudio.nyx.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companionstudio.nyx.R
import com.companionstudio.nyx.stats.StatsSettings
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.launch

@Composable
fun StatsOptInRoute(statsSettings: StatsSettings, setupSettings: SetupSettings, onFinished: () -> Unit) {
    val viewModel = viewModel { StatsOptInViewModel(statsSettings, setupSettings) }
    val optIn by viewModel.optIn.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    StatsOptInScreen(
        optIn = optIn,
        onOptInChange = { viewModel.setOptIn(it) },
        onFinish = {
            scope.launch {
                viewModel.finishSetup()
                onFinished()
            }
        },
    )
}

/** S-07. [optIn] is `null` while loading. What's shared is exactly A-09; nothing is sent before M14. */
@Composable
fun StatsOptInScreen(optIn: Boolean?, onOptInChange: (Boolean) -> Unit, onFinish: () -> Unit) {
    SetupStep(stringResource(R.string.stats_title)) {
        Text(stringResource(R.string.stats_intro))
        Text(stringResource(R.string.stats_shared_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.stats_shared))
        Text(stringResource(R.string.stats_never_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.stats_never))
        if (optIn == null) {
            CircularProgressIndicator()
            return@SetupStep
        }
        // The whole row toggles, so the label and the switch are one target for touch and TalkBack
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = optIn, onValueChange = onOptInChange, role = Role.Switch),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.stats_switch), modifier = Modifier.weight(1f))
            Switch(checked = optIn, onCheckedChange = null)
        }
        Text(stringResource(R.string.stats_change_later), style = MaterialTheme.typography.bodySmall)
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.stats_finish))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsOptInScreenPreview() {
    NyxTheme { StatsOptInScreen(optIn = false, onOptInChange = {}, onFinish = {}) }
}
