package org.xiboplayer.player.api

import org.xiboplayer.player.model.*
import org.xiboplayer.player.util.Logger
import org.w3c.dom.Element
import java.io.StringReader
import org.xml.sax.InputSource
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

/**
 * XMDS SOAP client for Xibo CMS v5 protocol.
 *
 * Communicates with the CMS via SOAP/XML over HTTP.
 * Protocol reference: https://github.com/linuxnow/xibo_players_docs
 */
class XmdsClient(
    private val cmsSettings: CmsSettings,
    private val httpClient: OkHttpClientWrapper,
    private val logger: Logger
) {
    private val schemaVersion = 5
    private val soapNamespace = "urn:xmds"
    private val soapEncoding = "http://schemas.xmlsoap.org/soap/encoding/"
    private val soapEnvelope = "http://schemas.xmlsoap.org/soap/envelope/"
    private val xsi = "http://www.w3.org/2001/XMLSchema-instance"
    private val xsd = "http://www.w3.org/2001/XMLSchema"

    private val xmlFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
    }
    private val xpath = XPathFactory.newInstance().newXPath()

    private var displayName: String = cmsSettings.displayName
    private var macAddress: String = "00:00:00:00:00:00"
    private var xmrChannel: String = ""
    private var xmrPubKey: String = ""

    /**
     * RegisterDisplay — authenticate and get player settings.
     */
    fun registerDisplay(): PlayerSettings? {
        val xml = callSoap("RegisterDisplay", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId,
            "displayName" to displayName,
            "clientType" to "android",
            "clientVersion" to "1.0.0",
            "clientCode" to "400",
            "operatingSystem" to "Android 9",
            "macAddress" to macAddress,
            "xmrChannel" to xmrChannel,
            "xmrPubKey" to xmrPubKey
        ))

        val doc = xmlFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val display = doc.getElementsByTagName("display")?.item(0) as? Element ?: return null
        val code = display.getAttribute("code")

        if (code != "READY") return null

        xmrChannel = getChildText(display, "xmrChannel") ?: ""
        xmrPubKey = getChildText(display, "xmrPubKey") ?: ""

        val commands = mutableMapOf<String, Command>()
        val commandsNodes = display.getElementsByTagName("commands")
        for (i in 0 until commandsNodes.length) {
            val cmdsEl = commandsNodes.item(i) as? Element ?: continue
            val cmdChildren = cmdsEl.childNodes
            for (j in 0 until cmdChildren.length) {
                val cmdEl = cmdChildren.item(j) as? Element ?: continue
                if (cmdEl.tagName == "command") {
                    val code = cmdEl.getAttribute("code") ?: continue
                    commands[code] = Command(
                        commandString = cmdEl.getAttribute("commandString") ?: "",
                        validationString = cmdEl.getAttribute("validationString") ?: "",
                        createAlertOn = cmdEl.getAttribute("createAlertOn") ?: ""
                    )
                }
            }
        }

        return PlayerSettings(
            xmrNetworkAddress = getChildText(display, "xmrNetworkAddress") ?: "",
            xmrChannel = xmrChannel,
            xmrPubKey = xmrPubKey,
            logLevel = getChildText(display, "logLevel") ?: "debug",
            displayName = getChildText(display, "displayName") ?: displayName,
            statsEnabled = getChildInt(display, "statsEnabled") != 0,
            preventSleep = getChildInt(display, "preventSleep") != 0,
            collectInterval = getChildLong(display, "collectInterval") ?: 900L,
            screenshotInterval = getChildLong(display, "screenShotRequestInterval") ?: 0L,
            embeddedServerPort = getChildInt(display, "embeddedServerPort") ?: 9696,
            sizeX = getChildInt(display, "sizeX") ?: 0,
            sizeY = getChildInt(display, "sizeY") ?: 0,
            posX = getChildInt(display, "offsetX") ?: 0,
            posY = getChildInt(display, "offsetY") ?: 0,
            commands = commands
        )
    }

    /**
     * RequiredFiles — get list of files the player needs.
     */
    fun requiredFiles(): Pair<List<RequiredFile>, List<String>> {
        val xml = callSoap("RequiredFiles", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId
        ))

        val doc = xmlFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val requiredFiles = mutableListOf<RequiredFile>()
        val purgeList = mutableListOf<String>()

        val fileNodes = doc.getElementsByTagName("file")
        for (i in 0 until fileNodes.length) {
            val el = fileNodes.item(i) as? Element ?: continue
            val fileType = el.getAttribute("type")
            if (fileType == "purge") {
                purgeList.add(el.textContent ?: "")
                continue
            }
            requiredFiles.add(RequiredFile(
                id = el.getAttribute("id").toLongOrNull() ?: 0L,
                type = fileType,
                size = el.getAttribute("size").toLongOrNull() ?: 0L,
                md5 = el.getAttribute("md5") ?: "",
                downloadUrl = el.getAttribute("path") ?: "",
                path = el.getAttribute("path") ?: "",
                name = el.getAttribute("saveAs") ?: "",
                code = el.getAttribute("code") ?: ""
            ))
        }

        return Pair(requiredFiles, purgeList)
    }

    /**
     * GetFile — download a file chunk from the CMS.
     */
    fun getFileData(fileId: Long, fileType: String, offset: Long, size: Long): ByteArray {
        val envelope = buildEnvelope("GetFile", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId,
            "fileId" to fileId.toString(),
            "fileType" to fileType,
            "chunkOffset" to offset.toString(),
            "chuckSize" to size.toString()
        ))
        val url = "${cmsSettings.address}/xmds.php?v=$schemaVersion&method=GetFile"
        val rawXml = httpClient.post(url, envelope, mapOf(
            "Content-Type" to "text/xml; charset=utf-8",
            "SOAPAction" to "\"urn:GetFile\""
        ))

        // Parse the raw SOAP response to extract base64 content
        val doc = xmlFactory.newDocumentBuilder().parse(InputSource(StringReader(rawXml)))
        // Find the file element (namespaced like ns1:GetFileResponse > file)
        val allElements = doc.getElementsByTagName("*")
        for (i in 0 until allElements.length) {
            val el = allElements.item(i) as? Element ?: continue
            if (el.tagName.equals("file", ignoreCase = true) || el.tagName.endsWith(":file")) {
                val b64 = el.textContent ?: return ByteArray(0)
                return android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            }
        }
        return ByteArray(0)
    }

    /**
     * Schedule — get the current schedule.
     */
    fun getSchedule(): Schedule {
        val xml = callSoap("Schedule", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId
        ))

        val doc = xmlFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val scheduleEl = doc.getElementsByTagName("schedule")?.item(0) as? Element ?: return Schedule()

        val entries = mutableListOf<ScheduleEntry>()
        val layoutNodes = scheduleEl.getElementsByTagName("layout")
        for (i in 0 until layoutNodes.length) {
            val el = layoutNodes.item(i) as? Element ?: continue
            entries.add(ScheduleEntry(
                fromDt = el.getAttribute("fromdt") ?: "",
                toDt = el.getAttribute("todt") ?: "",
                layoutId = el.getAttribute("file").toLongOrNull() ?: 0L,
                priority = el.getAttribute("priority").toIntOrNull() ?: 0
            ))
        }

        var defaultLayout: Long? = null
        val defaultEl = scheduleEl.getElementsByTagName("default")?.item(0) as? Element
        if (defaultEl != null) {
            defaultLayout = defaultEl.getAttribute("file").toLongOrNull()
        }

        return Schedule(default = defaultLayout, entries = entries)
    }

    /**
     * GetResource — get HTML resource for a layout/region/media.
     */
    fun getResource(layoutId: Long, regionId: String, mediaId: String): String {
        val xml = callSoap("GetResource", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId,
            "layoutId" to layoutId.toString(),
            "regionId" to regionId,
            "mediaId" to mediaId
        ))

        val doc = xmlFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        return doc.getElementsByTagName("return")?.item(0)?.textContent ?: ""
    }

    /**
     * BlackList — report a failed media download.
     */
    fun blacklist(mediaId: Long, mediaType: String, reason: String) {
        callSoap("BlackList", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId,
            "mediaId" to mediaId.toString(),
            "type" to mediaType,
            "reason" to reason
        ))
    }

    /**
     * MediaInventory — report download completion status.
     */
    fun submitMediaInventory(inventory: List<Pair<String, Boolean>>) {
        val xml = buildMediaInventoryXml(inventory)
        callSoap("MediaInventory", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId,
            "mediaInventory" to xml
        ))
    }

    /**
     * SubmitLog — send buffered log entries.
     */
    fun submitLog(entries: List<LogEntry>) {
        val xml = buildLogXml(entries)
        callSoap("SubmitLog", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId,
            "logXml" to xml
        ))
    }

    /**
     * NotifyStatus — send player status.
     */
    fun notifyStatus(status: PlayerStatus) {
        val json = buildStatusJson(status)
        callSoap("NotifyStatus", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId,
            "status" to json
        ))
    }

    /**
     * SubmitScreenShot — send a screenshot.
     */
    fun submitScreenshot(pngData: ByteArray) {
        val b64 = android.util.Base64.encodeToString(pngData, android.util.Base64.DEFAULT)
        callSoap("SubmitScreenShot", mapOf(
            "serverKey" to cmsSettings.key,
            "hardwareKey" to cmsSettings.displayId,
            "screenshot" to b64
        ))
    }

    // ─── SOAP transport ─────────────────────────────────────────────

    private fun callSoap(method: String, params: Map<String, String>): String {
        val envelope = buildEnvelope(method, params)
        val url = "${cmsSettings.address}/xmds.php?v=$schemaVersion&method=$method"

        logger.debug("XMDS $method -> $url")

        val response = httpClient.post(url, envelope, mapOf(
            "Content-Type" to "text/xml; charset=utf-8",
            "SOAPAction" to "\"urn:$method\""
        ))

        return parseResponse(response, method)
    }

    private fun buildEnvelope(method: String, params: Map<String, String>): String {
        val paramElements = params.entries.joinToString("\n      ") { (key, value) ->
            val escaped = value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
            "<$key xsi:type=\"xsd:string\">$escaped</$key>"
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<soap:Envelope
    xmlns:soap="$soapEnvelope"
    xmlns:soapenc="$soapEncoding"
    xmlns:tns="$soapNamespace"
    xmlns:types="$soapNamespace/encodedTypes"
    xmlns:xsi="$xsi"
    xmlns:xsd="$xsd">
  <soap:Body soap:encodingStyle="$soapEncoding">
    <tns:$method>
      $paramElements
    </tns:$method>
  </soap:Body>
</soap:Envelope>"""
    }

    private fun parseResponse(xml: String, method: String): String {
        val doc = xmlFactory.newDocumentBuilder().parse(InputSource(StringReader(xml)))

        // Check for SOAP fault
        val faultNodes = doc.getElementsByTagName("Fault")
        if (faultNodes.length > 0) {
            val faultString = xpath.evaluate("//faultstring", doc)
            throw XmdsException("SOAP Fault: $faultString")
        }

        // Extract response - handle namespaced tags like ns1:RegisterDisplayResponse
        val responseTag = "${method}Response"
        // With isNamespaceAware=false, namespaced tags have the full name like "ns1:RegisterDisplayResponse"
        // Search for any element whose tag name ends with the response tag
        val allElements = doc.getElementsByTagName("*")
        var responseEl: Element? = null
        for (i in 0 until allElements.length) {
            val el = allElements.item(i) as? Element ?: continue
            if (el.tagName.endsWith(responseTag)) {
                responseEl = el
                break
            }
        }
        if (responseEl == null) {
            throw XmdsException("No $responseTag in SOAP response")
        }
        // Find first element child (skip text nodes)
        val children = responseEl.childNodes
        var returnEl: Element? = null
        for (i in 0 until children.length) {
            if (children.item(i) is Element) {
                returnEl = children.item(i) as Element
                break
            }
        }
        if (returnEl == null) {
            throw XmdsException("No return element in $responseTag")
        }
        return returnEl.textContent ?: ""
    }

    // ─── XML helpers ────────────────────────────────────────────────

    private fun getChildText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        return (nodes.item(0) as? Element)?.textContent
    }

    private fun getChildInt(parent: Element, tag: String): Int? {
        return getChildText(parent, tag)?.toIntOrNull()
    }

    private fun getChildLong(parent: Element, tag: String): Long? {
        return getChildText(parent, tag)?.toLongOrNull()
    }

    private fun buildMediaInventoryXml(inventory: List<Pair<String, Boolean>>): String {
        val items = inventory.joinToString("") { (key, success) ->
            val parts = key.split("|", limit = 2)
            val type = parts.getOrElse(0) { "" }
            val id = parts.getOrElse(1) { "" }
            "<file type=\"$type\" id=\"$id\" complete=\"${if (success) 1 else 0}\" />"
        }
        return "<media_items>$items</media_items>"
    }

    private fun buildLogXml(entries: List<LogEntry>): String {
        val items = entries.joinToString("") { entry ->
            "<log date=\"${entry.date}\" category=\"${escapeXml(entry.category)}\" message=\"${escapeXml(entry.message)}\" />"
        }
        return "<logs>$items</logs>"
    }

    private fun buildStatusJson(status: PlayerStatus): String {
        return """{"currentLayoutId":${status.currentLayoutId},"availableSpace":${status.availableSpace},"totalSpace":${status.totalSpace},"lastCommandSuccess":${status.lastCommandSuccess},"deviceName":"${escapeJson(status.deviceName)}","timeZone":"${status.timeZone}"}"""
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

class XmdsException(message: String) : Exception(message)
