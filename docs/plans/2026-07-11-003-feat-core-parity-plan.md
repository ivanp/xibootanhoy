# Plan: Add Core Feature Parity to xibo-android-player

**Created:** 2026-07-11
**Plan type:** feat
**Target repo:** xibo-android-player
**Sequence:** 003
**Origin:** `docs/gap-analysis.md` (Phase 2)

---

## Problem Frame

The xibo-android-player has critical bugs fixed (Phase 1) but still lacks core feature parity with the PWA player. Six major features are missing:

1. **Campaign support** — The schedule parser only handles standalone layouts. Campaigns (groups of layouts sharing a priority) are ignored, so campaign-grouped content never plays.
2. **Overlay layouts** — The player renders one layout at a time. Overlay layouts (e.g., emergency alerts on top of normal content) are not supported.
3. **Transitions** — Transition fields are parsed from XLF but ignored. Widgets and layouts cut instantly with no animation.
4. **Interactive control (XIC)** — Touch actions, keyboard actions, and webhook triggers are not handled. The player is display-only.
5. **Layout pre-loading** — Layouts are loaded and parsed on switch, causing a black screen between transitions.
6. **Sub-playlist cycling** — Widgets with `cycle` or `playCount` attributes don't cycle or repeat properly.

---

## Requirements

| ID | Requirement | Source |
|:---|:-----------|:-------|
| R1 | Campaign XML from schedule must be parsed into grouped layouts with shared priority | gap-analysis §2.2 |
| R2 | Campaign layouts must cycle in XML order within the campaign | gap-analysis §2.2 |
| R3 | Overlay layouts must render on top of the current layout with proper z-ordering | gap-analysis §2.2 |
| R4 | Overlay layouts must auto-dismiss after their configured duration | gap-analysis §2.2 |
| R5 | Widget-level transitions (fade, fly) must animate between widget changes | gap-analysis §2.4 |
| R6 | Layout-level transitions (fade, slide, wipe) must animate between layout switches | gap-analysis §2.4 |
| R7 | Touch actions on widgets must trigger configured navigation/actions | gap-analysis §2.5 |
| R8 | Keyboard actions must trigger configured navigation/actions | gap-analysis §2.5 |
| R9 | Layouts must be pre-loaded in the background before the current layout expires | gap-analysis §2.2 |
| R10 | Widgets with cycle/playCount must cycle through sub-playlists | gap-analysis §2.2 |

---

## Key Technical Decisions

| Decision | Choice | Rationale |
|:---------|:-------|:----------|
| Campaign data model | Add `Campaign` data class with `id`, `priority`, `layoutIds` list | Matches XLF campaign XML structure |
| Schedule parsing | Parse `<campaign>` elements alongside `<layout>` elements in XMDS response | Minimal change to existing parser |
| Overlay rendering | Separate `OverlayRenderer` composable with z-index stacking | Clean separation from main layout renderer |
| Transition engine | Compose `AnimatedContent` and `AnimatedVisibility` | Native Compose animation APIs, no extra dependencies |
| Touch actions | `Modifier.pointerInput` with `detectTapGestures` on widget composables | Standard Compose gesture handling |
| Keyboard actions | `onKeyEvent` modifier on root player composable | Standard Compose key handling |
| Layout pre-loading | `LayoutPool` class with hot/warm states, pre-load at 75% of current duration | Matches PWA LayoutPool pattern |
| Sub-playlist cycling | Parse `cycle`/`playCount` from widget options, cycle widgets in region | Matches PWA sub-playlist behavior |

---

## Implementation Units

### U1. Add campaign data model and schedule parsing

**Goal:** Parse campaign XML from the CMS schedule response and store campaign data alongside standalone layouts.

**Requirements:** R1, R2

**Dependencies:** None

**Files:**
- `app/src/main/java/org/xiboplayer/player/model/Models.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/api/XmdsClient.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/engine/PlayerEngine.kt` (modify)

**Approach:**

1. Add `Campaign` data class to `Models.kt`:
```kotlin
data class Campaign(
    val id: Long,
    val priority: Int,
    val layoutIds: List<Long>
)
```

2. Update `Schedule` to include campaigns:
```kotlin
data class Schedule(
    val default: Long? = null,
    val entries: List<ScheduleEntry> = emptyList(),
    val campaigns: List<Campaign> = emptyList()
)
```

3. In `XmdsClient.getSchedule()`, parse `<campaign>` elements from the schedule XML. Each campaign has:
   - `id` attribute
   - `priority` attribute
   - Child `<layout>` elements with `file` attribute (layout ID)

4. In `PlayerEngine.layoutsNow()`, after filtering active entries by time window:
   - Collect active campaigns (those whose entries are active)
   - Find max priority among active entries + campaigns
   - Select all entries and campaigns at max priority
   - For campaigns, flatten their layout IDs into the result list
   - For standalone entries, add their layout ID

**Test scenarios:**
- Schedule with one campaign (2 layouts, priority 10) and one standalone (priority 5) → campaign layouts play (higher priority)
- Schedule with two campaigns at same priority → all layouts from both campaigns cycle
- Schedule with campaign and standalone at same priority → both campaign layouts and standalone layout cycle together
- Schedule with no active campaigns → falls back to standalone entries
- Empty schedule → default layout returned

**Verification:** Log output showing campaign layouts selected and cycling.

---

### U2. Add overlay layout rendering

**Goal:** Support rendering overlay layouts on top of the current layout with proper z-ordering and auto-dismiss.

**Requirements:** R3, R4

**Dependencies:** None

**Files:**
- `app/src/main/java/org/xiboplayer/player/model/Models.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/engine/PlayerEngine.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/renderer/LayoutRenderer.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/ui/screens/PlayerScreen.kt` (modify)

**Approach:**

1. Add overlay state to `PlayerEngine`:
   - `_overlayState: MutableStateFlow<LayoutState?>` — null when no overlay, `Showing(layout)` when active
   - `showOverlay(layoutId: Long, duration: Long?)` method
   - `dismissOverlay()` method
   - Auto-dismiss timer using `delay(duration)` in coroutine

2. Wire XMR `overlayLayout` handler to call `showOverlay()`

3. In `PlayerScreen.kt`, render overlay on top of main layout:
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // Main layout
    LayoutRenderer(layout = mainLayout, ...)
    
    // Overlay layout (on top)
    overlayLayout?.let {
        LayoutRenderer(
            layout = it,
            modifier = Modifier.zIndex(10f),
            ...
        )
    }
}
```

4. Overlay layouts use the same `LayoutRenderer` but with a higher z-index and a semi-transparent background if configured.

**Test scenarios:**
- XMR `overlayLayout` message → overlay appears on top of current layout
- Overlay auto-dismisses after configured duration
- Multiple overlays stack correctly (last one on top)
- Overlay dismissed before duration expires → removed immediately
- No overlay → main layout renders normally

**Verification:** Visual confirmation via emulator + log output showing overlay lifecycle.

---

### U3. Add Compose transitions for widgets and layouts

**Goal:** Animate widget changes within regions and layout switches using Compose animation APIs.

**Requirements:** R5, R6

**Dependencies:** None

**Files:**
- `app/src/main/java/org/xiboplayer/player/renderer/WidgetRenderer.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/renderer/LayoutRenderer.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/ui/screens/PlayerScreen.kt` (modify)

**Approach:**

1. **Widget-level transitions** in `RegionRenderer`:
   - Wrap widget content in `AnimatedContent` with `transitionSpec`
   - Parse `transitionIn`/`transitionOut` from widget options
   - Map XLF transition names to Compose animations:
     - `fade` → `fadeIn()` / `fadeOut()`
     - `fly` → `slideInHorizontally()` / `slideOutHorizontally()` with direction
     - Default → `fadeIn()` / `fadeOut()`

2. **Layout-level transitions** in `PlayerScreen`:
   - Wrap `LayoutRenderer` in `AnimatedContent` keyed on layout ID
   - Parse `layoutTransitionIn` from layout XLF
   - Support: `fade` (cross-fade), `slide` (slide in/out), `instant` (no animation)

3. Transition duration from XLF `transitionDuration` attribute.

**Test scenarios:**
- Widget with `transitionIn="fade"` → fades in over configured duration
- Widget with `transitionIn="fly" direction="L"` → slides in from left
- Layout switch with `fade` transition → cross-fade between layouts
- Layout switch with `slide` transition → slides in from right
- No transition configured → instant cut (no animation)
- Transition duration of 0 → instant cut

**Verification:** Visual confirmation via emulator showing smooth transitions.

---

### U4. Add interactive control (touch and keyboard actions)

**Goal:** Handle touch actions on widgets and keyboard actions to trigger navigation, layout changes, and other configured actions.

**Requirements:** R7, R8

**Dependencies:** None

**Files:**
- `app/src/main/java/org/xiboplayer/player/model/Models.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/renderer/WidgetRenderer.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/ui/screens/PlayerScreen.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/engine/PlayerEngine.kt` (modify)

**Approach:**

1. Add action data model:
```kotlin
data class WidgetAction(
    val triggerType: String,  // "touch", "keyboard"
    val triggerCode: String? = null,  // key code for keyboard
    val actionType: String,  // "navLayout", "navWidget", "next", "previous"
    val targetId: Long? = null,  // layout or widget ID
    val targetCode: String? = null  // trigger code for webhooks
)
```

2. Parse actions from XLF widget elements. Actions are stored in `<action>` child elements within `<media>`.

3. In `WidgetRenderer`, add touch handler:
```kotlin
Modifier.pointerInput(widget.id) {
    detectTapGestures { widgetActions.forEach { handleAction(it) } }
}
```

4. In `PlayerScreen`, add keyboard handler:
```kotlin
Modifier.onKeyEvent { event ->
    if (event.type == KeyEventType.KeyUp) {
        handleKeyboardAction(event.key)
    }
    false
}
```

5. Action handling in `PlayerEngine`:
   - `navLayout` → `jumpToLayout(targetId)`
   - `navWidget` → navigate to specific widget in current region
   - `next` → `nextLayout()`
   - `previous` → navigate to previous layout
   - `webhook` → fire webhook callback (stub for now)

**Test scenarios:**
- Widget with touch action `navLayout` → tapping navigates to target layout
- Widget with touch action `next` → tapping advances to next layout
- Keyboard shortcut `→` → advances to next layout
- Keyboard shortcut `←` → goes to previous layout
- Widget with no actions → no touch handler attached
- Unknown action type → logged and ignored

**Verification:** Log output showing action trigger and navigation.

---

### U5. Add layout pre-loading pool

**Goal:** Pre-load the next layout in the background before the current layout expires, eliminating the black screen between transitions.

**Requirements:** R9

**Dependencies:** None

**Files:**
- `app/src/main/java/org/xiboplayer/player/engine/LayoutPool.kt` (new)
- `app/src/main/java/org/xiboplayer/player/engine/PlayerEngine.kt` (modify)

**Approach:**

1. Create `LayoutPool` class:
   - Maintains up to 2 layouts: hot (currently visible) and warm (pre-loaded, hidden)
   - `preload(layoutId: Long)` — parses XLF and caches the parsed layout
   - `swap(layoutId: Long)` — promotes warm to hot, returns the layout
   - `evict(layoutId: Long)` — removes a layout from the pool
   - Uses `XlfParser` to parse layouts on pre-load

2. In `PlayerEngine`:
   - After schedule evaluation, identify the next layout to play
   - When current layout is at 75% of its duration, trigger pre-load of the next layout
   - On layout switch, use the pre-loaded layout from the pool
   - If the pool doesn't have the layout, fall back to loading it synchronously

3. Pre-loading timing:
   - Track current layout start time
   - Calculate 75% of layout duration
   - Launch pre-load coroutine at that point
   - Retry at 90% if first attempt failed

**Test scenarios:**
- Two layouts in schedule → second layout pre-loaded before first expires
- Pre-loaded layout available on switch → instant transition (no black screen)
- Pre-load fails → fall back to synchronous load
- Single layout → no pre-loading needed
- Layout pool eviction → old layout freed from memory

**Verification:** Log output showing pre-load timing and pool state.

---

### U6. Add sub-playlist cycling

**Goal:** Support widget cycling with `cycle` and `playCount` options, where a group of widgets cycles round-robin or randomly within a region.

**Requirements:** R10

**Dependencies:** None

**Files:**
- `app/src/main/java/org/xiboplayer/player/engine/XlfParser.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/renderer/LayoutRenderer.kt` (modify)
- `app/src/main/java/org/xiboplayer/player/model/Models.kt` (modify)

**Approach:**

1. Parse sub-playlist options from widget XLF:
   - `cycle` — if "true", widgets in the same region cycle
   - `playCount` — number of times to play before advancing
   - `random` — if "true", random selection instead of round-robin

2. In `RegionRenderer`, modify widget cycling logic:
   - If `cycle` is true, cycle through all widgets in the region
   - If `playCount` is set, repeat each widget N times before advancing
   - If `random` is true, select widgets randomly (with no-repeat until all played)
   - Default behavior (no cycle) — play each widget once in order

3. Track play count per widget in region state.

**Test scenarios:**
- Region with 3 widgets, `cycle=true` → widgets cycle indefinitely
- Region with 2 widgets, `playCount=3` → each widget plays 3 times before advancing
- Region with `random=true` → widgets play in random order
- Region with no cycle options → each widget plays once in order
- Single widget in region → no cycling needed

**Verification:** Log output showing widget cycling order and play counts.

---

## Dependencies

```
U1 (campaigns) — standalone
U2 (overlays) — standalone
U3 (transitions) — standalone
U4 (interactive control) — standalone
U5 (layout pre-loading) — standalone
U6 (sub-playlist cycling) — standalone
```

All six units are independent and can be implemented in any order.

---

## Risks

| Risk | Likelihood | Impact | Mitigation |
|:-----|:-----------|:-------|:-----------|
| Campaign XML format varies by CMS version | Medium | Medium | Parse both `<campaign>` and legacy formats, log unknown structures |
| Overlay z-ordering conflicts with WebView widgets | Low | Medium | WebView widgets use SurfaceView which may render on top; document limitation |
| Compose animation performance on low-end devices | Medium | Medium | Default to instant cut on low-end devices, make transitions configurable |
| Touch actions conflict with settings long-press | Low | Low | Use single-tap for actions, long-press for settings (already implemented) |
| Layout pre-loading increases memory usage | Medium | Low | Pool size capped at 2 layouts, evict cold layouts immediately |
| Sub-playlist cycle detection is heuristic | Low | Low | Default to sequential playback if cycle options are ambiguous |

---

## Open Questions

| Question | Impact | Status |
|:---------|:-------|:-------|
| Should overlay layouts support semi-transparent backgrounds? | Visual behavior | Deferred — implement basic overlay first, add transparency later |
| Should keyboard shortcuts be configurable? | UX | Deferred — hardcode common shortcuts first |
| What is the exact XLF action XML structure? | Action parsing | Deferred — inspect real CMS output during implementation |
