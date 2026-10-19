package com.companionstudio.nyx.pet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The pet and personality ids the user picked; `null` until they pick (§14.3). */
data class PetChoice(val petId: String?, val personalityId: String?)

class PetSettings(private val store: DataStore<Preferences>) {
    val choice: Flow<PetChoice> = store.data.map { PetChoice(it[PET_ID], it[PERSONALITY_ID]) }

    suspend fun save(petId: String, personalityId: String) {
        store.edit {
            it[PET_ID] = petId
            it[PERSONALITY_ID] = personalityId
        }
    }

    private companion object {
        val PET_ID = stringPreferencesKey("pet.petId")
        val PERSONALITY_ID = stringPreferencesKey("pet.personalityId")
    }
}
