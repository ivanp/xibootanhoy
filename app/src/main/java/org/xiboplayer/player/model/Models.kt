package org.xiboplayer.player.model

/**
 * CMS connection settings — how the player talks to the Xibo CMS.
 */
data class CmsSettings(
    val address: String,
    val key: String,
    val displayId: String,
    val displayName: String = "Android Xibo Player",
    val proxy: String? = null
)

/**
 * Player settings returned by RegisterDisplay.
 */
data class PlayerSettings(
    val displayName: String = "Xibo",
    val collectInterval: Long = 900L,
    val statsEnabled: Boolean = false,
    val xmrNetworkAddress: String = "",
    val xmrChannel: String = "",
    val xmrPubKey: String = "",
    val logLevel: String = "debug",
    val screenshotInterval: Long = 0L,
    val embeddedServerPort: Int = 9696,
    val preventSleep: Boolean = false,
    val sizeX: Int = 0,
    val sizeY: Int = 0,
    val posX: Int = 0,
    val posY: Int = 0,
    val commands: Map<String, Command> = emptyMap()
)

/**
 * A player command (shell, HTTP, RS232) configured in the CMS.
 */
data class Command(
    val commandString: String,
    val validationString: String = "",
    val createAlertOn: String = ""
)

/**
 * A layout from the schedule.
 */
data class LayoutInfo(
    val id: Long,
    val code: String = "",
    val xlf: String = "",
    val width: Int = 1920,
    val height: Int = 1080,
    val backgroundColor: String = "#000000",
    val enableStat: Boolean = true,
    val options: Map<String, String> = emptyMap()
)

/**
 * A region within a layout.
 */
data class Region(
    val id: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val widgets: List<Widget> = emptyList()
)

/**
 * A media widget within a region.
 */
data class Widget(
    val id: Long,
    val type: WidgetType,
    val left: Float = 0f,
    val top: Float = 0f,
    val width: Float = 100f,
    val height: Float = 100f,
    val duration: Long = 10000L,
    val transitionIn: String? = null,
    val transitionOut: String? = null,
    val transitionDuration: Long = 0L,
    val raw: String = "",
    val fileId: Long? = null,
    val uri: String? = null,
    val options: Map<String, String> = emptyMap(),
    val cycle: Boolean = false,
    val playCount: Int = 1,
    val random: Boolean = false
)

/**
 * An interactive action attached to a widget.
 */
data class WidgetAction(
    val triggerType: String,
    val triggerCode: String? = null,
    val actionType: String,
    val targetId: Long? = null,
    val targetCode: String? = null
)

enum class WidgetType {
    IMAGE, VIDEO, AUDIO, TEXT, HTML, TICKER, WEBPAGE, PDF, SHELL_COMMAND, CLOCK, DATASET_VIEW, LOCAL_VIDEO
}

/**
 * A schedule entry from the CMS.
 */
data class ScheduleEntry(
    val fromDt: String,
    val toDt: String,
    val layoutId: Long,
    val priority: Int
)

/**
 * A campaign from the CMS schedule.
 */
data class Campaign(
    val id: Long,
    val priority: Int,
    val layoutIds: List<Long>
)

/**
 * Schedule with optional default layout.
 */
data class Schedule(
    val default: Long? = null,
    val entries: List<ScheduleEntry> = emptyList(),
    val campaigns: List<Campaign> = emptyList()
)

/**
 * A required file from the CMS.
 */
data class RequiredFile(
    val id: Long,
    val type: String,
    val size: Long,
    val md5: String,
    val downloadUrl: String? = null,
    val path: String = "",
    val name: String = "",
    val code: String = ""
)

/**
 * Log entry for CMS submission.
 */
data class LogEntry(
    val date: Long,
    val category: String,
    val message: String
)

/**
 * Player status for NotifyStatus.
 */
data class PlayerStatus(
    val currentLayoutId: Long = 0,
    val availableSpace: Long = 0,
    val totalSpace: Long = 0,
    val lastCommandSuccess: Boolean = true,
    val deviceName: String = "Android Xibo Player",
    val timeZone: String = "UTC"
)

/**
 * XMR message types.
 */
sealed class XmrMessage {
    data object CollectNow : XmrMessage()
    data object Screenshot : XmrMessage()
    data object Purge : XmrMessage()
    data class ChangeLayout(val layoutId: Long) : XmrMessage()
    data class OverlayLayout(val layoutId: Long, val duration: Long? = null) : XmrMessage()
    data object RevertToSchedule : XmrMessage()
    data class WebHook(val code: String) : XmrMessage()
    data class Command(val code: String) : XmrMessage()
}
