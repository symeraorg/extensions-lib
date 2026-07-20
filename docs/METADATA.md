# Metadata Mapping

Extensions map provider data. The SDK does not enrich results unless the provider itself exposes the value.

## Required Fields

| Model | Required | Optional |
|---|---|---|
| `SContent` | `url`, `title` | `originalTitle`, `alternativeTitles`, `description`, `posterUrl`, `backdropUrl`, `genres`, `tags`, `contentType`, `categories`, `status`, `release`, `rating`, `durationMillis`, `ageRating`, `countries`, `languages`, `credits`, `externalIds`, `structure`, `updateStrategy`, `seasonCount`, `episodeCount`, `attributes` |
| `SSeason` | `url`, `number` | `title`, `description`, `posterUrl`, `airDate`, `playableItems`, `attributes` |
| `SPlayableItem` | `url`, `type` | `title`, `episodeNumber`, `seasonNumber`, `absoluteEpisodeNumber`, `summary`, `thumbnailUrl`, `airDate`, `publishedAtEpochMillis`, `durationMillis`, `isFiller`, `attributes` |
| `Episode` item | all `SPlayableItem` requirements + `episodeNumber: EpisodeNumber` | `absoluteEpisodeNumber`, `isFiller` |
| `SHoster` | `id`, `name` | `requestUrl`, `streams`, `resolverData` |
| `DeferredStream` | `id`, `resolverData` | `title`, `preferred` |
| `PlayableStream` | `id`, `request: MediaRequest` | `protocol`, `hints`, `drm`, `subtitleTracks`, `audioTracks`, `timestamps`, `preferred` |

### EpisodeNumber

`BigDecimal` avoids `Float` precision loss. Values like `12.5` are valid. Specials without a meaningful number use `PlayableItemType.SPECIAL`, not a fabricated episode number.

### SSeason

Emitted only when the source explicitly groups content by season. Flat anime sources return items from `getPlayableItems(SContent)` directly.

## Presence Rules

- `null` for absent scalars, empty collections for absent lists.
- Do not use placeholder text (`Unknown`, `N/A`, `Episode ?`).
- `SourceDate` for calendar dates (no time zone, works on API 24+).
- `ContentRating` holds provider rating scale and name.
- `ExternalId` for TMDB, IMDb, TVDB, AniList, MyAnimeList identifiers when known.
- `attributes` for provider-specific data without a stable SDK field. Hosts must not depend on arbitrary attributes for core behavior.

## ContentPage

```kotlin
data class ContentPage(
    val contents: List<SContent>,
    val hasNextPage: Boolean,
    val nextCursor: String? = null,
    val totalCount: Long? = null,
)
```

## Classification

`ContentType`: `MOVIE`, `SERIES`, `SHORT`, `OTHER`. Describes structure.

`ContentCategory`: `ANIME`, `CARTOON`, `DOCUMENTARY`, `LIVE_ACTION`. Describes genre independently of structure.

`ContentStructure`: `SINGLE_ITEM`, `FLAT_ITEMS`, `SEASONS`, `UNKNOWN`. Describes source navigation, not external metadata grouping.
