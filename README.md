# Symera Extensions Library

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.symeraorg:extensions-lib:<tag>")
}
```

```groovy
dependencies {
    compileOnly "org.symera:extensions-lib:1"
}
```

## API Overview

### Source contracts (`org.symera.source`)

| Interface | Purpose |
|---|---|
| `SymeraSource` | Base contract every source must implement |
| `SymeraCatalogSource` | Adds movie and series catalog browsing, search, home sections |
| `ConfigurableSymeraSource` | Adds app-rendered preferences |
| `SymeraSourceFactory` | Factory for multi-source extensions |
| `UnmeteredSource` | Marker for self-hosted / local-network sources |

### HTTP sources (`org.symera.source.online`)

| Class | Purpose |
|---|---|
| `SymeraHttpSource` | Base for sources backed by an HTTP website or API |
| `ParsedSymeraHttpSource` | Convenience base for HTML sources using Jsoup selectors |
| `ResolvableSymeraSource` | Deep-link support: resolve a URI to content or playable items |

### Models (`org.symera.source.model`)

| Type | Description |
|---|---|
| `SContent` | A movie, series, anime, or other content item |
| `SPlayableItem` | An individual playable episode or video |
| `SSeason` | A season grouping playable items |
| `SHoster` | A hosting provider with a list of streams |
| `SStream` | A single playable stream with optional tracks |
| `ContentPage` | Paginated catalog result |
| `HomeSection` | A named section on the source's home page |
| `Filter`, `FilterList` | Search/browse filter hierarchy |
| `SourcePreference` | Preference UI model for configurable sources |
| `UpdateStrategy`, `FetchType` | Optional host hints for update and fetch behavior |

### Utilities

| Extension | Description |
|---|---|
| `HttpExtensions.kt` | `GET`/`POST`, `awaitSuccess` (async HTTP), `asJsoup`, `bodyString` |
| `network/*` | Request helpers, OkHttp response helpers, cache/cookie network helper, JavaScript engine |
| `util/*` | Coroutine, JSON, and Jsoup helpers for extension implementations |

### Network and JavaScript

`SymeraHttpSource` exposes both `client` and `cloudflareClient`. The host app can provide these through `defaultClientProvider` and `defaultCloudflareClientProvider`, allowing extensions to opt into Cloudflare-capable behavior without bundling network infrastructure.

`JavaScriptEngine` wraps Android WebView for pages or hosts that require JavaScript execution before stream URLs can be extracted.

### Host app info (`org.symera`)

| Class | Description |
|---|---|
| `AppInfo` | Version code and name of the host Symera application |

## Creating an Extension

Every extension must provide at least one source implementing `SymeraSource`. Sources are discovered at runtime via the manifest metadata constants in `SymeraExtensionMetadata`.

Catalog sources expose `getMovies(page)` and `getSeries(page)` as first-class browse entry points. `search(page, query, filters)` and `getFilterList()` remain independent and are still used for text search and filtered browsing.

Current SDK version: `1`.

## License

MPL-2.0
