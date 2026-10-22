package com.companionstudio.nyx.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** TalkBack users jump between headings, so sub-headings are marked as such, not only styled (REQUIREMENTS accessibility). */
@RunWith(AndroidJUnit4::class)
class SetupSubheadingsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun choosePet_petAndPersonalityAreHeadings() {
        composeRule.setContent {
            ChoosePetScreen(
                state = ChoosePetState(
                    pets = listOf(PetOption("cat", "Cat", "😸")),
                    personalities = listOf(PersonalityOption("cheerful", "Cheerful coach", "Let's make today a good one!")),
                    petId = "cat",
                    personalityId = "cheerful",
                ),
                onPickPet = {},
                onPickPersonality = {},
                onContinue = {},
            )
        }

        composeRule.onAllNodes(isHeading() and (hasText("Pet") or hasText("Personality"))).assertCountEquals(2)
    }

    @Test
    fun statsOptIn_sharedAndNeverSharedAreHeadings() {
        composeRule.setContent { StatsOptInScreen(optIn = false, onOptInChange = {}, onFinish = {}) }

        composeRule.onAllNodes(isHeading() and (hasText("What's shared") or hasText("Never shared"))).assertCountEquals(2)
    }
}
