package com.companionstudio.nyx.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.NyxConfig
import com.companionstudio.nyx.pet.PetChoice
import com.companionstudio.nyx.pet.PetSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PetOption(val id: String, val name: String, val face: String)
data class PersonalityOption(val id: String, val name: String, val previewLine: String)

/** What S-04 shows. The selected ids are always among the options. */
data class ChoosePetState(
    val pets: List<PetOption>,
    val personalities: List<PersonalityOption>,
    val petId: String,
    val personalityId: String,
)

/**
 * Only enabled pets and personalities can be picked (US-20). Selection: what the user tapped on this screen,
 * else what's stored if it's still enabled, else the first enabled one.
 */
fun choosePetState(document: NyxConfig, stored: PetChoice, tapped: PetChoice): ChoosePetState {
    // The face a new pet starts the day with; the validator guarantees a band and a face for every value
    val startBand = document.mood.bands.first { document.mood.start in it.from..it.to }.id
    val pets = document.pets.filter { it.enabled }.map { PetOption(it.id, it.name, it.faces.getValue(startBand)) }
    val personalities = document.personalities.filter { it.enabled }.map { PersonalityOption(it.id, it.name, it.previewLine) }
    return ChoosePetState(
        pets = pets,
        personalities = personalities,
        petId = pick(tapped.petId, stored.petId, pets.map { it.id }),
        personalityId = pick(tapped.personalityId, stored.personalityId, personalities.map { it.id }),
    )
}

private fun pick(tapped: String?, stored: String?, enabledIds: List<String>): String =
    tapped ?: stored?.takeIf { it in enabledIds } ?: enabledIds.first()

class ChoosePetViewModel(config: Flow<EffectiveConfig>, private val petSettings: PetSettings) : ViewModel() {
    private val tapped = MutableStateFlow(PetChoice(petId = null, personalityId = null))

    /** `null` while loading. */
    val state: StateFlow<ChoosePetState?> = combine(config, petSettings.choice, tapped) { effective, stored, tapped ->
        choosePetState(effective.document, stored, tapped)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun pickPet(id: String) {
        tapped.value = tapped.value.copy(petId = id)
    }

    fun pickPersonality(id: String) {
        tapped.value = tapped.value.copy(personalityId = id)
    }

    /** Stores what's selected on screen. Does nothing while loading. */
    suspend fun save() {
        val current = state.value ?: return
        petSettings.save(current.petId, current.personalityId)
    }
}
