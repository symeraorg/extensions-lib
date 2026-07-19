# Playback Contract

The extension describes a media resource. The host owns Media3 objects, HTTP data sources, track selection, decoder policy, caching, DRM sessions, audio output, and retries.

## Stream Types

`SStream` is a sealed interface with two implementations:

| Type | Resolution | Use Case |
|---|---|---|
| `PlayableStream` | Fully resolved | Direct stream with URI, protocol, hints, DRM, tracks |
| `DeferredStream` | Lazy | Resolver data for host-driven resolution |

### SStream hierarchy

```kotlin
sealed interface SStream {
    val id: String
    val title: String?
    val preferred: Boolean
}

data class PlayableStream(
    val id: String,
    val title: String? = null,
    val request: MediaRequest,
    val protocol: StreamProtocol = StreamProtocol.AUTO,
    val hints: StreamHints = StreamHints(),
    val drm: StreamDrm? = null,
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val timestamps: List<StreamTimestamp> = emptyList(),
    val preferred: Boolean = false,
) : SStream

data class DeferredStream(
    val id: String,
    val title: String? = null,
    val resolverData: String,
    val preferred: Boolean = false,
) : SStream

```

### Hoster

```kotlin
data class SHoster(
    val id: String,
    val name: String,
    val requestUrl: String? = null,
    val streams: List<SStream>? = null,   // null = needs getStreams(hoster); empty = resolved empty
    val resolverData: String? = null,
)
```

## Playback Flow

Two supported paths:

```
item → streams → [DeferredStream → PlayableStream]
item → hosters → streams → [DeferredStream → PlayableStream]
```

Direct providers call `getStreams(SPlayableItem)`. Hosted providers call `getHosters(SPlayableItem)` then `getStreams(SHoster)`. A hoster with non-null `streams` skips the network call entirely.

`DeferredStream` contains opaque `resolverData`. The host calls `StreamResolver.resolveStream(deferred)` and uses the returned `PlayableStream`.

## MediaRequest

```kotlin
data class MediaRequest(
    val uri: String,
    val method: HttpMethod = HttpMethod.GET,
    val body: ByteArray? = null,
    val headers: List<HttpHeader> = emptyList(),
    val headerScope: HeaderScope = HeaderScope.SAME_ORIGIN_DERIVED_REQUESTS,
)
```

Constraints:
- `uri` must not be blank.
- `body` is only valid when `method == POST`.
- `HeaderScope.PRIMARY_REQUEST_ONLY`: headers apply only to this request.
- `SAME_ORIGIN_DERIVED_REQUESTS` (default): headers propagate to same-origin redirects and segment/key requests.
- `ALL_DERIVED_REQUESTS`: headers propagate everywhere (including cross-origin).

DRM licenses have their own `MediaRequest` inside `StreamDrm`. Credentials must not be forwarded to cross-origin segments, subtitles, images, or DRM endpoints unless explicitly scoped.

## Stream Hints

```kotlin
data class StreamHints(
    val containerMimeType: String? = null,
    val codecs: List<String> = emptyList(),   // RFC 6381 values
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val bitrateBitsPerSecond: Long? = null,
)
```

Hints do not override tracks discovered by Media3 from a manifest or extractor.

## DRM

```kotlin
data class StreamDrm(
    val scheme: DrmScheme,                         // WIDEVINE, CLEARKEY, PLAYREADY, CUSTOM
    val customSchemeUuid: String? = null,          // required when scheme == CUSTOM
    val licenseRequest: MediaRequest? = null,
    val licenseUriPolicy: LicenseUriPolicy = LicenseUriPolicy.MANIFEST,
    val multiSession: Boolean = false,
)
```

`LicenseUriPolicy`: `MANIFEST` (use license URL from manifest), `FALLBACK` (use if manifest has none), `OVERRIDE` (always use `licenseRequest`).

## Subtitles

```kotlin
data class SubtitleTrack(
    val id: String,
    val request: MediaRequest,
    val language: String? = null,
    val label: String? = null,
    val format: SubtitleFormat = SubtitleFormat.UNKNOWN,   // WEBVTT, TTML, SUBRIP, SSA_ASS
    val roles: Set<SubtitleRole> = emptySet(),             // SUBTITLE, CAPTION, FORCED, HEARING_IMPAIRED, DESCRIPTION
    val default: Boolean = false,
)
```

## Audio Tracks

```kotlin
data class AudioTrack(
    val id: String,
    val request: MediaRequest,
    val language: String? = null,
    val label: String? = null,
    val mimeType: String? = null,
    val codecs: List<String> = emptyList(),
    val roles: Set<AudioRole> = emptySet(),               // MAIN, ALTERNATE, COMMENTARY, DUB, DESCRIPTION
    val default: Boolean = false,
)
```

## Timestamps

```kotlin
data class StreamTimestamp(
    val startMillis: Long,
    val endMillis: Long? = null,
    val title: String,
    val type: StreamTimestampType = StreamTimestampType.OTHER,  // OPENING, ENDING, RECAP, MIXED, OTHER
)
```

## Protocol

`StreamProtocol`: `AUTO`, `PROGRESSIVE`, `HLS`, `DASH`, `SMOOTH_STREAMING`, `RTSP`, `TORRENT`. The host maps this to Media3, ExoPlayer, or another player stack. Extensions do not select decoders, renderers, or FFmpeg usage.
