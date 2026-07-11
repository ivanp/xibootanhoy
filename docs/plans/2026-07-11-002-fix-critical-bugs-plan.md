# Plan: Fix Critical Bugs in xibo-android-player

**Created:** 2026-07-11
**Plan type:** fix
**Target repo:** xibo-android-player
**Sequence:** 002
**Origin:** `docs/gap-analysis.md`

---

## Problem Frame

The xibo-android-player has four critical bugs that block core functionality:

1. **Reversed priority resolution** — The schedule parser uses `priority < curPrio` (lower wins), but the Xibo CMS convention is higher-priority-wins. This causes the wrong layout to play during schedule conflicts.
2. **Missing `<uri>` child element parsing** — The XLF parser only reads `<options>` attributes, but the XLF format stores `<uri>` as a child element. Media widgets show placeholders instead of content. (Already planned in `2026-07-11-001-fix-media-widgets-not-rendering-plan.md`.)
3. **No XMR WebSocket connection** — XMR message types are defined (`CollectNow`, `Screenshot`, `Purge`, `WebHook`, `Command`) but there is no WebSocket client to receive them. Real-time commands never arrive.
4. **No command execution** — Commands (shell, HTTP, RS232) are parsed from RegisterDisplay but never executed. The `PlayerSettings.commands` map is populated and then ignored.

---

## Requirements

| ID | Requirement | Source |
|:---|:-----------|:-------|
| R1 | Schedule priority must follow CMS convention: higher priority value = higher importance | gap-analysis §3.1 |
| R2 | Image widgets must display the cached image file | gap-analysis §3.2 |
| R3 | Video widgets must play the cached video file | gap-analysis §3.2 |
| R4 | Widgets must fall back gracefully if the media file is not cached | gap-analysis §3.2 |
| R5 | The player must connect to the XMR WebSocket and handle real-time messages | gap-analysis §3.5 |
| R6 | The player must execute shell/HTTP/RS232 commands from the CMS | gap-analysis §3.4 |
| R7 | Command execution must report success/failure back to the CMS | gap-analysis §3.4 |

---

## Key Technical Decisions

| Decision | Choice | Rationale |
|:---------|:-------|:----------|
| Priority fix scope | Change `<` to `>` in `layoutsNow()` | Minimal change, single line, matches all other Xibo players |
| URI resolution timing | At layout render time, not parse time | Parser is stateless; renderer has context and cache |
| URI format | `file://` absolute paths | Works with both Coil (images) and ExoPlayer (videos) |
| XMR transport | WebSocket (okhttp3 WebSocket) | Native Android WebSocket support via OkHttp, no extra dependencies |
| XMR message dispatch | Event-driven sealed class pattern | Matches existing `XmrMessage` model, extensible |
| Command execution | Coroutine-based with timeout | Non-blocking, safe for shell/HTTP/RS232, matches existing coroutine pattern |
| Command timeout | 30 seconds | Matches PWA player convention |
| Shell command safety | Whitelist-based: only allow commands from CMS config | Prevents arbitrary shell injection |

---

## Implementation Units

### U1. Fix reversed priority resolution

**Goal:** Correct the priority comparison in `Schedule.layoutsNow()` so higher priority values win, matching CMS convention.

**Requirements:** R1

**Dependencies:** None

**Files:**
- `app/src/main/java/org/xiboplayer/player/engine/PlayerEngine.kt`

**Approach:**
Change line 351 from `entry.priority < curPrio` to `entry.priority > curPrio`. Also change the initial value of `curPrio` from `Int.MAX_VALUE` to `Int.MIN_VALUE` so the first entry always wins.

**Test scenarios:**
- Schedule with entries at priorities 1, 5, 10 → priority 10 layout is selected
- Schedule with entries at priorities 10, 5, 1 → priority 10 layout is selected
- Schedule with two entries at same priority (both 5) → both layouts are returned for cycling
- Schedule with no active entries → default layout is returned
- Empty schedule → empty list returned

**Verification:** Unit test or log output showing correct layout selection for a multi-priority schedule.

---

### U2. Parse `<uri>` from `<options>` child elements in XlfParser

**Goal:** Extract the `uri` child element from `<options>` and store it in `Widget.options["uri"]`.

**Requirements:** R2, R3, R4

**Dependencies:** None

**Files:**
- `app/src/main/java/org/xiboplayer/player/engine/XlfParser.kt`

**Approach:**
After parsing `<options>` attributes (lines 108-111), also look for a `<uri>` child element within `<options>` and extract its text content. The XLF structure is:

```xml
<options>
  <uri>26.jpg</uri>
  <scaleType>center</scaleType>
  ...
</options>
```

Add after the attribute loop:
```kotlin
// Parse uri from <options> child elements
val uriChildren = optionsEl.getElementsByTagName("uri")
if (uriChildren.length > 0) {
    options["uri"] = uriChildren.item(0).textContent ?: ""
}
```

**Test scenarios:**
- Widget with `<options><uri>file.jpg</uri></options>` → `options["uri"]` = `"file.jpg"`
- Widget with `<options></options>` (no uri child) → `options["uri"]` absent
- Widget with no `<options>` element at all → no crash, `options` stays empty

**Verification:** Unit test or log output showing `options["uri"]` is populated for media widgets.

---

### U3. Resolve URI to local file path in LayoutRenderer

**Goal:** Convert the bare filename from `options["uri"]` to a `file://` absolute path using the file cache, and set it on `widget.uri` before passing to `WidgetRenderer`.

**Requirements:** R2, R3, R4

**Dependencies:** U2

**Files:**
- `app/src/main/java/org/xiboplayer/player/renderer/LayoutRenderer.kt`
- `app/src/main/java/org/xiboplayer/player/storage/FileCache.kt`

**Approach:**
In `LayoutRenderer`, before rendering each region's widgets, resolve the URI:

1. Get the `uri` from `widget.options["uri"]`
2. If present, look up the file in `FileCache` by searching the media directory for a file matching the basename
3. Set `widget.uri` to `"file://" + absolutePath`
4. If not found in cache, leave `widget.uri` as null (fallback to placeholder)

Add a helper method to `FileCache`:
```kotlin
fun resolveMediaUri(filename: String): String? {
    val file = File(mediaDir, filename)
    return if (file.exists()) file.absolutePath else null
}
```

**Test scenarios:**
- Cached file `26.jpg` exists → `widget.uri` = `"file:///data/.../xibo-cache/media/26.jpg"`
- Cached file does not exist → `widget.uri` stays null → placeholder shown
- Widget with no `uri` in options → no resolution attempted

**Verification:** Log output showing resolved URI path for each media widget.

---

### U4. Add XMR WebSocket client

**Goal:** Implement a WebSocket client that connects to the XMR relay and dispatches real-time messages to the player engine.

**Requirements:** R5

**Dependencies:** None

**Files:**
- `app/src/main/java/org/xiboplayer/player/xmr/XmrClient.kt` (new)
- `app/src/main/java/org/xiboplayer/player/engine/PlayerEngine.kt` (modify)

**Approach:**

Create a new `XmrClient` class that:
1. Connects to the XMR WebSocket URL (`ws://{xmrNetworkAddress}`) using OkHttp WebSocket
2. Sends an init handshake with `channel` and `key` on connect
3. Listens for JSON messages and dispatches by `action` field
4. Handles reconnection with exponential backoff (1s → 2s → 4s → max 30s)
5. Drops messages past TTL expiry

Supported actions:
- `collectNow` → trigger `PlayerEngine.collectOnce()`
- `screenshot` / `screenShot` → trigger screenshot capture
- `purge` → clear cache and re-download
- `changeLayout` → switch to specific layout
- `overlayLayout` → push overlay layout (stub for now)
- `revertToSchedule` → return to scheduled content
- `webhook` → fire webhook callback (stub for now)
- `command` → execute a CMS command

Wire into `PlayerEngine.start()`: after RegisterDisplay succeeds, if `xmrNetworkAddress` is non-empty, start the XMR client.

**Patterns to follow:**
- OkHttp WebSocket: `okhttp3.WebSocket` and `okhttp3.WebSocketListener`
- Existing coroutine pattern in `PlayerEngine` for lifecycle management
- `XmrMessage` sealed class in `Models.kt` for message types

**Test scenarios:**
- XMR WebSocket connects and sends init handshake with correct channel/key
- `collectNow` message triggers `collectOnce()`
- `screenshot` message triggers screenshot capture
- `purge` message clears cache and triggers collect
- `changeLayout` message switches to specified layout
- WebSocket disconnects → reconnects with backoff
- TTL-expired messages are silently dropped
- Empty `xmrNetworkAddress` → no connection attempted

**Verification:** Log output showing XMR connection, handshake, and message dispatch.

---

### U5. Add command execution engine

**Goal:** Implement an engine that executes shell, HTTP, and RS232 commands from the CMS and reports results.

**Requirements:** R6, R7

**Dependencies:** U4 (XMR dispatches command messages)

**Files:**
- `app/src/main/java/org/xiboplayer/player/engine/CommandExecutor.kt` (new)
- `app/src/main/java/org/xiboplayer/player/engine/PlayerEngine.kt` (modify)

**Approach:**

Create a `CommandExecutor` class that:
1. Receives a `Command` object (from `PlayerSettings.commands`)
2. Parses the `commandString` to determine type:
   - Shell commands: execute via `Runtime.getRuntime().exec()` with 30s timeout
   - HTTP commands: execute via OkHttp GET/POST with 30s timeout
   - RS232 commands: parse `dev,baud,bits,parity,stop,handshake,hex` and send via serial (stub for now — Android lacks serial port API)
3. Validates the command against `validationString` before execution
4. Reports success/failure via `PlayerStatus.lastCommandSuccess`
5. Logs all command execution attempts

Wire into `PlayerEngine`:
- Add `executeCommand(code: String)` method
- Call from XMR `command` action handler
- Call from scheduled commands during collect cycle

**Patterns to follow:**
- `Runtime.getRuntime().exec()` for shell commands (standard Android pattern)
- OkHttp for HTTP commands (already a dependency)
- Existing error handling pattern in `PlayerEngine`

**Test scenarios:**
- Shell command `echo "hello"` → executes, reports success
- Shell command with invalid binary → reports failure
- HTTP GET command to valid URL → executes, reports success
- HTTP command to unreachable URL → reports failure after timeout
- RS232 command → logged as unsupported (stub)
- Command with validation string that doesn't match output → reports failure
- Command with empty `commandString` → no-op, reports failure
- XMR `command` action triggers command execution

**Verification:** Log output showing command execution, validation, and result reporting.

---

### U6. Verify all fixes on emulator

**Goal:** Confirm all four critical bugs are fixed by building and running the APK.

**Requirements:** R1, R2, R3, R4, R5, R6, R7

**Dependencies:** U1, U2, U3, U4, U5

**Files:**
- (no code changes — verification only)

**Approach:**
1. Build the APK with `./gradlew assembleDebug`
2. Install on emulator
3. Configure with CMS credentials
4. Verify priority: schedule two layouts at different priorities, confirm higher-priority plays
5. Verify media: schedule a layout with image and video widgets, confirm they render
6. Verify XMR: send `collectNow` from CMS, confirm player re-collects
7. Verify commands: configure a shell command in CMS, trigger it, confirm execution

**Verification:** Visual confirmation via emulator + logcat output.

---

## Dependencies

```
U1 (priority fix) — standalone
U2 (uri parsing) — standalone
  └─ U3 (uri resolution) — depends on U2
U4 (XMR client) — standalone
  └─ U5 (command executor) — depends on U4
U6 (verification) — depends on U1, U2, U3, U4, U5
```

U1, U2, and U4 can be implemented in parallel. U3 depends on U2. U5 depends on U4. U6 depends on all.

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|:-----|:-----------|:-------|:-----------|
| XMR WebSocket URL format differs from expected | Medium | Medium | Log connection errors, fall back gracefully, make URL configurable |
| Shell commands blocked by Android SELinux | Medium | High | Log permission errors, report failure to CMS |
| RS232 not available on Android | High | Low | Stub with clear log message, report "not supported" to CMS |
| Large video files OOM on emulator | Low | Medium | Streaming download already implemented |
| WebView cleartext HTTP blocked | Low | Medium | App already allows cleartext traffic |

---

## Open Questions

| Question | Impact | Status |
|:---------|:-------|:-------|
| What is the exact XMR WebSocket URL format? | XMR connection | Deferred to implementation — log and inspect from CMS |
| Should RS232 be implemented via USB serial or Bluetooth? | Command execution | Deferred — stubbed for now |
| Should shell commands run on UI thread or background? | Command execution | Background via coroutine (matches existing pattern) |
