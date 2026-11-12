package app.linkshelf.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshelf.data.LinkEntity
import app.linkshelf.data.platformEnum
import app.linkshelf.download.DownloadMode
import app.linkshelf.download.Downloads
import app.linkshelf.ui.Actions
import app.linkshelf.ui.theme.Panel
import app.linkshelf.ui.theme.TextMuted

/** Bottom sheet with the actions for a single saved link. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(
    link: LinkEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Panel) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text(
                link.title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${link.platformEnum().displayName} · ${link.kind}",
                color = TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                link.url,
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            SheetAction(Icons.Filled.Public, "Open original") {
                Actions.open(context, link.url); onDismiss()
            }
            SheetAction(Icons.Filled.ContentCopy, "Copy link") {
                Actions.copy(context, link.url); onDismiss()
            }
            SheetAction(Icons.Filled.Share, "Share") {
                Actions.share(context, link.url); onDismiss()
            }
            if (Downloads.isAvailable) {
                SheetAction(Icons.Filled.Movie, "Download video") {
                    Actions.download(context, listOf(link), DownloadMode.VIDEO); onDismiss()
                }
                SheetAction(Icons.Filled.MusicNote, "Download audio (MP3)") {
                    Actions.download(context, listOf(link), DownloadMode.AUDIO); onDismiss()
                }
            }
            SheetAction(Icons.Filled.Edit, "Rename") { renaming = true }
            SheetAction(Icons.Filled.Delete, "Delete", danger = true) { confirmDelete = true }
        }
    }

    if (renaming) {
        RenameDialog(
            current = link.title,
            onDismiss = { renaming = false },
            onSave = { onRename(it); renaming = false; onDismiss() },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this item?",
            body = link.title,
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = { onDelete(); confirmDelete = false; onDismiss() },
        )
    }
}
