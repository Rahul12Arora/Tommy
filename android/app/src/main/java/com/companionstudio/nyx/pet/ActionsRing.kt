package com.companionstudio.nyx.pet

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.focus.FocusPhase
import com.companionstudio.nyx.focus.focusShortText
import com.companionstudio.nyx.focus.focusSummaryText
import com.companionstudio.nyx.home.GoalProgress
import com.companionstudio.nyx.home.TodaySummary
import com.companionstudio.nyx.home.formatDuration
import com.companionstudio.nyx.home.formatShort
import com.companionstudio.nyx.rules.RingStatus
import com.companionstudio.nyx.ui.labelRes
import com.companionstudio.nyx.ui.theme.NyxTheme

/**
 * The ring's actions (O-03). [onFocus] and [onTrends] are `null` when focus sessions or trends are switched off in
 * config. [onEndFocus]: Done (`true`) or End on the running session's card (D-118).
 */
class RingActions(
    val hideMinutes: Int,
    val onFocus: (() -> Unit)?,
    val onOpenApp: () -> Unit,
    val onHide: () -> Unit,
    val onEndFocus: (done: Boolean) -> Unit = {},
    val onTrends: (() -> Unit)? = null,
)

/**
 * One slot: an emoji, an optional short value, and the full text the caption and TalkBack show. [onClick] `null` is a
 * value slot: tapping it shows its caption. [alert]: over a limit, shown by "!" as well as colour (§9).
 */
class RingSlot(val glyph: String, val value: String?, val caption: String, val onClick: (() -> Unit)?, val alert: Boolean = false)

/**
 * O-03, the actions ring (CR-09, §14.7): the pet on its edge, the slots on the half circle facing the middle of the
 * screen, a caption for the last slot pressed, and the Limits list in the ring's place when asked for. Taps on the empty
 * space close it (D-92), like taps outside the window.
 */
@Composable
fun ActionsRing(
    summary: TodaySummary,
    status: RingStatus,
    glowing: Boolean,
    actions: RingActions,
    side: Side,
    maxWidth: Dp,
    onPetClick: () -> Unit,
    onClose: () -> Unit,
) {
    var selected by remember { mutableStateOf<Int?>(null) }
    var showLimits by remember { mutableStateOf(false) }
    var showFocus by remember { mutableStateOf(false) }
    val slots = ringSlots(summary, status, actions, onLimits = { showLimits = true }, onFocusCard = { showFocus = true })
    val focus = status.focus
    val geometry = ringGeometry(side, slots.size, maxWidth.value)
    Box(Modifier.size(geometry.width.dp, geometry.height.dp)) {
        // Behind everything: only taps that miss every slot land here
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) })
        if (showLimits) {
            LimitsList(summary, status, side, geometry, onBack = { showLimits = false })
        } else if (showFocus && focus != null) {
            RingFocusCard(focus, side, geometry, onEnd = actions.onEndFocus, onBack = { showFocus = false })
        } else {
            slots.forEachIndexed { i, slot ->
                val center = geometry.slots[i]
                RingSlotButton(
                    slot,
                    selected = selected == i,
                    onSelect = { selected = i },
                    modifier = Modifier.offset((center.x - RING_SLOT_DP / 2).dp, (center.y - RING_SLOT_DP / 2).dp),
                )
            }
            selected?.let { i -> Caption(slots[i].caption, side, geometry) }
        }
        Box(Modifier.offset((geometry.pet.x - PET_SIZE.value / 2).dp, (geometry.pet.y - PET_SIZE.value / 2).dp)) {
            PetFace(summary.petFace, glowing, panelOpen = true, onClick = onPetClick, focus = focus)
        }
    }
}

@Composable
private fun ringSlots(summary: TodaySummary, status: RingStatus, actions: RingActions, onLimits: () -> Unit, onFocusCard: () -> Unit): List<RingSlot> {
    val worst = summary.goals.maxByOrNull { it.usedSeconds.toDouble() / it.targetSeconds }
    val continuous = formatDuration(status.continuousMs / 1000)
    return buildList {
        // Total today opens Trends: US-18's way in from the ring, without an 8th slot (D-153)
        val total = stringResource(R.string.ring_total, formatDuration(summary.totalSeconds))
        val totalCaption = if (actions.onTrends == null) total else stringResource(R.string.ring_total_trends, total)
        add(RingSlot("📱", formatShort(summary.totalSeconds), totalCaption, actions.onTrends))
        add(
            RingSlot(
                "🌱",
                "${formatShort(summary.productiveSeconds)}·${formatShort(summary.doomscrollSeconds)}",
                stringResource(R.string.ring_balance, formatDuration(summary.productiveSeconds), formatDuration(summary.doomscrollSeconds)),
                null,
            ),
        )
        add(
            if (worst == null) {
                RingSlot("🚦", "—", stringResource(R.string.ring_limits_none), onLimits)
            } else {
                val shortValue = "${worst.usedSeconds / 60}/${worst.targetSeconds / 60}m"
                RingSlot("🚦", if (worst.isOver) "!$shortValue" else shortValue, goalCaption(worst), onLimits, alert = worst.isOver)
            },
        )
        val breakAt = status.breakAfterMinutes
        add(
            RingSlot(
                "⏳",
                formatShort(status.continuousMs / 1000),
                if (breakAt == null) {
                    stringResource(R.string.ring_continuous, continuous)
                } else {
                    stringResource(R.string.ring_continuous_reminder, continuous, formatDuration(breakAt * 60L))
                },
                null,
            ),
        )
        // A running session's timer takes Start focus's slot (CR-09), even if focus was switched off since (D-120)
        val focus = status.focus
        if (focus != null) {
            val caption = stringResource(R.string.focus_ring_caption, focusSummaryText(focus))
            add(RingSlot("⏱", focusShortText(focus), caption, onFocusCard, alert = focus.phase == FocusPhase.DRIFTING))
        } else {
            actions.onFocus?.let { add(RingSlot("🎯", null, stringResource(R.string.home_start_focus), it)) }
        }
        add(RingSlot("🏠", null, stringResource(R.string.panel_open_app), actions.onOpenApp))
        add(RingSlot("🙈", "${actions.hideMinutes}m", stringResource(R.string.panel_hide, actions.hideMinutes), actions.onHide))
    }
}

@Composable
private fun goalCaption(goal: GoalProgress): String {
    val name = if (goal.category == null) stringResource(R.string.home_goal_total) else stringResource(goal.category.labelRes)
    val used = formatDuration(goal.usedSeconds)
    val target = formatDuration(goal.targetSeconds)
    return stringResource(if (goal.isOver) R.string.ring_limit_over else R.string.ring_limit, name, used, target)
}

@Composable
private fun RingSlotButton(slot: RingSlot, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    // The pressed slot turns dark, like the reference's ring (CR-09)
    Surface(
        shape = CircleShape,
        color = if (selected) colors.inverseSurface else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.inverseOnSurface else colors.onSurface,
        shadowElevation = 4.dp,
        modifier = modifier
            .size(RING_SLOT_DP.dp)
            .combinedClickable(role = Role.Button, onLongClick = onSelect, onClick = { slot.onClick?.invoke() ?: onSelect() })
            .semantics { contentDescription = slot.caption },
    ) {
        // Sized in dp, like the pet's face: large text mustn't burst the circle. The caption has the full text
        val density = LocalDensity.current
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            Text(slot.glyph, fontSize = with(density) { 20.dp.toSp() })
            slot.value?.let {
                Text(
                    it,
                    fontSize = with(density) { 10.dp.toSp() },
                    maxLines = 1,
                    color = when {
                        selected -> colors.inverseOnSurface
                        slot.alert -> colors.error
                        else -> colors.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun Caption(text: String, side: Side, geometry: RingGeometry) {
    if (geometry.captionWidth <= 0f) return
    val alignment = if (side == Side.LEFT) Alignment.CenterStart else Alignment.CenterEnd
    Box(
        contentAlignment = alignment,
        modifier = Modifier.offset(geometry.captionLeft.dp, 0.dp).size(geometry.captionWidth.dp, geometry.height.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 4.dp,
        ) {
            Text(text, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
        }
    }
}

@Preview(widthDp = 400, heightDp = 320)
@Composable
private fun ActionsRingPreview() {
    NyxTheme {
        ActionsRing(
            summary = TodaySummary(
                totalSeconds = 3 * 3600 + 12 * 60,
                productiveSeconds = 3600 + 5 * 60,
                doomscrollSeconds = 47 * 60,
                goals = listOf(GoalProgress(Category.SOCIAL, 52 * 60, 45 * 60)),
                petFace = "😾",
            ),
            status = RingStatus(continuousMs = 38 * 60_000L, breakAfterMinutes = 45),
            glowing = true,
            actions = RingActions(30, onFocus = {}, onOpenApp = {}, onHide = {}),
            side = Side.RIGHT,
            maxWidth = 400.dp,
            onPetClick = {},
            onClose = {},
        )
    }
}
