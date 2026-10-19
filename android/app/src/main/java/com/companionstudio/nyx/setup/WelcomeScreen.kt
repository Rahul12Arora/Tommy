package com.companionstudio.nyx.setup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.companionstudio.nyx.R
import com.companionstudio.nyx.ui.theme.NyxTheme

/** S-01. */
@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    SetupStep(stringResource(R.string.welcome_title)) {
        Text(stringResource(R.string.welcome_pet))
        Text(stringResource(R.string.welcome_nudges))
        Text(stringResource(R.string.welcome_privacy))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.welcome_start))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    NyxTheme { WelcomeScreen(onStart = {}) }
}
