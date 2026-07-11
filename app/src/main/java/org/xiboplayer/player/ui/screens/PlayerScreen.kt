package org.xiboplayer.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xiboplayer.player.engine.LayoutState
import org.xiboplayer.player.renderer.LayoutRenderer
import org.xiboplayer.player.util.Logger

/**
 * Main player screen — fullscreen layout rendering.
 * Long-press to access settings and logout.
 */
@Composable
fun PlayerScreen(
    layoutState: LayoutState,
    modifier: Modifier = Modifier,
    logger: Logger = Logger(),
    onSettingsClick: () -> Unit = {},
    onLogout: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showMenu = true }
                )
            }
    ) {
        when (layoutState) {
            is LayoutState.Showing -> {
                LayoutRenderer(
                    layout = layoutState.layout,
                    logger = logger,
                    modifier = Modifier.fillMaxSize()
                )
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
