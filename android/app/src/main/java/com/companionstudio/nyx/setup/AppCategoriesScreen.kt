package com.companionstudio.nyx.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.companionstudio.nyx.R
import com.companionstudio.nyx.config.Category
import com.companionstudio.nyx.config.EffectiveConfig
import com.companionstudio.nyx.tracking.AppCategoryRepository
import com.companionstudio.nyx.tracking.InstalledApps
import com.companionstudio.nyx.ui.labelRes
import com.companionstudio.nyx.ui.theme.NyxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private val IconSize = 40.dp

@Composable
fun AppCategoriesRoute(
    installedApps: InstalledApps,
    config: Flow<EffectiveConfig>,
    repository: AppCategoryRepository,
    onNext: () -> Unit,
) {
    val viewModel = viewModel { AppCategoriesViewModel(installedApps::list, config, repository) }
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val iconPx = with(LocalDensity.current) { IconSize.roundToPx() }
    AppCategoriesScreen(
        rows = rows,
        query = query,
        onSearch = viewModel::search,
        onChoose = viewModel::choose,
        // Icons load per visible row, off the main thread, never all up front (§14.3)
        loadIcon = { packageName ->
            withContext(Dispatchers.IO) { installedApps.icon(packageName)?.toBitmap(iconPx, iconPx)?.asImageBitmap() }
        },
        onNext = onNext,
    )
}

/**
 * S-05. [rows] is `null` while the app list loads. Not built on [SetupStep]: the list is lazy, and Continue stays
 * below it, always in reach. The title, intro and search scroll with the list, so large text in landscape still
 * leaves room for apps (BUG-04).
 */
@Composable
fun AppCategoriesScreen(
    rows: List<AppRow>?,
    query: String,
    onSearch: (String) -> Unit,
    onChoose: (packageName: String, Category) -> Unit,
    loadIcon: suspend (packageName: String) -> ImageBitmap?,
    onNext: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item {
                    Text(
                        stringResource(R.string.app_categories_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                }
                item { Text(stringResource(R.string.app_categories_intro), Modifier.padding(vertical = 12.dp)) }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onSearch,
                        label = { Text(stringResource(R.string.app_categories_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
                when {
                    rows == null -> item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    rows.isEmpty() -> item {
                        Text(stringResource(if (query.isBlank()) R.string.app_categories_none else R.string.app_categories_no_match))
                    }
                    else -> items(rows, key = { it.packageName }) { row ->
                        AppCategoryRow(row, onChoose = { onChoose(row.packageName, it) }, loadIcon = loadIcon)
                        HorizontalDivider()
                    }
                }
            }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.setup_continue))
            }
        }
    }
}

@Composable
private fun AppCategoryRow(row: AppRow, onChoose: (Category) -> Unit, loadIcon: suspend (String) -> ImageBitmap?) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val icon by produceState<ImageBitmap?>(initialValue = null, row.packageName) { value = loadIcon(row.packageName) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(R.string.app_categories_change)) { menuOpen = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val bitmap = icon
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(IconSize))
            else Spacer(Modifier.size(IconSize))
            Column(Modifier.weight(1f)) {
                Text(row.label, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(row.category.labelRes), style = MaterialTheme.typography.bodyMedium)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Category.entries.forEach { category ->
                val name = stringResource(category.labelRes)
                DropdownMenuItem(
                    // The current one is marked in words, not only by position or colour (§9)
                    text = { Text(if (category == row.category) stringResource(R.string.app_categories_current, name) else name) },
                    onClick = {
                        menuOpen = false
                        onChoose(category)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppCategoriesScreenPreview() {
    NyxTheme {
        AppCategoriesScreen(
            rows = listOf(
                AppRow("com.instagram.android", "Instagram", Category.SOCIAL),
                AppRow("com.android.settings", "Settings", Category.NEUTRAL),
            ),
            query = "",
            onSearch = {},
            onChoose = { _, _ -> },
            loadIcon = { null },
            onNext = {},
        )
    }
}
