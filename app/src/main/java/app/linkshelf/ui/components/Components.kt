package app.linkshelf.ui.components

import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.linkshelf.data.LinkEntity
import app.linkshelf.data.platformEnum
import app.linkshelf.domain.LinkParser
import app.linkshelf.ui.theme.Accent
import app.linkshelf.ui.theme.Danger
import app.linkshelf.ui.theme.Line
import app.linkshelf.ui.theme.Panel
import app.linkshelf.ui.theme.TextMuted
import app.linkshelf.ui.theme.brush
import coil.compose.AsyncImage

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        color = TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 2.dp),
    )
}

@Composable
fun FolderTile(
    name: String,
    shortLabel: String,
    brush: Brush,
    count: Int,
    dimWhenEmpty: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .alpha(if (dimWhenEmpty && count == 0) 0.5f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Pill(shortLabel, Modifier.align(Alignment.TopStart), alpha = 0.25f, fontSize = 13)
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "$count item${if (count == 1) "" else "s"}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, alpha: Float = 0.6f, fontSize: Int = 11) {
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .background(Color.Black.copy(alpha = alpha), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkCard(
    link: LinkEntity,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val platform = link.platformEnum()
    val shape = RoundedCornerShape(14.dp)
    val border = if (selected) BorderStroke(2.dp, Accent) else BorderStroke(1.dp, Line)
    val host = remember(link.url) { Uri.parse(link.url).host.orEmpty().removePrefix("www.") }
    val whenSaved = DateUtils.getRelativeTimeSpanString(
        link.addedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(border, shape)
            .background(Panel)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .background(platform.brush()),
            contentAlignment = Alignment.Center,
        ) {
            LinkParser.thumbnailUrl(link.videoId)?.let { thumb ->
                AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
            }
            Pill(link.kind, Modifier.align(Alignment.TopStart).padding(8.dp))
            Pill(platform.shortLabel, Modifier.align(Alignment.TopEnd).padding(8.dp))
            if (selecting) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (selected) Accent else Color.Black.copy(alpha = 0.35f))
                        .border(2.dp, if (selected) Accent else Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check, contentDescription = "Selected",
                            tint = Color.White, modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 11.dp)) {
            Text(
                text = link.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$host · $whenSaved",
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(body, color = TextMuted, fontSize = 14.sp)
        if (action != null) {
            Spacer(Modifier.height(10.dp))
            action()
        }
    }
}

@Composable
fun SheetAction(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) Danger else Color.Unspecified
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) Danger else TextMuted)
        Spacer(Modifier.width(14.dp))
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Title") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = Danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
