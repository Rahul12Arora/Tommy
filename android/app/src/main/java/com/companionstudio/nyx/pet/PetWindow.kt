package com.companionstudio.nyx.pet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import com.companionstudio.nyx.focus.FocusStatus
import com.companionstudio.nyx.focus.focusShortText
import com.companionstudio.nyx.focus.focusSummaryText
import com.companionstudio.nyx.home.GoalProgress
import com.companionstudio.nyx.home.TodaySummary
import com.companionstudio.nyx.ui.theme.NyxTheme

/** The pet's size on screen; also its touch target (≥ 48 dp, ARCHITECTURE §9). */
val PET_SIZE = 56.dp

/** Space between the pet and a bubble or list beside it. */
val PANEL_GAP = 8.dp

/** How a bubble opens relative to the closed pet (D-71, D-84): towards the middle of the screen, never off it. */
data class PanelLayout(val side: Side, val below: Boolean, val maxHeight: Dp)

/** A speech bubble beside the closed pet. [onSnooze] is `null` except on a nag that can still be snoozed (US-10). */
class BubbleContent(val line: String, val onSnooze: (() -> Unit)?, val onDismiss: () -> Unit)

/**
 * The overlay window while the ring is closed: the pet (O-01) and maybe a speech bubble (O-02) beside it. [focus]: the
 * running session, for the pet's badge (D-118).
 */
@Composable
fun PetWindow(
    summary: TodaySummary?,
    glowing: Boolean,
    bubble: BubbleContent?,
    layout: PanelLayout,
    onPetClick: () -> Unit,
    focus: FocusStatus? = null,
) {
    NyxTheme {
        // Physical edges, like the window's gravity, whatever the text direction
        val alignment = if (layout.side == Side.LEFT) AbsoluteAlignment.Left else AbsoluteAlignment.Right
        Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(PANEL_GAP)) {
            val besidePet: @Composable () -> Unit = {
                bubble?.let { SpeechBubble(it.line, it.onSnooze, it.onDismiss, Modifier.heightIn(max = layout.maxHeight)) }
            }
            if (!layout.below) besidePet()
            PetFace(summary?.petFace.orEmpty(), glowing, panelOpen = false, onPetClick, focus)
            if (layout.below) besidePet()
        }
    }
}

/**
 * The pet's face; a red ring around it while a limit is nagging (§14.7), and during a focus session a badge with the
 * minutes, "!" while away (D-118). Both are also said to TalkBack.
 */
@Composable
fun PetFace(face: String, glowing: Boolean, panelOpen: Boolean, onClick: () -> Unit, focus: FocusStatus? = null) {
    val description = when {
        focus != null -> stringResource(R.string.pet_description_focus, focusSummaryText(focus))
        glowing -> stringResource(R.string.pet_description_over_limit)
        else -> stringResource(R.string.pet_description)
    }
    val action = stringResource(if (panelOpen) R.string.pet_close_summary else R.string.pet_open_summary)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        border = if (glowing) BorderStroke(3.dp, MaterialTheme.colorScheme.error) else null,
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
            // Inside the pet's circle, so the window keeps its size
            focus?.let { FocusBadge(focusShortText(it), Modifier.align(Alignment.BottomCenter)) }
        }
    }
}

@Composable
private fun FocusBadge(text: String, modifier: Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier.clearAndSetSemantics {},
    ) {
        Text(text, fontSize = with(LocalDensity.current) { 9.dp.toSp() }, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
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
        glowing = true,
        bubble = BubbleContent("That's 1 h of Social & scrolling today. Stretch break?", onSnooze = {}, onDismiss = {}),
        layout = PanelLayout(Side.RIGHT, below = true, maxHeight = 480.dp),
        onPetClick = {},
    )
}
