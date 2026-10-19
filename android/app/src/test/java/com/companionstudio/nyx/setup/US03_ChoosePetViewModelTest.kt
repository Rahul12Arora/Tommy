package com.companionstudio.nyx.setup

import com.companionstudio.nyx.config.ConfigResult
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.config.SharedFiles
import com.companionstudio.nyx.config.parseConfig
import com.companionstudio.nyx.data.createSettingsStore
import com.companionstudio.nyx.pet.PetChoice
import com.companionstudio.nyx.pet.PetSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class US03_ChoosePetViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    // Cat and Blob, Cheerful coach and Calm friend; mood starts at 70, in the "happy" band
    private val defaults = (parseConfig(SharedFiles.defaultConfig) as ConfigResult.Valid).config
    private val nothing = PetChoice(petId = null, personalityId = null)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun showsEnabledPetsWithTheirStartingFace_andPersonalitiesWithPreviewLines() {
        val state = choosePetState(defaults, stored = nothing, tapped = nothing)

        assertEquals(listOf(PetOption("cat", "Cat", "😸"), PetOption("blob", "Blob", "😄")), state.pets)
        assertEquals(listOf("cheerful", "calm"), state.personalities.map { it.id })
        assertEquals("Let's make today a good one!", state.personalities.first().previewLine)
    }

    @Test
    fun disabledItems_areNotOffered() {
        val document = defaults.copy(
            pets = defaults.pets.map { if (it.id == "cat") it.copy(enabled = false) else it },
            personalities = defaults.personalities.map { if (it.id == "calm") it.copy(enabled = false) else it },
        )

        val state = choosePetState(document, stored = nothing, tapped = nothing)

        assertEquals(listOf("blob"), state.pets.map { it.id })
        assertEquals(listOf("cheerful"), state.personalities.map { it.id })
    }

    @Test
    fun nothingStored_selectsTheFirstEnabled() {
        val state = choosePetState(defaults, stored = nothing, tapped = nothing)

        assertEquals("cat", state.petId)
        assertEquals("cheerful", state.personalityId)
    }

    @Test
    fun storedChoice_isSelected_unlessItWasDisabled() {
        val stored = PetChoice(petId = "blob", personalityId = "calm")
        assertEquals("blob" to "calm", choosePetState(defaults, stored, nothing).let { it.petId to it.personalityId })

        val blobDisabled = defaults.copy(pets = defaults.pets.map { if (it.id == "blob") it.copy(enabled = false) else it })
        assertEquals("cat", choosePetState(blobDisabled, stored, nothing).petId)
    }

    @Test
    fun tappedChoice_winsOverStored() {
        val state = choosePetState(defaults, stored = PetChoice("cat", "cheerful"), tapped = PetChoice("blob", null))

        assertEquals("blob", state.petId)
        assertEquals("cheerful", state.personalityId)
    }

    @Test
    fun save_storesWhatIsSelected() = runTest {
        val petSettings = PetSettings(createSettingsStore({ folder.root.resolve("settings.preferences_pb") }, onCorrupted = {}))
        val viewModel = ChoosePetViewModel(flowOf(EffectiveConfig(defaults, emptyMap())), petSettings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.state.filterNotNull().first()

        viewModel.pickPet("blob")
        viewModel.pickPersonality("calm")
        viewModel.state.first { it?.petId == "blob" && it.personalityId == "calm" }
        viewModel.save()

        assertEquals(PetChoice("blob", "calm"), petSettings.choice.first())
    }
}
