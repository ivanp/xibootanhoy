package org.xiboplayer.player.renderer

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
    logger: Logger = Logger()
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
                    logger = logger
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
    logger: Logger
) {
    val density = LocalDensity.current

    if (region.widgets.isEmpty()) return

    var currentWidgetIndex by remember { mutableIntStateOf(0) }

    // Cycle widgets based on duration
    val currentWidget = region.widgets.getOrNull(currentWidgetIndex)
    if (currentWidget != null) {
        LaunchedEffect(currentWidget.id) {
            delay(currentWidget.duration)
            currentWidgetIndex = (currentWidgetIndex + 1) % region.widgets.size
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
        if (currentWidget != null) {
            WidgetRenderer(
                widget = currentWidget,
                logger = logger
            )
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
