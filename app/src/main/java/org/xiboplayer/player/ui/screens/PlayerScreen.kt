package org.xiboplayer.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.xiboplayer.player.engine.LayoutState
import org.xiboplayer.player.model.LayoutInfo
import org.xiboplayer.player.renderer.LayoutRenderer
import org.xiboplayer.player.util.Logger

/**
 * Main player screen — fullscreen layout rendering.
 * This is the primary display for the signage player.
 */
@Composable
fun PlayerScreen(
    layoutState: LayoutState,
    modifier: Modifier = Modifier,
    logger: Logger = Logger()
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
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
                // Show idle / splash screen
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
    }
}
