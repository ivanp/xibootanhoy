package org.xiboplayer.player.renderer

import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import org.xiboplayer.player.model.Widget
import org.xiboplayer.player.model.WidgetType
import org.xiboplayer.player.util.Logger

/**
 * Renders a single widget in a Compose layout.
 */
@Composable
fun WidgetRenderer(
    widget: Widget,
    modifier: Modifier = Modifier,
    logger: Logger = Logger()
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (widget.type) {
            WidgetType.IMAGE -> ImageWidget(widget)
            WidgetType.VIDEO, WidgetType.LOCAL_VIDEO -> VideoWidget(widget)
            WidgetType.AUDIO -> AudioWidget(widget)
            WidgetType.TEXT -> TextWidget(widget)
            WidgetType.HTML, WidgetType.TICKER -> HtmlWidget(widget, logger)
            WidgetType.WEBPAGE -> WebpageWidget(widget, logger)
            WidgetType.CLOCK -> ClockWidget()
            else -> TextWidget(widget)
        }
    }
}

/**
 * Image widget using Coil for async loading.
 */
@Composable
private fun ImageWidget(widget: Widget) {
    val uri = widget.uri
    if (uri != null) {
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build()
        )
        Image(
            painter = painter,
            contentDescription = "Image widget ${widget.id}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    } else {
        // Show placeholder
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Image", color = Color.White)
        }
    }
}

/**
 * Video widget using ExoPlayer.
 */
@Composable
private fun VideoWidget(widget: Widget) {
    val context = LocalContext.current
    val uri = widget.uri

    if (uri != null) {
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                exoPlayer.release()
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Text("Video", color = Color.White)
        }
    }
}

/**
 * Audio widget (plays audio without video).
 */
@Composable
private fun AudioWidget(widget: Widget) {
    val context = LocalContext.current
    val uri = widget.uri

    if (uri != null) {
        val exoPlayer = remember {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                exoPlayer.release()
            }
        }

        // Audio-only — show nothing or a small indicator
        Box(modifier = Modifier.fillMaxSize())
    }
}

/**
 * Text widget — renders raw text content.
 */
@Composable
private fun TextWidget(widget: Widget) {
    val text = widget.raw
    val fontSize = widget.options["fontSize"]?.toFloatOrNull()?.sp ?: 24.sp
    val fontColor = widget.options["fontColor"]?.let { parseColor(it) } ?: Color.White
    val bgColor = widget.options["backgroundColor"]?.let { parseColor(it) } ?: Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fontColor,
            fontSize = fontSize,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/**
 * HTML/Ticker widget using WebView.
 */
@Composable
private fun HtmlWidget(widget: Widget, logger: Logger) {
    val html = widget.raw

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        logger.debug("HTML widget ${widget.id} loaded")
                    }
                }
                loadDataWithBaseURL(null, wrapHtml(html), "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Webpage widget — loads a URL in WebView.
 */
@Composable
private fun WebpageWidget(widget: Widget, logger: Logger) {
    val url = widget.uri ?: widget.raw

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        logger.debug("Webpage widget ${widget.id} loaded: $url")
                    }
                }
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Clock widget — shows current time.
 */
@Composable
private fun ClockWidget() {
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            currentTime = now.format(java.util.Date())
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = currentTime,
            color = Color.White,
            fontSize = 48.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Wrap raw HTML in a full document.
 */
private fun wrapHtml(content: String): String {
    return if (content.trimStart().startsWith("<!") || content.trimStart().startsWith("<html")) {
        content
    } else {
        """<!DOCTYPE html>
<html>
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
<body style="margin:0;padding:0;overflow:hidden;background:transparent;">$content</body>
</html>"""
    }
}

/**
 * Parse a hex color string to Compose Color.
 */
private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.White
    }
}
