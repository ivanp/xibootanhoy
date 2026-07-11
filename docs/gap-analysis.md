# Gap Analysis: xibo-android-player vs xiboplayer (PWA)

> **Date:** 2026-07-11
> **Scope:** Feature-by-feature comparison between the Android player (`xibo-android-player`) and the PWA player (`xiboplayer` packages).
> **Reference:** xiboplayer v0.7.23, xibo-android-player v1.0.0

---

## 1. Architecture Comparison

| Dimension | xibo-android-player | xiboplayer (PWA) |
|-----------|-------------------|-------------------|
| **Language** | Kotlin (Android/Compose) | JavaScript/TypeScript (platform-agnostic) |
| **CMS Protocol** | SOAP only (XMDS v5) | REST + SOAP dual transport (auto-detected) |
| **Auth** | hardwareKey per call | JWT Bearer (REST) / hardwareKey (SOAP) |
| **Schedule** | Client-side XML parsing | Server-side JSON (REST) / Client XML (SOAP) |
| **Architecture** | Monolithic Activity + Engine | 16 modular npm packages, event-driven |
| **State Management** | MutableStateFlow | EventEmitter + IndexedDB |
| **Offline** | Filesystem cache only | ServiceWorker + IndexedDB + filesystem |
| **Tests** | 0 (no test files found) | ~1629 tests across 16 packages |

---

## 2. Feature Parity Matrix

### 2.1 CMS Communication

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| SOAP/XMDS v5 | ✅ | ✅ | Both support |
| REST/JSON API | ❌ | ✅ | Android lacks PlayerRestApi support |
| JWT Bearer auth | ❌ | ✅ | Android uses hardwareKey only |
| Protocol auto-detection | ❌ | ✅ | PWA probes REST, falls back to SOAP |
| ETag caching | ❌ | ✅ | Android re-downloads unchanged data |
| CRC32 skip optimization | ❌ | ✅ | PWA skips collect if CRC unchanged |
| Weather data fetching | ❌ | ✅ | PWA fetches weather for criteria |
| Fault reporting | ❌ | ✅ | PWA reports faults to CMS dashboard |

### 2.2 Schedule & Layout Management

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| Basic schedule (time windows) | ✅ | ✅ | Both parse fromdt/todt |
| Priority resolution | ⚠️ | ✅ | **BUG**: Android uses `priority < curPrio` (reversed) |
| Default layout fallback | ✅ | ✅ | Both support |
| Campaigns | ❌ | ✅ | Android doesn't parse campaign XML |
| Sub-playlist cycling | ❌ | ✅ | Android has no cycle/playCount logic |
| Overlay layouts | ❌ | ✅ | Android renders one layout at a time |
| Interrupt layouts (ShareOfVoice) | ❌ | ✅ | Android has no interrupt scheduler |
| Dayparting (recurrence) | ❌ | ✅ | Android only supports absolute time windows |
| Geo-fencing | ❌ | ✅ | PWA has GPS+IP geofence criteria |
| Weather criteria | ❌ | ✅ | PWA evaluates weather conditions |
| Display property criteria | ❌ | ✅ | PWA evaluates CMS custom fields |
| Timeline projection | ❌ | ✅ | PWA has 2-hour lookahead debug overlay |
| Layout pre-loading (pool) | ❌ | ✅ | PWA has hot/warm LayoutPool |
| Layout blacklisting | ❌ | ✅ | PWA has 3-strike blacklist |
| Dependant tracking | ❌ | ✅ | PWA tracks layout→media dependencies |

### 2.3 Widget Types

| Widget Type | Android | PWA | Notes |
|-------------|:-------:|:---:|-------|
| Image | ✅ | ✅ | Android: Coil; PWA: HTML `<img>` |
| Video | ✅ | ✅ | Android: ExoPlayer; PWA: HTML5 `<video>` |
| Audio | ✅ | ✅ | Both support |
| Text | ✅ | ✅ | Both support |
| HTML | ✅ | ✅ | Both use WebView/iframe |
| Ticker | ✅ | ✅ | Both route to HTML renderer |
| Webpage | ✅ | ✅ | Both use WebView/iframe |
| Clock | ✅ | ✅ | Both render current time |
| PDF | ❌ | ✅ | PWA uses pdfjs-dist; Android falls back to text placeholder |
| Local Video | ✅ | ✅ | Both support |
| Dataset View | ❌ | ✅ | PWA has data connector integration |
| Shell Command | ❌ | ✅ | PWA executes via Electron IPC / HTTP endpoint |
| Clock Digital | ❌ | ✅ | PWA has dedicated digital clock widget |
| Clock Analogue | ❌ | ✅ | PWA has dedicated analogue clock widget |
| Calendar | ❌ | ✅ | PWA has calendar widget |
| Weather | ❌ | ✅ | PWA has weather widget |
| Currencies | ❌ | ✅ | PWA has currencies widget |
| Stocks | ❌ | ✅ | PWA has stocks widget |
| Twitter | ❌ | ✅ | PWA has Twitter widget |
| Embedded | ❌ | ✅ | PWA has generic iframe widget |
| Video In | ❌ | ✅ | PWA supports video input sources |
| HLS | ❌ | ✅ | PWA supports HLS streaming |
| PowerPoint | ❌ | ❌ | Legacy, neither supports |
| Flash | ❌ | ❌ | Legacy, neither supports |

### 2.4 Transitions & Animations

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| Widget-level transitions | ❌ | ✅ | Android parses but ignores transition fields |
| Layout-level transitions | ❌ | ✅ | PWA has fade/slide/wipe/cut |
| Region exit transitions | ❌ | ✅ | PWA applies exit animations |
| 8-direction fly | ❌ | ✅ | PWA supports compass directions |
| Web Animations API | ❌ | ✅ | PWA uses `element.animate()` |

### 2.5 Interactive Control (XIC)

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| Touch actions | ❌ | ✅ | PWA has click-on-widget triggers |
| Keyboard actions | ❌ | ✅ | PWA has keydown map |
| Webhook triggers | ❌ | ✅ | PWA has XMR + HTTP webhooks |
| navLayout | ❌ | ✅ | PWA supports navigation layouts |
| navWidget | ❌ | ✅ | PWA supports drawer navigation |
| next/previous | ❌ | ✅ | PWA has layout navigation |
| XIC HTTP endpoints | ❌ | ✅ | PWA has all 7 XIC endpoints |
| DataConnector realtime | ❌ | ✅ | PWA has polling + IC notifications |
| XpState (SMIL) | ❌ | ✅ | PWA has xp-state-init/xp-if runtime |

### 2.6 Real-Time Messaging (XMR)

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| XMR WebSocket client | ❌ | ✅ | Android has no socket connection |
| CollectNow | ❌ | ✅ | PWA triggers immediate collect |
| Screenshot capture | ❌ | ✅ | PWA captures and uploads |
| Purge cache | ❌ | ✅ | PWA clears and re-downloads |
| Change layout | ❌ | ✅ | PWA switches layout on command |
| Overlay layout | ❌ | ✅ | PWA pushes overlay on command |
| Revert to schedule | ❌ | ✅ | PWA returns to scheduled content |
| WebHook dispatch | ❌ | ✅ | PWA fires webhook callbacks |
| Command execution | ❌ | ✅ | PWA runs shell/HTTP commands |
| Licence check | ❌ | ✅ | PWA validates license |
| TTL expiry | ❌ | ✅ | PWA drops stale messages |
| Rekey action | ❌ | ✅ | PWA re-keys and re-collects |
| Geo-location sync | ❌ | ✅ | PWA syncs GPS coordinates |
| 60s reconnect health check | ❌ | ✅ | PWA auto-reconnects |

### 2.7 Multi-Display Sync

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| Lead/follower protocol | ❌ | ✅ | PWA has full sync architecture |
| BroadcastChannel (same-machine) | ❌ | ✅ | PWA syncs across browser tabs |
| WebSocket relay (cross-device) | ❌ | ✅ | PWA syncs across LAN |
| Choreography effects | ❌ | ✅ | PWA has 12 stagger modes |
| Heartbeat monitoring | ❌ | ✅ | PWA detects offline followers |
| Layout mapping (video wall) | ❌ | ✅ | PWA maps single layout to multiple displays |
| <8ms precision | ❌ | ✅ | PWA achieves sub-frame sync |

### 2.8 Cache & Download

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| File download (HTTP) | ✅ | ✅ | Both support |
| File download (XMDS GetFile) | ✅ | ✅ | Both support |
| MD5 verification | ✅ | ✅ | Both verify |
| Streaming large files | ✅ | ✅ | Both stream to disk |
| Chunked downloads | ⚠️ | ✅ | Android: sequential chunks; PWA: parallel chunks |
| Download barrier | ❌ | ✅ | PWA starts video before all chunks arrive |
| Resume support | ❌ | ✅ | PWA resumes interrupted downloads |
| Range requests | ❌ | ✅ | PWA uses HTTP Range headers |
| ETag caching | ❌ | ✅ | PWA caches via ETag |
| ContentStore (transport-agnostic) | ❌ | ✅ | PWA has unified cache backend |
| ServiceWorker integration | ❌ | ✅ | PWA caches in SW for offline |
| Dependant-aware download | ❌ | ✅ | PWA downloads layout assets first |
| Bandwidth limiting | ❌ | ✅ | PWA respects server-side limits |

### 2.9 Stats & Proof of Play

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| NotifyStatus | ✅ | ✅ | Both report basic status |
| MediaInventory | ✅ | ✅ | Both report download results |
| SubmitLog | ✅ | ✅ | Both submit logs |
| SubmitScreenshot | ✅ | ✅ | Both capture and upload |
| Layout/widget stats | ❌ | ✅ | PWA tracks per-layout/widget proof of play |
| Event-based stats | ❌ | ✅ | PWA tracks user interactions |
| Engagement tracking | ❌ | ✅ | PWA tracks ad impressions |
| Stats buffering (IndexedDB) | ❌ | ✅ | PWA buffers stats offline |
| Stats submission on collect | ❌ | ✅ | PWA submits on each cycle |

### 2.10 Commands & Actions

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| Shell command execution | ❌ | ✅ | Android parses but never executes |
| HTTP command execution | ❌ | ✅ | PWA executes via HTTP endpoint |
| RS232 command execution | ❌ | ✅ | PWA delegates to platform |
| Scheduled commands | ❌ | ✅ | PWA processes time-triggered commands |
| Command validation | ❌ | ✅ | PWA validates command strings |
| Command result reporting | ❌ | ✅ | PWA reports success/failure to CMS |

### 2.11 Data Connectors

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| Data connector polling | ❌ | ✅ | PWA polls external APIs |
| Data connector IC notifications | ❌ | ✅ | PWA receives realtime data updates |
| Dataset view rendering | ❌ | ✅ | PWA renders data-driven widgets |

### 2.12 Platform Features

| Feature | Android | PWA | Notes |
|---------|:-------:|:---:|-------|
| Boot auto-start | ✅ | ❌ | Android has BootReceiver |
| Screen sleep prevention | ✅ | ✅ | Both support |
| Screenshot capture | ✅ | ✅ | Both support |
| Embedded server | ❌ | ✅ | PWA has local HTTP server (proxy) |
| Multi-instance support | ❌ | ✅ | PWA runs multiple displays on one machine |
| Debug overlays | ❌ | ✅ | PWA has timeline/download progress overlays |
| Keyboard shortcuts | ❌ | ✅ | PWA has debug/control shortcuts |
| Config file provisioning | ❌ | ✅ | PWA reads config.json |
| Setup wizard | ✅ | ✅ | Both have setup screens |

---

## 3. Critical Bugs Found in Android Player

### 3.1 Reversed Priority Resolution

**File:** `PlayerEngine.kt` line 351

```kotlin
entry.priority < curPrio -> {  // BUG: should be >
    curPrio = entry.priority
    layouts.clear()
    layouts.add(entry.layoutId)
}
```

The standard Xibo CMS convention is: **higher priority value = higher importance**. The Android player uses `<` (lower priority wins), which means:
- A priority-1 layout overrides a priority-10 layout
- This is the opposite of CMS behavior
- All other players (PWA, Electron, .NET, Linux C++) use higher-priority-wins

### 3.2 Missing `<uri>` Child Element Parsing (Fixed in Plan)

**File:** `XlfParser.kt` — The parser reads `<options>` attributes but the XLF format stores `<uri>` as a child element. This was identified in the existing plan `2026-07-11-001-fix-media-widgets-not-rendering-plan.md` and is being addressed.

### 3.3 Transitions Parsed But Ignored

**File:** `XlfParser.kt` lines 96-98, `LayoutRenderer.kt` — Transition fields (`transitionIn`, `transitionOut`, `transitionDuration`) are parsed from XLF but never applied during rendering. Widgets cut instantly with no animation.

### 3.4 Commands Parsed But Never Executed

**File:** `XmdsClient.kt` lines 66-82, `PlayerEngine.kt` — Commands are parsed from RegisterDisplay response and stored in `PlayerSettings.commands`, but there is no code path that ever executes them. The `XmrMessage.Command` model exists but is never dispatched.

### 3.5 XMR Messages Defined But No Socket Connection

**File:** `Models.kt` lines 147-153 — `XmrMessage` sealed class defines `CollectNow`, `Screenshot`, `Purge`, `WebHook`, and `Command` types, but there is no WebSocket or ZeroMQ client to receive them. The `xmrNetworkAddress` and `xmrChannel`/`xmrPubKey` are fetched from CMS but never used.

---

## 4. Gap Severity Classification

### 🔴 Critical (blocks core functionality)
| Gap | Impact |
|-----|--------|
| Reversed priority resolution | Wrong layout plays during schedule conflicts |
| Missing `<uri>` child element parsing | Media widgets show placeholders instead of content |
| No XMR WebSocket connection | Real-time commands (collectNow, screenshot, layout change) never arrive |
| No command execution | Shell/HTTP/RS232 commands silently ignored |

### 🟡 High (significant feature loss)
| Gap | Impact |
|-----|--------|
| No campaign support | Campaign-grouped layouts don't play correctly |
| No overlay layouts | Multiple simultaneous layouts not supported |
| No transitions | No visual polish between layout/widget changes |
| No interactive control (XIC) | Touch/keyboard/webhook interactions don't work |
| No layout pre-loading | Black screen between layout switches |
| No sub-playlist cycling | Cycle/random playback not supported |
| No dayparting recurrence | Recurring schedules (daily/weekly/monthly) don't work |

### 🟠 Medium (important but not blocking)
| Gap | Impact |
|-----|--------|
| No REST API support | Can't use PlayerRestApi features (JWT, ETag, server-side schedule) |
| No multi-display sync | Video walls not supported |
| No data connectors | Dynamic data-driven widgets don't work |
| No layout blacklisting | Broken layouts keep retrying |
| No download barrier | Videos wait for full download before starting |
| No chunked parallel downloads | Large file downloads are slower |
| No weather/geo criteria | Weather-triggered and location-triggered schedules don't work |
| No interrupt layouts | ShareOfVoice layouts don't play |
| No engagement tracking | Ad impression stats not collected |

### 🔵 Low (nice to have)
| Gap | Impact |
|-----|--------|
| No PDF widget | PDF files show as text placeholder |
| No HLS streaming | HLS video streams not supported |
| No debug overlays | No timeline/download progress visualization |
| No keyboard shortcuts | No debug/control keybindings |
| No config file provisioning | Must use setup screen every time |
| No multi-instance | Can't run multiple Android players on one device |
| No timeline projection | No 2-hour lookahead for debugging |

---

## 5. Summary Statistics

| Metric | Android | PWA |
|--------|:-------:|:---:|
| **Total features** | ~25 | ~85 |
| **Fully implemented** | ~20 | ~80 |
| **Partially implemented** | ~3 | ~2 |
| **Missing** | ~60 | ~0 |
| **Critical bugs** | 4 | 0 |
| **Test count** | 0 | ~1629 |
| **Feature parity** | ~25% | 100% |

---

## 6. Recommended Priority for Implementation

### Phase 1 — Critical Fixes (immediate)
1. Fix reversed priority resolution in `PlayerEngine.kt`
2. Complete media widget URI parsing (already planned)
3. Add XMR WebSocket client for real-time commands
4. Add command execution engine

### Phase 2 — Core Feature Parity (next)
5. Add campaign support to schedule parser
6. Add overlay layout rendering
7. Add CSS/Compose transitions
8. Add interactive control (touch/keyboard actions)
9. Add layout pre-loading pool
10. Add sub-playlist cycling

### Phase 3 — Advanced Features (future)
11. Add REST API support (PlayerRestApi)
12. Add multi-display sync
13. Add data connectors
14. Add dayparting recurrence engine
15. Add download barrier and parallel chunks
16. Add layout blacklisting

### Phase 4 — Polish (stretch)
17. Add PDF widget support
18. Add debug overlays
19. Add config file provisioning
20. Add HLS streaming support
