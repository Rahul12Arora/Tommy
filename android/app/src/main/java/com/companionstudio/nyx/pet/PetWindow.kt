package com.companionstudio.nyx.pet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.home.GoalProgress
import com.companionstudio.nyx.home.SummaryNumbers
import com.companionstudio.nyx.home.TodaySummary
import com.companionstudio.nyx.ui.theme.NyxTheme

/** The pet's size on screen; also its touch target (≥ 48 dp, ARCHITECTURE §9). */
val PET_SIZE = 56.dp

/** Space between the pet and its panel. */
val PANEL_GAP = 8.dp

/** How the panel or bubble opens relative to the pet (D-71): towards the middle of the screen, never off it. */
data class PanelLayout(val side: Side, val below: Boolean, val maxHeight: Dp)

/** O-03's buttons. [onFocus] is `null` when focus sessions are switched off in config. */
class PanelActions(val hideMinutes: Int, val onFocus: (() -> Unit)?, val onOpenApp: () -> Unit, val onHide: () -> Unit)

/** What shows beside the pet, away from its edge: the summary panel or a speech bubble, never both (D-84). */
sealed interface Beside {
    class Panel(val actions: PanelActions) : Beside

    class Bubble(val line: String, val onDismiss: () -> Unit) : Beside
}

/** Everything in the overlay window: the pet (O-01) and, beside it, the summary panel (O-03) or a speech bubble (O-02). */
@Composable
fun PetWindow(summary: TodaySummary?, beside: Beside?, layout: PanelLayout, onPetClick: () -> Unit) {
    NyxTheme {
        // Physical edges, like the window's gravity, whatever the text direction
        val alignment = if (layout.side == Side.LEFT) AbsoluteAlignment.Left else AbsoluteAlignment.Right
        Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
            val besidePet: @Composable () -> Unit = {
                val modifier = Modifier.heightIn(max = layout.maxHeight)
                when (beside) {
                    is Beside.Panel -> if (summary != null) SummaryPanel(summary, beside.actions, modifier)
                    is Beside.Bubble -> SpeechBubble(beside.line, onSnooze = null, onDismiss = beside.onDismiss, modifier)
                    null -> Unit
                }
            }
            if (!layout.below) besidePet()
            PetFace(summary?.petFace.orEmpty(), panelOpen = beside is Beside.Panel, onPetClick)
            if (layout.below) besidePet()
        }
    }
}

@Composable
fun PetFace(face: String, panelOpen: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.pet_description)
    val action = stringResource(if (panelOpen) R.string.pet_close_summary else R.string.pet_open_summary)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = Modifier
            .size(PET_SIZE)
            .clickable(onClickLabel = action, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Sized in dp, not sp: large system text must not push the face out of its circle
            val faceSize = with(LocalDensity.current) { 34.dp.toSp() }
            // TalkBack reads the description above, not the emoji's name
            Text(face, fontSize = faceSize, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
fun SummaryPanel(summary: TodaySummary, actions: PanelActions, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.widthIn(max = 320.dp),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryNumbers(summary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.onFocus?.let { Button(onClick = it) { Text(stringResource(R.string.home_start_focus)) } }
                OutlinedButton(onClick = actions.onOpenApp) { Text(stringResource(R.string.panel_open_app)) }
                OutlinedButton(onClick = actions.onHide) {
                    Text(stringResource(R.string.panel_hide, actions.hideMinutes))
                }
            }
        }
    }
}

@Preview
@Composable
private fun PetWindowPreview() {
    PetWindow(
        summary = TodaySummary(
            totalSeconds = 3 * 3600 + 12 * 60,
            productiveSeconds = 3600 + 5 * 60,
            doomscrollSeconds = 47 * 60,
            goals = listOf(GoalProgress(Category.SOCIAL, usedSeconds = 47 * 60, targetSeconds = 60 * 60)),
            petFace = "😸",
        ),
        beside = Beside.Panel(PanelActions(hideMinutes = 30, onFocus = {}, onOpenApp = {}, onHide = {})),
        layout = PanelLayout(Side.RIGHT, below = true, maxHeight = 480.dp),
        onPetClick = {},
    )
}
