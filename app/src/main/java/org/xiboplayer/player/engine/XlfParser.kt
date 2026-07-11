package org.xiboplayer.player.engine

import org.xiboplayer.player.model.*
import org.xiboplayer.player.util.Logger
import org.w3c.dom.Element
import java.io.StringReader
import org.xml.sax.InputSource
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses XLF (Xibo Layout Format) XML into layout/region/widget models.
 *
 * XLF is the native layout format used by Xibo CMS.
 * Reference: arexibo layout.rs, xiboplayer renderer-lite.js
 */
class XlfParser(private val logger: Logger) {

    private val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
    }

    /**
     * Parse a full XLF layout document.
     */
    fun parseLayout(xlf: String, layoutId: Long): LayoutInfo {
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xlf)))
        val layoutEl = doc.documentElement

        val width = layoutEl.getAttribute("width").toIntOrNull() ?: 1920
        val height = layoutEl.getAttribute("height").toIntOrNull() ?: 1080
        val bgColor = layoutEl.getAttribute("backgroundColor") ?: "#000000"
        val enableStat = (layoutEl.getAttribute("enableStat")?.toIntOrNull() ?: 1) != 0

        return LayoutInfo(
            id = layoutId,
            width = width,
            height = height,
            backgroundColor = bgColor,
            enableStat = enableStat
        )
    }

    /**
     * Parse regions from a layout XLF.
     */
    fun parseRegions(xlf: String): List<Region> {
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xlf)))
        val regions = mutableListOf<Region>()

        val regionNodes = doc.getElementsByTagName("region")
        for (i in 0 until regionNodes.length) {
            val regionEl = regionNodes.item(i) as? Element ?: continue
            val regionId = regionEl.getAttribute("id") ?: "region_$i"
            val left = regionEl.getAttribute("left").toFloatOrNull() ?: 0f
            val top = regionEl.getAttribute("top").toFloatOrNull() ?: 0f
            val rw = regionEl.getAttribute("width").toFloatOrNull() ?: 100f
            val rh = regionEl.getAttribute("height").toFloatOrNull() ?: 100f

            val widgets = parseWidgets(regionEl)
            regions.add(Region(regionId, left, top, rw, rh, widgets))
        }

        return regions
    }

    /**
     * Parse widgets from a region element.
     */
    private fun parseWidgets(regionEl: Element): List<Widget> {
        val widgets = mutableListOf<Widget>()
        val mediaNodes = regionEl.getElementsByTagName("media")

        for (i in 0 until mediaNodes.length) {
            val mediaEl = mediaNodes.item(i) as? Element ?: continue
            val widget = parseWidget(mediaEl)
            if (widget != null) widgets.add(widget)
        }

        return widgets
    }

    /**
     * Parse a single widget from a <media> element.
     */
    private fun parseWidget(mediaEl: Element): Widget? {
        val id = mediaEl.getAttribute("id").toLongOrNull() ?: return null
        val typeStr = mediaEl.getAttribute("type") ?: "image"
        val type = parseWidgetType(typeStr)

        val duration = mediaEl.getAttribute("duration").toLongOrNull() ?: 10000L
        val left = mediaEl.getAttribute("left").toFloatOrNull() ?: 0f
        val top = mediaEl.getAttribute("top").toFloatOrNull() ?: 0f
        val width = mediaEl.getAttribute("width").toFloatOrNull() ?: 100f
        val height = mediaEl.getAttribute("height").toFloatOrNull() ?: 100f

        val transitionIn = mediaEl.getAttribute("transitionIn")?.takeIf { it.isNotEmpty() }
        val transitionOut = mediaEl.getAttribute("transitionOut")?.takeIf { it.isNotEmpty() }
        val transitionDuration = mediaEl.getAttribute("transitionDuration").toLongOrNull() ?: 0L

        val raw = mediaEl.textContent ?: ""

        // Parse options
        val options = mutableMapOf<String, String>()
        val optionNodes = mediaEl.getElementsByTagName("options")
        if (optionNodes.length > 0) {
            val optionsEl = optionNodes.item(0) as? Element
            if (optionsEl != null) {
                for (j in 0 until optionsEl.attributes.length) {
                    val attr = optionsEl.attributes.item(j)
                    options[attr.nodeName] = attr.nodeValue ?: ""
                }
            }
        }

        // Parse fileId
        val fileId = mediaEl.getAttribute("fileId").toLongOrNull()
            ?: mediaEl.getAttribute("id").toLongOrNull()

        return Widget(
            id = id,
            type = type,
            left = left,
            top = top,
            width = width,
            height = height,
            duration = duration,
            transitionIn = transitionIn,
            transitionOut = transitionOut,
            transitionDuration = transitionDuration,
            raw = raw,
            fileId = fileId,
            options = options
        )
    }

    private fun parseWidgetType(type: String): WidgetType {
        return when (type.lowercase()) {
            "image" -> WidgetType.IMAGE
            "video" -> WidgetType.VIDEO
            "localvideo" -> WidgetType.LOCAL_VIDEO
            "audio" -> WidgetType.AUDIO
            "text" -> WidgetType.TEXT
            "html" -> WidgetType.HTML
            "ticker" -> WidgetType.TICKER
            "webpage" -> WidgetType.WEBPAGE
            "pdf" -> WidgetType.PDF
            "shellcommand" -> WidgetType.SHELL_COMMAND
            "clock" -> WidgetType.CLOCK
            "datasetview" -> WidgetType.DATASET_VIEW
            else -> {
                logger.warn("Unknown widget type: $type, treating as HTML")
                WidgetType.HTML
            }
        }
    }
}
