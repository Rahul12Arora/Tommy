package com.companionstudio.nyx.setup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.companionstudio.nyx.R
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.launch

/** S-02 wired to storage: "yes" is stored before moving on; "no" isn't stored at all (D-57). */
@Composable
fun AgeCheckRoute(setupSettings: SetupSettings, onAdult: () -> Unit, onUnder18: () -> Unit) {
    val scope = rememberCoroutineScope()
    AgeCheckScreen(
        onAdult = {
            scope.launch {
                setupSettings.confirmAge()
                onAdult()
            }
        },
        onUnder18 = onUnder18,
    )
}

/** S-02. */
@Composable
fun AgeCheckScreen(onAdult: () -> Unit, onUnder18: () -> Unit) {
    SetupStep(stringResource(R.string.age_title)) {
        Text(stringResource(R.string.age_body))
        Button(onClick = onAdult, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.age_yes))
        }
        OutlinedButton(onClick = onUnder18, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.age_no))
        }
    }
}

/** Where setup stops for someone under 18 (US-02). There is no way forward, only Close. */
@Composable
fun AdultsOnlyScreen(onClose: () -> Unit) {
    SetupStep(stringResource(R.string.adults_only_title)) {
        Text(stringResource(R.string.adults_only_body))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.adults_only_close))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AgeCheckScreenPreview() {
    NyxTheme { AgeCheckScreen(onAdult = {}, onUnder18 = {}) }
}

@Preview(showBackground = true)
@Composable
private fun AdultsOnlyScreenPreview() {
    NyxTheme { AdultsOnlyScreen(onClose = {}) }
}
