package org.xiboplayer.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import org.xiboplayer.player.engine.LayoutState
import org.xiboplayer.player.engine.PlayerEngine
import org.xiboplayer.player.model.CmsSettings
import org.xiboplayer.player.model.Widget
import org.xiboplayer.player.model.WidgetAction
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

    /**
     * Parse widget actions from the widget's options map and dispatch to the engine.
     */
    fun handleWidgetAction(widget: Widget) {
        val eng = engine ?: return
        val actionsStr = widget.options["actions"] ?: return
        val actions = actionsStr.split(";").mapNotNull { part ->
            val map = part.split(",").mapNotNull { kv ->
                val eqIdx = kv.indexOf('=')
                if (eqIdx >= 0) kv.substring(0, eqIdx) to kv.substring(eqIdx + 1)
                else null
            }.toMap()
            val triggerType = map["triggerType"] ?: return@mapNotNull null
            val actionType = map["actionType"] ?: return@mapNotNull null
            WidgetAction(
                triggerType = triggerType,
                triggerCode = map["triggerCode"],
                actionType = actionType,
                targetId = map["targetId"]?.toLongOrNull(),
                targetCode = map["targetCode"]
            )
        }
        // Only dispatch actions matching the touch trigger (WidgetRenderer fires on tap)
        for (action in actions) {
            if (action.triggerType == "touch") {
                eng.handleWidgetAction(action)
            }
        }
    }

    /**
     * Handle keyboard key presses by dispatching widget-like actions.
     */
    fun handleKeyboardAction(key: Key) {
        val eng = engine ?: return
        when (key) {
            Key.DirectionRight, Key.Plus -> eng.nextLayout()
            Key.DirectionLeft, Key.Minus -> eng.previousLayout()
            else -> {} // Ignore other keys
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
        val overlayState by engine?.overlayState?.collectAsState() ?: remember {
            mutableStateOf<LayoutState?>(null)
        }

        PlayerScreen(
            layoutState = layoutState,
            overlayState = overlayState,
            logger = logger,
            onSettingsClick = {
                showSettings = true
            },
            onLogout = {
                onLogout()
                savedSettings = null
                showSettings = true
            },
            onKeyboardAction = ::handleKeyboardAction,
            onWidgetAction = ::handleWidgetAction,
            modifier = Modifier.fillMaxSize()
        )
    }
}
