# Symera Extensions SDK

Contracts and implementation tools for Symera VOD and IPTV extensions.

**API version:** 4 · **Release:** 4.0.0

## Dependency

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.symeraorg:extensions-lib:4.0.0")
}
```

The host must include the same artifact at runtime. Public SDK dependencies (OkHttp, coroutines, Jsoup, kotlinx.serialization, UniFile) are published transitively.

## Architecture

```
Host (Symera app)
 └─ SymeraExtensionFactory
     ├─ createVodSources(SourceEnvironment) → List<SymeraSource>
     └─ createIptvSources(SourceEnvironment) → List<IptvSource>
```

- **Extensions** map provider data, apply filters, and resolve playback.
- **Host** owns network infrastructure, challenge UI, player, DRM, filesystem, and discovery.
- Complete SDK implementations (e.g. `SymeraHttpSource`) are opt-in adapters; implementing core interfaces directly is valid.

### Entry Point

Each extension exposes one disabled marker service with an intent filter and factory metadata:

```xml
<uses-feature android:name="symera.extension" android:required="false" />
<application>
    <service
        android:name=".SymeraExtensionMarkerService"
        android:enabled="false"
        android:exported="true">
        <intent-filter>
            <action android:name="symera.extension" />
        </intent-filter>
        <meta-data android:name="symera.extension.factory" android:value="com.example.MyExtensionFactory" />
        <meta-data android:name="symera.extension.sdk" android:value="4" />
        <meta-data android:name="symera.extension.nsfw" android:value="false" />
    </service>
</application>
```

The factory class must be a Kotlin `object` or have a public no-argument constructor. R8 rules for extension projects:

```proguard
-keep class * implements org.symera.source.SymeraExtensionFactory {
    public <init>();
    public static ** INSTANCE;
}
```

## VOD

### SymeraSource

Base contract. Required properties: `id: Long`, `name: String`, `lang: String`, `contentTypes: Set<ContentType>`.

`sourceCapabilities: Set<SourceCapability>` declares what playback operations the source supports. The host only calls methods whose capability is advertised.

| Capability | Method | Description |
|---|---|---|
| `PLAYABLE_ITEMS` | `getPlayableItems(SContent)` | Items directly under content |
| `SEASONS` | `getSeasons(SContent)` | Explicit season entries |
| `ITEM_STREAMS` | `getStreams(SPlayableItem)` | Direct stream resolution |
| `HOSTERS` | `getHosters(SPlayableItem)` | Hosters → streams |
| `RELATED_CONTENT` | `getRelated(SContent)` | Direct related items |
| `RELATED_SEARCH` | `getRelatedBySearch(SContent)` | Search-based related |
| `DEFERRED_STREAMS` | via `DeferredStream` in stream lists | Lazy resolution |

`getDetails(SContent)` is always available. `prepareNewPlayableItem(item, content)` is a non-suspend identity function by default.

### SymeraCatalogSource

Extends `SymeraSource` with browsing feeds. `catalogCapabilities: Set<CatalogCapability>` declares available feeds.

| Capability | Method |
|---|---|
| `MOVIES` | `getMovies(PageRequest)` |
| `SERIES` | `getSeries(PageRequest)` |
| `POPULAR` | `getPopular(PageRequest)` |
| `LATEST` | `getLatest(PageRequest)` |
| `SEARCH` | `search(PageRequest, query, filters)` |
| `HOME_SECTIONS` | `getHomeSections()` + `getSectionItems(section, PageRequest)` |

`getFilterList(feed)` and `getFilterList(section)` return the independent schema for each destination. Default: empty.

### Playback Flow

```
item → streams → [DeferredStream → PlayableStream]   (direct)
item → hosters → streams → [DeferredStream → PlayableStream]   (hosted)
```

See [docs/PLAYBACK.md](docs/PLAYBACK.md).

### SymeraHttpSource

Base class for HTTP-based VOD sources. Provides a `Request`/`Parse` pair for every catalog and playback method. Only `contentDetailsParse(Response)` is abstract. All other parse methods are `open` with unsupported defaults.

`ParsedSymeraHttpSource` adds CSS-selector-based HTML parsing. All selectors and element parsers are `open`. Includes `itemStreamsSelector`/`itemStreamFromElement` for direct HTML→stream extraction.

## Filters

`Filter<T>` is open. Available types:

| Filter | State | Description |
|---|---|---|
| `Header` | `Unit` | Section header |
| `Separator` | `Unit` | Visual divider |
| `Text` | `String` | Free text input |
| `CheckBox` | `Boolean` | Toggle |
| `Select<V>` | `Int` (index) | Single selection |
| `MultiSelect<V>` | `Set<Int>` (indices) | Multiple selection |
| `TriState` | `TriStateValue` | Include/Exclude/Ignore |
| `Group<V>` | `List<V>` | Nested container (`FilterContainer`) |
| `Sort` | `SortSelection?` | Sort with ascending/descending |
| `NumberRange` | `Range` | Numeric range |
| `DateRange` | `Range` | Date range |

Keys must be unique across all filters. `FilterList` constructor calls `requireValid()`, which checks mutable state, custom `FilterContainer` children, duplicate keys, and cycles. Subclassing a standard filter attaches site-specific request metadata; it does not create new host widgets.

## Preferences

`SourcePreference<T>` sealed class:

| Type | State | Description |
|---|---|---|
| `Text` | `String` | Text with optional `TextValidation` |
| `Secret` | `String` | Encrypted storage (default must be empty) |
| `Switch` | `Boolean` | Toggle |
| `Number` | `Long` | Numeric with optional min/max |
| `Select` | `String` | Single selection from `Option` list |
| `MultiSelect` | `Set<String>` | Multiple selection |
| `Action` | `Unit` | Button (requires `ActionableSymeraSource`) |
| `Header` | `Unit` | Section header |
| `Separator` | — | Divider |

`enabledWhen: PreferenceCondition?` supports `BooleanValue`, `StringValue`, `StringSetContains`, `LongValue`, `IsNotBlank`, `All`, `Any`, `Not`. Dependency cycles are rejected.

`ConfigurableSymeraSource.sourcePreferenceNamespace` binds its schema to the `SourceEnvironment.preferencesFor(namespace)` store. The default is `source.<stable source id>`; overrides must remain stable and use only ASCII letters, digits, `.`, `_`, or `-`.

## Challenges

Extensions implement `WebChallengeSource` to declare challenge policy. The host supplies the interceptor through `SourceEnvironment.webChallengeInterceptorFactory`; `SymeraHttpSource` installs it automatically.

Challenge detection, response handling, WebView, cookies, coordination, and retries are handled entirely by the host.

See [docs/CHALLENGES.md](docs/CHALLENGES.md).

For a visible browser opened explicitly by the user, implement `InteractiveBrowserSource`. Its `InteractiveBrowserRequest` declares one HTTPS entry URL and up to 16 additional HTTPS top-level origins.

## IPTV

`IptvSource` and `IptvSession` model IPTV independently from VOD. Configuration supports playlist URLs, individual channels, or provider-specific adapters.

### Composition

`IptvSessionServices` composes a channel catalog and live playback resolver. Optional services: groups, EPG, now/next, catch-up, timeshift, dynamic headers, license exchange, refresh. `capabilities` is derived from which services are non-null.

```
IptvSessionServices(
    channels: IptvChannelCatalog,           // required
    playback: IptvPlaybackServices,         // required (live + optional catch-up/timeshift)
    groups, epg, nowNext, dynamicHeaders,   // optional
    licenseExchange, refresher, clock       // optional
)
```

### Playback

`IptvPlaybackServices` dispatches to live, catch-up, or timeshift resolvers based on intent mode. Each resolver returns an `IptvPlaybackRequest` with URI, protocol, headers, and DRM.

### Playlist Parsers

`ExtendedM3uParser` and `XmlTvParser` are reusable extension tools. Playlist loading, authentication, persistence, merging, and session orchestration belong to the host app.

See [docs/IPTV.md](docs/IPTV.md).

## Torrent

Bencode parsing, BitTorrent v1/v2/hybrid metainfo, `btih`/`btmh` magnets, BEP 53 file selection, trackers, web seeds, and size-limited HTTP loading. Magnet parsing never fabricates a file list or size. Authenticated `.torrent` requests accept an OkHttp `Request`; redirects drop extension headers on origin change and reject HTTPS downgrade.

## SourceEnvironment

Host-provided infrastructure injected into every source:

| Property | Type | Description |
|---|---|---|
| `httpClient` | `OkHttpClient` | Shared HTTP client |
| `userAgent` | `String` | User-Agent string |
| `appInfo` | `HostAppInfo` | Version code, version name, SDK version |
| `logger` | `SourceLogger` | Debug/warning/error logging |
| `webChallengeInterceptorFactory` | `WebChallengeInterceptorFactory?` | Optional challenge interceptor |
| `localFileSystem` | `LocalSourceFileSystem?` | Optional local file access |
| `javaScriptEngineFactory` | `JavaScriptEngineFactory?` | Optional host-owned JS execution abstraction |

`LocalSourceFileSystem` interface: `requireBaseDirectory`, `getFilesInBaseDirectory`, `getContentDirectories`, `getContentDirectory(name)`, `walk(dir, maxDepth, maxEntries)`, `getPlayableFiles(dir, extensions, maxDepth, maxEntries)`, `getSidecarSubtitles(video)`.

## Local Storage

`LocalMediaDefaults` provides defaults: `MAXIMUM_DEPTH = 8`, `MAXIMUM_ENTRIES = 100_000`, standard video/subtitle extensions. The host supplies the `LocalSourceFileSystem` implementation and owns SAF permissions and traversal policy.

## Quality

- Kotlin warnings are build errors.
- `checkPublicApi` compares the release AAR against the reviewed `api/extensions-lib.api` ABI snapshot.
- Unit tests cover VOD capabilities, mutable filters, composed IPTV, M3U, XMLTV/XXE, catch-up credential scoping, URI policy, secret redaction, and BitTorrent v1/v2.
- HTTP coroutine cancellation cancels the underlying OkHttp call.
- XMLTV parsing disables external entities and DTD loading.

## License

MPL-2.0
