package app.linkshelf.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshelf.data.LinkEntity
import app.linkshelf.domain.Platform
import app.linkshelf.ui.Actions
import app.linkshelf.ui.MainViewModel
import app.linkshelf.ui.components.EmptyState
import app.linkshelf.ui.components.FolderTile
import app.linkshelf.ui.components.ItemSheet
import app.linkshelf.ui.components.LinkCard
import app.linkshelf.ui.components.SectionHeader
import app.linkshelf.ui.theme.AccentPink
import app.linkshelf.ui.theme.AllBrush
import app.linkshelf.ui.theme.Background
import app.linkshelf.ui.theme.TextMuted
import app.linkshelf.ui.theme.brush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    links: List<LinkEntity>,
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    onOpenFolder: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var pasteText by rememberSaveable { mutableStateOf("") }
    var sheetItem by remember { mutableStateOf<LinkEntity?>(null) }
    val counts = remember(links) { links.groupingBy { it.platform }.eachCount() }

    val savePasted = {
        vm.saveText(pasteText) { added -> if (added) pasteText = "" }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("LinkShelf", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.saveText(Actions.readClipboard(context)) },
                icon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) },
                text = { Text("Paste link") },
                containerColor = AccentPink,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 100.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            placeholder = { Text("Paste a reel, video or post link…") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { savePasted() }),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { savePasted() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("Save") }
                    }
                    Text(
                        "Tip: in Instagram, Facebook or YouTube tap Share → Save to LinkShelf. " +
                            "Or turn on the floating button in Settings.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Folders") }
            item {
                FolderTile("All saved", "ALL", AllBrush, links.size, dimWhenEmpty = false) {
                    onOpenFolder("ALL")
                }
            }
            items(Platform.entries) { platform ->
                FolderTile(
                    name = platform.displayName,
                    shortLabel = platform.shortLabel,
                    brush = platform.brush(),
                    count = counts[platform.name] ?: 0,
                ) { onOpenFolder(platform.name) }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Recently saved") }
            if (links.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "Nothing saved yet",
                        body = "Share a link to LinkShelf, paste one above, or load a few samples.",
                    ) {
                        OutlinedButton(onClick = { vm.addSamples() }) { Text("Add sample links") }
                    }
                }
            } else {
                items(links.take(6), key = { it.id }) { link ->
                    LinkCard(
                        link = link,
                        selecting = false,
                        selected = false,
                        onClick = { sheetItem = link },
                        onLongClick = { sheetItem = link },
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
}
