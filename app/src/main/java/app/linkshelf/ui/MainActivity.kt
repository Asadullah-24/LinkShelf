package app.linkshelf.ui

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.linkshelf.capture.BubbleService
import app.linkshelf.linkShelf
import app.linkshelf.ui.screens.FolderScreen
import app.linkshelf.ui.screens.HomeScreen
import app.linkshelf.ui.screens.SettingsScreen
import app.linkshelf.ui.theme.LinkShelfTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Bring the floating button back if the user had it on (e.g. after the app was closed).
        if (linkShelf.settings.bubbleEnabled && Settings.canDrawOverlays(this)) {
            BubbleService.start(this)
        }

        setContent {
            LinkShelfTheme {
                LinkShelfRoot()
            }
        }
    }
}

/** Routes: "home", "settings", or "folder:<ALL|PLATFORM_NAME>". */
@Composable
private fun LinkShelfRoot(vm: MainViewModel = viewModel()) {
    val links by vm.links.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var route by rememberSaveable { mutableStateOf("home") }

    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    BackHandler(enabled = route != "home") { route = "home" }

    when {
        route == "settings" -> SettingsScreen(
            vm = vm,
            snackbar = snackbar,
            linkCount = links.size,
            onBack = { route = "home" },
        )

        route.startsWith("folder:") -> FolderScreen(
            folderKey = route.removePrefix("folder:"),
            links = links,
            vm = vm,
            snackbar = snackbar,
            onBack = { route = "home" },
        )

        else -> HomeScreen(
            links = links,
            vm = vm,
            snackbar = snackbar,
            onOpenFolder = { route = "folder:$it" },
            onOpenSettings = { route = "settings" },
        )
    }
}
