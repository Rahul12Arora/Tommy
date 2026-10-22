package com.companionstudio.nyx.focus

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.companionstudio.nyx.R

val APP_ICON_SIZE = 40.dp

/** S-09's allowed apps (US-12): a search field and a checkbox per launcher app, picked ones first. */
fun LazyListScope.allowedApps(
    apps: List<AppChoice>?,
    query: String,
    onSearch: (String) -> Unit,
    onToggle: (String) -> Unit,
    loadIcon: suspend (String) -> ImageBitmap?,
) {
    item(key = "apps-search") {
        OutlinedTextField(
            value = query,
            onValueChange = onSearch,
            label = { Text(stringResource(R.string.focus_apps_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    when {
        apps == null -> item(key = "apps-loading") { CircularProgressIndicator(Modifier.padding(16.dp)) }
        apps.isEmpty() -> item(key = "apps-none") { Text(stringResource(R.string.focus_apps_none)) }
        else -> items(apps, key = { it.packageName }) { app ->
            AllowedAppRow(app, onToggle = { onToggle(app.packageName) }, loadIcon = loadIcon)
            HorizontalDivider()
        }
    }
}

@Composable
private fun AllowedAppRow(app: AppChoice, onToggle: () -> Unit, loadIcon: suspend (String) -> ImageBitmap?) {
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) { value = loadIcon(app.packageName) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is one checkbox for TalkBack: the app's name and checked or not
            .toggleable(value = app.selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val bitmap = icon
        if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(APP_ICON_SIZE))
        else Spacer(Modifier.size(APP_ICON_SIZE))
        Text(app.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Checkbox(checked = app.selected, onCheckedChange = null)
    }
}
