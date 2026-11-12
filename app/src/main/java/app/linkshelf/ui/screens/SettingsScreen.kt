package app.linkshelf.ui.screens

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.linkshelf.BuildConfig
import app.linkshelf.R
import app.linkshelf.capture.BubbleService
import app.linkshelf.capture.QuickSaveTileService
import app.linkshelf.linkShelf
import app.linkshelf.ui.MainViewModel
import app.linkshelf.ui.components.ConfirmDialog
import app.linkshelf.ui.components.SectionHeader
import app.linkshelf.ui.theme.Background
import app.linkshelf.ui.theme.Danger
import app.linkshelf.ui.theme.Panel
import app.linkshelf.ui.theme.TextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    snackbar: SnackbarHostState,
    linkCount: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = context.linkShelf.settings

    var bubbleOn by remember {
        mutableStateOf(settings.bubbleEnabled && Settings.canDrawOverlays(context))
    }
    var waitingForOverlayPermission by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    fun startBubble() {
        settings.bubbleEnabled = true
        BubbleService.start(context)
        bubbleOn = true
    }

    // The bubble works without notifications, so start it whatever the user answers.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startBubble() }

    fun continueEnablingBubble() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startBubble()
        }
    }

    fun enableBubble() {
        if (Settings.canDrawOverlays(context)) {
            continueEnablingBubble()
        } else {
            waitingForOverlayPermission = true
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        }
    }

    fun disableBubble() {
        settings.bubbleEnabled = false
        BubbleService.stop(context)
        bubbleOn = false
    }

    // Coming back from the "Display over other apps" system screen.
    LifecycleResumeEffect(Unit) {
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false
            if (Settings.canDrawOverlays(context)) {
                continueEnablingBubble()
            } else {
                scope.launch {
                    snackbar.showSnackbar("Allow \"Display over other apps\" to use the floating button")
                }
            }
        }
        onPauseOrDispose { }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(vm::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importFrom) }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            SectionHeader("Ways to save")
            SettingCard {
                SettingRow(
                    title = "Floating save button",
                    subtitle = "A button that stays on top of Instagram, YouTube and other apps. " +
                        "Copy a link, tap the button, done. Long-press opens LinkShelf.",
                    trailing = {
                        Switch(
                            checked = bubbleOn,
                            onCheckedChange = { on -> if (on) enableBubble() else disableBubble() },
                        )
                    },
                    onClick = { if (bubbleOn) disableBubble() else enableBubble() },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SettingRow(
                        title = "Add \"Save link\" to Quick Settings",
                        subtitle = "Pull down the notification shade and tap the tile to save the copied link.",
                        onClick = { requestAddTile(context) },
                    )
                }
                SettingRow(
                    title = "Share menu",
                    subtitle = "Always on: in any app tap Share → Save to LinkShelf.",
                )
            }

            SectionHeader("Your data")
            SettingCard {
                SettingRow(
                    title = "Back up links",
                    subtitle = "Save all $linkCount links to a JSON file.",
                    onClick = { exportLauncher.launch("linkshelf-backup.json") },
                )
                SettingRow(
                    title = "Restore from backup",
                    subtitle = "Existing links are kept; duplicates are skipped.",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
                SettingRow(
                    title = "Delete all links",
                    subtitle = "Removes everything saved on this phone.",
                    titleColor = Danger,
                    onClick = { confirmDeleteAll = true },
                )
            }

            SectionHeader("About")
            SettingCard {
                SettingRow(
                    title = "LinkShelf ${BuildConfig.VERSION_NAME}",
                    subtitle = "Open source. Your links never leave this phone.",
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = "Delete all links?",
            body = "This cannot be undone. Make a backup first if you might need them.",
            confirmLabel = "Delete all",
            onDismiss = { confirmDeleteAll = false },
            onConfirm = { vm.deleteAll(); confirmDeleteAll = false },
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, QuickSaveTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_bookmark),
        context.mainExecutor,
    ) { _ -> }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        Column(Modifier.clip(RoundedCornerShape(14.dp))) { content() }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    titleColor: Color = Color.Unspecified,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = titleColor)
            Text(subtitle, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}
