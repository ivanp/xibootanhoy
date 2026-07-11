package org.xiboplayer.player.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xiboplayer.player.engine.LayoutState
import org.xiboplayer.player.model.Widget
import org.xiboplayer.player.renderer.LayoutRenderer
import org.xiboplayer.player.util.Logger

/**
 * Main player screen — fullscreen layout rendering.
 * Long-press to access settings and logout.
 */
@Composable
fun PlayerScreen(
    layoutState: LayoutState,
    overlayState: LayoutState? = null,
    modifier: Modifier = Modifier,
    logger: Logger = Logger(),
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onKeyboardAction: (Key) -> Unit = {},
    onWidgetAction: (Widget) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    fun handleKeyboardAction(key: Key) {
        onKeyboardAction(key)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    handleKeyboardAction(event.key)
                }
                false
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showMenu = true }
                )
            }
    ) {
        when (layoutState) {
            is LayoutState.Showing -> {
                val layout = layoutState.layout
                val transitionName = layout.options["transition"]?.lowercase()
                val transitionDuration = layout.options["transitionDuration"]?.toIntOrNull()?.takeIf { it > 0 } ?: 300

                AnimatedContent(
                    targetState = layout.id,
                    transitionSpec = {
                        when (transitionName) {
                            "fade" -> fadeIn(animationSpec = tween(transitionDuration)) togetherWith
                                fadeOut(animationSpec = tween(transitionDuration))
                            "slide" -> slideInHorizontally(
                                animationSpec = tween(transitionDuration),
                                initialOffsetX = { fullWidth -> fullWidth }
                            ) togetherWith slideOutHorizontally(
                                animationSpec = tween(transitionDuration),
                                targetOffsetX = { fullWidth -> -fullWidth }
                            )
                            "instant", null, "" -> fadeIn(animationSpec = tween(0)) togetherWith
                                fadeOut(animationSpec = tween(0))
                            else -> fadeIn(animationSpec = tween(transitionDuration)) togetherWith
                                fadeOut(animationSpec = tween(transitionDuration))
                        }
                    },
                    label = "layout-transition"
                ) { _ ->
                    LayoutRenderer(
                        layout = layout,
                        logger = logger,
                        modifier = Modifier.fillMaxSize(),
                        onWidgetAction = onWidgetAction
                    )
                }
            }
            is LayoutState.Idle -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1a1a2e)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Xibo Player",
                        color = Color.White,
                        fontSize = 36.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Waiting for schedule...",
                        color = Color.Gray,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Long-press for settings",
                        color = Color.DarkGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is LayoutState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1a1a2e)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Error",
                        color = Color.Red,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = layoutState.message,
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        }

        // Settings FAB — always visible
        FloatingActionButton(
            onClick = { showMenu = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp),
            containerColor = Color(0x88000000)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
        }

        // Overlay layout — rendered on top of main content
        if (overlayState is LayoutState.Showing) {
            LayoutRenderer(
                layout = overlayState.layout,
                logger = logger,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f),
                onWidgetAction = onWidgetAction
            )
        }
    }

    // Settings / Logout menu dialog
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("Xibo Player") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showMenu = false
                            onSettingsClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Settings", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            showMenu = false
                            onLogout()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Logout", color = Color(0xFFFF5252))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF2a2a3e),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}
