package com.companionstudio.nyx.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.pet.PetSettings
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun ChoosePetRoute(config: Flow<EffectiveConfig>, petSettings: PetSettings, onNext: () -> Unit) {
    val viewModel = viewModel { ChoosePetViewModel(config, petSettings) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    ChoosePetScreen(
        state = state,
        onPickPet = viewModel::pickPet,
        onPickPersonality = viewModel::pickPersonality,
        onContinue = {
            scope.launch {
                viewModel.save()
                onNext()
            }
        },
    )
}

/** S-04. [state] is `null` while loading. */
@Composable
fun ChoosePetScreen(
    state: ChoosePetState?,
    onPickPet: (String) -> Unit,
    onPickPersonality: (String) -> Unit,
    onContinue: () -> Unit,
) {
    SetupStep(stringResource(R.string.choose_pet_title)) {
        if (state == null) {
            CircularProgressIndicator()
            return@SetupStep
        }
        Text(stringResource(R.string.choose_pet_pets), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.pets.forEach { pet ->
                Choice(selected = pet.id == state.petId, onSelect = { onPickPet(pet.id) }) {
                    Text(pet.face, style = MaterialTheme.typography.displaySmall)
                    Text(pet.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(stringResource(R.string.choose_pet_personalities), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.personalities.forEach { personality ->
                Choice(selected = personality.id == state.personalityId, onSelect = { onPickPersonality(personality.id) }) {
                    Column {
                        Text(personality.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.choose_pet_preview_line, personality.previewLine))
                    }
                }
            }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.setup_continue))
        }
    }
}

/** A radio-style card: the whole card is the touch target, and TalkBack reads it as one selectable item. */
@Composable
private fun Choice(selected: Boolean, onSelect: () -> Unit, content: @Composable () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChoosePetScreenPreview() {
    NyxTheme {
        ChoosePetScreen(
            state = ChoosePetState(
                pets = listOf(PetOption("cat", "Cat", "😸"), PetOption("blob", "Blob", "😄")),
                personalities = listOf(PersonalityOption("cheerful", "Cheerful coach", "Let's make today a good one!")),
                petId = "cat",
                personalityId = "cheerful",
            ),
            onPickPet = {},
            onPickPersonality = {},
            onContinue = {},
        )
    }
}
