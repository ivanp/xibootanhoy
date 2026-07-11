# Fix: Media Widgets Not Rendering (Images/Videos)

**Created:** 2026-07-11
**Plan type:** fix
**Target repo:** xibo-android-player

## Problem Frame

The Android player successfully downloads media files (images, videos) from the CMS and caches them on disk, but the rendered layout shows only placeholder text ("Image", "Video") instead of the actual media content.

## Root Cause

The XLF layout XML references media via `<options><uri>filename.ext</uri></options>` — the `uri` is a **child element** of `<options>`, not an attribute. The current `XlfParser.parseWidget()` only reads **attributes** of the `<options>` element (line 108-111), so `widget.uri` is always `null`. Both `ImageWidget` and `VideoWidget` check `widget.uri`, find it null, and render a placeholder.

Additionally, even if the URI were parsed, it's just a bare filename (`26.jpg`). It needs to be resolved to a local `file://` path from the file cache.

## Requirements

- R1: Image widgets must display the cached image file
- R2: Video widgets must play the cached video file
- R3: Widgets must fall back gracefully if the media file is not cached

## Key Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| URI resolution timing | At layout render time, not parse time | The parser is stateless; the renderer has access to context and cache |
| URI format | `file://` absolute paths | Works with both Coil (images) and ExoPlayer (videos) |
| Fallback behavior | Show placeholder with media type name | Matches current behavior, gives visual feedback |

## Implementation Units

### U1. Parse `<uri>` from `<options>` child elements in XlfParser

**Goal:** Extract the `uri` child element from `<options>` and store it in `Widget.uri`.

**Files:**
- `app/src/main/java/org/xiboplayer/player/engine/XlfParser.kt`

**Approach:**
After parsing `<options>` attributes (lines 102-113), also look for a `<uri>` child element within `<options>` and extract its text content. The XLF structure is:

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

### U2. Resolve URI to local file path in LayoutRenderer

**Goal:** Convert the bare filename from `options["uri"]` to a `file://` absolute path using the file cache, and set it on `widget.uri` before passing to `WidgetRenderer`.

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

### U3. Verify rendering on emulator

**Goal:** Confirm images display and videos play on the emulator.

**Files:**
- (no code changes — verification only)

**Approach:**
1. Build and install APK
2. Configure with CMS credentials
3. Wait for collect cycle to complete
4. Observe layout rendering — images should show, videos should play

**Verification:** Visual confirmation via emulator screenshot.

## Dependencies

- U1 must be done before U2 (URI must be parsed before it can be resolved)
- U3 depends on U1 + U2

## Risks

- **Large video files may still cause OOM on emulator** — mitigated by streaming download already implemented
- **ExoPlayer may not support all video codecs** — the CMS videos use standard H.264, which ExoPlayer handles natively
- **WebView-based widgets (HTML, ticker, webpage) may not load HTTP URLs** — the WebView inherits cleartext settings from the app, which already allows cleartext traffic
