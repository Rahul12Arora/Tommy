package com.companionstudio.nyx.pet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R
import com.companionstudio.nyx.focus.FocusSessionContent
import com.companionstudio.nyx.focus.FocusStatus

/** The ring's timer slot opens this in the ring's place, like the Limits list (D-118): the session, Done, End, Back. */
@Composable
internal fun RingFocusCard(status: FocusStatus, side: Side, geometry: RingGeometry, onEnd: (done: Boolean) -> Unit, onBack: () -> Unit) {
    val besidePet = PET_SIZE.value + PANEL_GAP.value
    val width = (geometry.width - besidePet).coerceAtMost(280f)
    val left = if (side == Side.LEFT) besidePet else geometry.width - besidePet - width
    Box(contentAlignment = Alignment.Center, modifier = Modifier.offset(left.dp, 0.dp).size(width.dp, geometry.height.dp)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier.heightIn(max = geometry.height.dp),
        ) {
            Box(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                FocusSessionContent(status, onDone = { onEnd(true) }, onEnd = { onEnd(false) }) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.ring_back)) }
                }
            }
        }
    }
}
