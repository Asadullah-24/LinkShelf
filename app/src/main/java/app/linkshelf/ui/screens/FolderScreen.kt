package app.linkshelf.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.linkshelf.data.LinkEntity
import app.linkshelf.domain.Platform
import app.linkshelf.ui.Actions
import app.linkshelf.ui.MainViewModel
import app.linkshelf.ui.components.ConfirmDialog
import app.linkshelf.ui.components.EmptyState
import app.linkshelf.ui.components.ItemSheet
import app.linkshelf.ui.components.LinkCard
import app.linkshelf.ui.theme.Background
import app.linkshelf.ui.theme.Panel2

private enum class SortOrder(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), TITLE("A–Z") }

/** One folder: cards for a platform (or all links), with search, filters and multi-select. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folderKey: String,
    links: List<LinkEntity>,
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val platform: Platform? = if (folderKey == "ALL") null else Platform.fromKey(folderKey)
    val title = platform?.displayName ?: "All saved"

    var search by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortOrder.NEWEST) }
    var kindFilter by rememberSaveable { mutableStateOf("All") }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var sheetItem by remember { mutableStateOf<LinkEntity?>(null) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val folderLinks = remember(links, platform) {
        if (platform == null) links else links.filter { it.platform == platform.name }
    }
    val kinds = remember(folderLinks) { listOf("All") + folderLinks.map { it.kind }.distinct() }
    val activeKind = if (kindFilter in kinds) kindFilter else "All"
    val visible = remember(folderLinks, search, sort, activeKind) {
        val query = search.trim().lowercase()
        folderLinks
            .filter { activeKind == "All" || it.kind == activeKind }
            .filter { query.isEmpty() || it.title.lowercase().contains(query) || it.url.lowercase().contains(query) }
            .let { list ->
                when (sort) {
                    SortOrder.NEWEST -> list.sortedByDescending { it.addedAt }
                    SortOrder.OLDEST -> list.sortedBy { it.addedAt }
                    SortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
                }
            }
    }

    // Forget selections for items that were deleted.
    LaunchedEffect(folderLinks) {
        val ids = folderLinks.map { it.id }.toSet()
        selected = selected.filter { it in ids }.toSet()
    }

    fun exitSelection() {
        selecting = false
        selected = emptySet()
    }

    val selectedLinks = folderLinks.filter { it.id in selected }

    BackHandler(enabled = selecting) { exitSelection() }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selecting) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    title = { Text("${selected.size} selected") },
                    actions = {
                        IconButton(onClick = {
                            val ids = visible.map { it.id }.toSet()
                            selected = if (selected.containsAll(ids)) selected - ids else selected + ids
                        }) { Icon(Icons.Filled.SelectAll, contentDescription = "Select all") }
                        IconButton(
                            onClick = { Actions.copy(context, selectedLinks.joinToString("\n") { it.url }) },
                            enabled = selected.isNotEmpty(),
                        ) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy links") }
                        IconButton(
                            onClick = { Actions.share(context, selectedLinks.joinToString("\n") { it.url }) },
                            enabled = selected.isNotEmpty(),
                        ) { Icon(Icons.Filled.Share, contentDescription = "Share links") }
                        IconButton(
                            onClick = { confirmDelete = true },
                            enabled = selected.isNotEmpty(),
                        ) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Panel2),
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    actions = {
                        if (folderLinks.isNotEmpty()) {
                            TextButton(onClick = { selecting = true }) { Text("Select") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(155.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Search in this folder") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(Icons.Filled.SwapVert, contentDescription = "Sort: ${sort.label}")
                            }
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                order.label,
                                                fontWeight = if (order == sort) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        onClick = { sort = order; sortMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }
                    if (kinds.size > 2) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            items(kinds) { kind ->
                                FilterChip(
                                    selected = kind == activeKind,
                                    onClick = { kindFilter = kind },
                                    label = { Text(kind) },
                                )
                            }
                        }
                    }
                }
            }

            if (visible.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (folderLinks.isEmpty()) {
                        EmptyState(
                            title = "This folder is empty",
                            body = "Links you save from ${platform?.displayName ?: "any app"} will appear here.",
                        )
                    } else {
                        EmptyState(title = "No matches", body = "Try a different search or filter.")
                    }
                }
            } else {
                items(visible, key = { it.id }) { link ->
                    LinkCard(
                        link = link,
                        selecting = selecting,
                        selected = link.id in selected,
                        onClick = {
                            if (selecting) {
                                selected = if (link.id in selected) selected - link.id else selected + link.id
                            } else {
                                sheetItem = link
                            }
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            selecting = true
                            selected = selected + link.id
                        },
                    )
                }
            }
        }
    }

    sheetItem?.let { link ->
        ItemSheet(
            link = link,
            onDismiss = { sheetItem = null },
            onRename = { vm.rename(link.id, it) },
            onDelete = { vm.delete(listOf(link.id)) },
        )
    }

    if (confirmDelete) {
        val count = selected.size
        ConfirmDialog(
            title = "Delete $count item${if (count == 1) "" else "s"}?",
            body = "They will be removed from this phone.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                vm.delete(selected)
                confirmDelete = false
                exitSelection()
            },
        )
    }
}
