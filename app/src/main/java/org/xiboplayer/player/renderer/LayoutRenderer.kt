package org.xiboplayer.player.renderer

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay
import org.xiboplayer.player.engine.XlfParser
import org.xiboplayer.player.storage.FileCache
import org.xiboplayer.player.model.LayoutInfo
import org.xiboplayer.player.model.Region
import org.xiboplayer.player.model.Widget
import org.xiboplayer.player.util.Logger

/**
 * Renders a full Xibo layout with regions and widgets.
 *
 * Mirrors the arexibo layout.rs HTML translator and renderer-lite.js.
 * Regions are positioned absolutely within the layout, and widgets
 * cycle within each region based on their duration.
 */
@Composable
fun LayoutRenderer(
    layout: LayoutInfo,
    modifier: Modifier = Modifier,
    logger: Logger = Logger(),
    onWidgetAction: (Widget) -> Unit = {}
) {
    val parser = remember { XlfParser(logger) }
    val xlf = layout.xlf

    if (xlf.isBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val regions = remember(xlf) { parser.parseRegions(xlf) }
    val layoutWidth = layout.width.coerceAtLeast(1)
    val layoutHeight = layout.height.coerceAtLeast(1)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val cache = remember {
        FileCache(java.io.File(context.filesDir, "xibo-cache"), logger)
    }
    // Resolve media URIs from bare filenames to file:// paths via FileCache
    val resolvedRegions = remember(regions) {
        regions.map { region ->
            region.copy(widgets = region.widgets.map { widget ->
                val uri = widget.options["uri"]
                if (uri != null) {
                    val resolved = cache.resolveMediaUri(uri)
                    if (resolved != null) {
                        logger.debug("Resolved ${widget.id}: $uri -> $resolved")
                        widget.copy(uri = resolved)
                    } else {
                        logger.warn("Media file not cached: $uri (widget ${widget.id})")
                        widget
                    }
                } else {
                    widget
                }
            })
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(parseLayoutColor(layout.backgroundColor))
            .onSizeChanged { containerSize = it }
    ) {
        if (containerSize == IntSize.Zero) return@Box

        val scaleX = containerSize.width.toFloat() / layoutWidth
        val scaleY = containerSize.height.toFloat() / layoutHeight
        val scale = minOf(scaleX, scaleY)

        val offsetX = (containerSize.width - (layoutWidth * scale).toInt()) / 2
        val offsetY = (containerSize.height - (layoutHeight * scale).toInt()) / 2

        Box(
            modifier = Modifier
                .offset(
                    x = with(density) { offsetX.toDp() },
                    y = with(density) { offsetY.toDp() }
                )
                .size(
                    width = with(density) { (layoutWidth * scale).toDp() },
                    height = with(density) { (layoutHeight * scale).toDp() }
                )
        ) {
            resolvedRegions.forEach { region ->
                RegionRenderer(
                    region = region,
                    scale = scale,
                    logger = logger,
                    onWidgetAction = onWidgetAction
                )
            }
        }
    }
}

/**
 * Renders a single region with widget cycling.
 */
@Composable
private fun RegionRenderer(
    region: Region,
    scale: Float,
    logger: Logger,
    onWidgetAction: (Widget) -> Unit = {}
) {
    val density = LocalDensity.current

    if (region.widgets.isEmpty()) return

    // Read playlist cycling options from first widget that has them set
    val firstWidget = region.widgets.first()
    val cycle = firstWidget.cycle
    val playCount = firstWidget.playCount.coerceAtLeast(1)
    val random = firstWidget.random

    var currentWidgetIndex by remember { mutableIntStateOf(0) }
    // How many times the current widget has been played in this slot
    var currentPlayCount by remember { mutableIntStateOf(0) }
    // Shuffled order for random mode; rebuilt each full cycle
    var randomOrder by remember {
        mutableStateOf(region.widgets.indices.toMutableList().also { it.shuffle() })
    }
    // Whether we've completed at least one full pass (controls non-cycle termination)
    var cycleComplete by remember { mutableStateOf(false) }

    val resolvedIndex = if (random) {
        randomOrder.getOrElse(currentWidgetIndex) { 0 }
    } else {
        currentWidgetIndex
    }
    val currentWidget = region.widgets.getOrNull(resolvedIndex)

    if (currentWidget != null) {
        // Key on both widget identity and play-count iteration so LaunchedEffect fires each repeat
        LaunchedEffect(resolvedIndex, currentPlayCount) {
            delay(currentWidget.duration)

            val nextPlayCount = currentPlayCount + 1
            if (nextPlayCount < playCount) {
                // Still need to play this widget more times
                currentPlayCount = nextPlayCount
            } else {
                // Advance to next widget
                currentPlayCount = 0
                val nextIndex = currentWidgetIndex + 1
                if (nextIndex >= region.widgets.size) {
                    // Completed a full pass through all widgets
                    if (cycle) {
                        // Rebuild random order for next cycle
                        if (random) {
                            randomOrder = region.widgets.indices.toMutableList().also { it.shuffle() }
                        }
                        currentWidgetIndex = 0
                    } else {
                        // Non-cycling: mark complete, stay on last widget
                        cycleComplete = true
                    }
                } else {
                    currentWidgetIndex = nextIndex
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { (region.left * scale).toDp() },
                y = with(density) { (region.top * scale).toDp() }
            )
            .size(
                width = with(density) { (region.width * scale).toDp() },
                height = with(density) { (region.height * scale).toDp() }
            )
    ) {
        AnimatedContent(
            targetState = currentWidget,
            transitionSpec = {
                val widget = targetState
                val durationMs = widget?.transitionDuration?.toInt()?.takeIf { it > 0 } ?: 300
                val transitionIn = widget?.transitionIn?.lowercase()
                val transitionOut = widget?.transitionOut?.lowercase()

                val enterAnim: EnterTransition = when (transitionIn) {
                    "fly" -> slideInHorizontally(
                        animationSpec = tween(durationMs),
                        initialOffsetX = { fullWidth -> fullWidth }
                    )
                    "fade", null, "" -> fadeIn(animationSpec = tween(durationMs))
                    else -> fadeIn(animationSpec = tween(durationMs))
                }

                val exitAnim: ExitTransition = when (transitionOut) {
                    "fly" -> slideOutHorizontally(
                        animationSpec = tween(durationMs),
                        targetOffsetX = { fullWidth -> -fullWidth }
                    )
                    "fade", null, "" -> fadeOut(animationSpec = tween(durationMs))
                    else -> fadeOut(animationSpec = tween(durationMs))
                }

                enterAnim togetherWith exitAnim
            },
            label = "widget-transition-${region.id}"
        ) { widget ->
            if (widget != null) {
                WidgetRenderer(
                    widget = widget,
                    logger = logger,
                    onAction = onWidgetAction
                )
            }
        }
    }

}

/**
 * Parse a hex color string to Compose Color.
 */
private fun parseLayoutColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Black
    }
}
