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
    initialSettings: CmsSettings? = null
) {
    var showSettings by remember { mutableStateOf(initialSettings == null) }
    var savedSettings by remember { mutableStateOf(initialSettings) }

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
            modifier = Modifier.fillMaxSize()
        )
    }
}
