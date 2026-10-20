package com.companionstudio.nyx.pet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant

/** The pet and personality ids the user picked; `null` until they pick (§14.3). */
data class PetChoice(val petId: String?, val personalityId: String?)

/** Everything stored about the pet: the user's pick (§14.3), where it sits and until when it's hidden (§14.5). */
class PetSettings(private val store: DataStore<Preferences>) {
    val choice: Flow<PetChoice> = store.data.map { PetChoice(it[PET_ID], it[PERSONALITY_ID]) }

    val position: Flow<PetPosition> = store.data.map { prefs ->
        val side = Side.entries.firstOrNull { it.name.lowercase() == prefs[SIDE] } ?: PetPosition.DEFAULT.side
        PetPosition(side, (prefs[Y_FRACTION] ?: PetPosition.DEFAULT.yFraction).coerceIn(0f, 1f))
    }.distinctUntilChanged()

    /** `null` when the pet isn't hidden. */
    val hiddenUntil: Flow<Instant?> = store.data.map { prefs -> prefs[HIDDEN_UNTIL]?.let(Instant::ofEpochMilli) }
        .distinctUntilChanged()

    suspend fun save(petId: String, personalityId: String) {
        store.edit {
            it[PET_ID] = petId
            it[PERSONALITY_ID] = personalityId
        }
    }

    suspend fun savePosition(position: PetPosition) {
        store.edit {
            it[SIDE] = position.side.name.lowercase()
            it[Y_FRACTION] = position.yFraction
        }
    }

    /** `null` shows the pet again. */
    suspend fun setHiddenUntil(until: Instant?) {
        store.edit {
            if (until == null) it.remove(HIDDEN_UNTIL) else it[HIDDEN_UNTIL] = until.toEpochMilli()
        }
    }

    private companion object {
        val PET_ID = stringPreferencesKey("pet.petId")
        val PERSONALITY_ID = stringPreferencesKey("pet.personalityId")
        val SIDE = stringPreferencesKey("pet.side")
        val Y_FRACTION = floatPreferencesKey("pet.yFraction")
        val HIDDEN_UNTIL = longPreferencesKey("pet.hiddenUntil")
    }
}
