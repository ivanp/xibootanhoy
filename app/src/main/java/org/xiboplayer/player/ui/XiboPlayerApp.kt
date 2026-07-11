package org.xiboplayer.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.xiboplayer.player.engine.LayoutState
import org.xiboplayer.player.engine.PlayerEngine
import org.xiboplayer.player.model.CmsSettings
import org.xiboplayer.player.ui.screens.PlayerScreen
import org.xiboplayer.player.ui.screens.SettingsScreen
import org.xiboplayer.player.util.Logger

/**
 * Root composable for the Xibo Player app.
 * Manages navigation between player and settings screens.
 */
@Composable
fun XiboPlayerApp(
    engine: PlayerEngine?,
    logger: Logger,
    onSaveSettings: (CmsSettings) -> Unit,
    onLogout: () -> Unit = {},
    initialSettings: CmsSettings? = null
) {
    // Use a keyed remember so it re-evaluates when initialSettings changes
    var showSettings by remember(initialSettings) { mutableStateOf(initialSettings == null) }
    var savedSettings by remember { mutableStateOf(initialSettings) }

    // Sync savedSettings when initialSettings changes from outside
    LaunchedEffect(initialSettings) {
        if (initialSettings != null) {
            savedSettings = initialSettings
        }
    }

    if (showSettings) {
        SettingsScreen(
            currentSettings = savedSettings,
            onSave = { settings ->
                savedSettings = settings
                onSaveSettings(settings)
                showSettings = false
            },
            onBack = {
                if (savedSettings != null) {
                    showSettings = false
                }
            }
        )
    } else {
        val layoutState by engine?.layoutState?.collectAsState() ?: remember {
            mutableStateOf<LayoutState>(LayoutState.Idle)
        }

        PlayerScreen(
            layoutState = layoutState,
            logger = logger,
            onSettingsClick = {
                showSettings = true
            },
            onLogout = {
                onLogout()
                savedSettings = null
                showSettings = true
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
