# IPTV Extension Model

IPTV is a separate domain from VOD. `IptvSource` and `IptvSession` model live television, catch-up, and timeshift independently from movies and episodes.

## Configuration

`IptvCatalogConfiguration` supports three shapes:

- **`Playlists`**: one or more `IptvPlaylistLocation` values (URL + optional headers + EPG locations).
- **`Channels`**: individually configured channels.
- **`Provider`**: extension-owned adapter with a base URI and custom normalization.

## Session Composition

`IptvSessionServices` composes a session from individually optional services:

```kotlin
data class IptvSessionServices(
    val channels: IptvChannelCatalog,           // required
    val playback: IptvPlaybackServices,         // required (live + optional catch-up/timeshift)
    val groups: IptvGroupCatalog? = null,
    val epg: IptvEpgProvider? = null,
    val nowNext: IptvNowNextProvider? = null,
    val dynamicHeaders: IptvDynamicHeaderProvider? = null,
    val licenseExchange: IptvLicenseExchanger? = null,
    val refresher: IptvRefresher? = null,
    val clock: IptvClock = IptvClock.SYSTEM,
)
```

`capabilities` is derived automatically from which services are non-null and which `IptvChannelKind` values are present.

### Playback Dispatch

```kotlin
data class IptvPlaybackServices(
    val live: IptvLivePlaybackResolver,
    val catchUp: IptvCatchUpResolver? = null,
    val timeshift: IptvTimeshiftResolver? = null,
)
```

`resolve(channel, intent, now, timeZoneId)` dispatches to the correct resolver based on `IptvPlaybackMode` (LIVE, CATCH_UP, TIMESHIFT). Returns `IptvError.Unsupported` when the mode is unavailable.

### CompositeIptvSession

```kotlin
class CompositeIptvSession(
    override val configuration: IptvConfiguration,
    services: IptvSessionServices,
    onClose: () -> Unit = {},
) : IptvSession
```

Thread-safe via `AtomicBoolean`. All operations return `IptvError.Cancelled` after `close()`. Provider adapters implement this class and expose only the services they support.

## ConfiguredIptvSource

Ready-to-use implementation for playlist URLs and individually configured channels.

### Components

`ConfiguredIptvComponents` makes every strategy injectable:

| Component | Default | Purpose |
|---|---|---|
| `playlistParser` | `ExtendedM3uParser` | Parse M3U/M3U8 playlists |
| `epgParser` | `XmlTvParser` | Parse XMLTV EPG |
| `channelIdentity` | `entry.channel.id` | Generate stable channel IDs |
| `catalogMerger` | `FirstWinsIptvCatalogMerger` | Merge multiple playlist parts |
| `epgMatcher` | match by `epgId` or `channel.id` | Associate EPG data with channels |
| `catchUpResolver` | `TemplateIptvCatchUpResolver` | Resolve catch-up archive URLs |
| `timeshiftResolver` | `WindowIptvTimeshiftResolver` | Validate timeshift offsets |
| `clock` | `IptvClock.SYSTEM` | Current time provider |

Extensions replace only the components they need without overriding a monolithic session class.

### ExtendedM3uParser

Parses quoted attributes, commas, `tvg-*` attributes, groups, radio, catch-up, timeshift, VLC options, URL headers, relative URLs, and unknown attribute preservation. Rejects HLS manifests as catalogs. Strict/lenient diagnostic modes available.

### XMLTV Parsing

`XmlTvParser` is streaming and XXE-protected. Supports channels, localized text, programmes, categories, icons, episode identifiers, ratings, numeric UTC offsets, named zones, optional stop times, and configurable limits for maximum channels and programmes. Now/next uses half-open `[start, stop)` ranges.

XMLTV reference: https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd

## Playback

Channels resolve to `IptvPlaybackRequest` containing URI, protocol, headers, and DRM descriptors.

Header rules target: manifests, segments, keys, licenses, subtitles, images. Widevine, ClearKey, PlayReady, and custom DRM exchanges are described without importing player classes.

Catch-up requests never inherit static headers, referrer, User-Agent, or dynamic authorization when the compiled URI changes origin. Inline catch-up headers are scoped to the archive origin only.

Playlist, EPG, image, referrer, and license resources require HTTP(S) URIs. RTSP/RTP/UDP are accepted only for matching playback protocols.

## Authentication

`ConfiguredIptvAuthenticator` owns both the UI descriptor and generated transport headers.

### Built-in Factories

| Factory | Scheme | Fields | Headers |
|---|---|---|---|
| `none()` | NONE | — | — |
| `httpBasic(usernameKey, passwordKey, allowedOrigins?)` | HTTP_BASIC | username (TEXT), password (SECRET) | `Authorization: Basic <credentials>` |
| `bearerToken(tokenKey, allowedOrigins?)` | BEARER_TOKEN | token (SECRET) | `Authorization: Bearer <token>` |
| `apiKey(headerName, credentialKey, allowedOrigins?)` | API_KEY | apiKey (SECRET) | `<headerName>: <key>` |

Generated headers are scoped to the canonical origin of the configuration's resource URIs (or explicit `allowedOrigins`). Cross-origin EPG does not receive credentials. Static location headers override authenticator headers. Secret header references override both.

### Dynamic Authorization

`IptvAuthorizationHandle` is bound to explicit origins. The SDK rejects use against another origin and validates provider-returned headers before exposing them.

### Provider Adapters

Xtream-like APIs do not have a stable public specification. Implement them behind a provider adapter and normalize categories, channels, EPG, archive availability, and playback into the core IPTV models. Credentials belong to host encrypted storage and must be redacted from logs.

`toString()` is redacted on: `IptvPlaybackRequest`, `IptvHeaderRule`, `IptvLicenseRequest`, `IptvConfiguration`, `IptvChannel`, `IptvAuthorizationHandle`.
