package org.symera.source.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ContentModelsTest {
    @Test
    fun flatAnimeDoesNotRequireInventedSeason() {
        val anime = SContent(
            url = "/anime/example",
            title = "Example",
            contentType = ContentType.SERIES,
            categories = setOf(ContentCategory.ANIME),
            structure = ContentStructure.FLAT_ITEMS,
        )
        val episode = SPlayableItem(
            url = "/anime/example/1",
            title = "Episode 1",
            type = PlayableItemType.EPISODE,
            episodeNumber = EpisodeNumber(1),
        )

        assertEquals(ContentStructure.FLAT_ITEMS, anime.structure)
        assertEquals(null, episode.seasonNumber)
    }

    @Test
    fun singleItemRequiresExactlyOnePlayableItem() {
        val content = SContent(
            url = "/movie/example",
            title = "Example",
            contentType = ContentType.MOVIE,
            structure = ContentStructure.SINGLE_ITEM,
        )
        val item = SPlayableItem(
            url = "/movie/example/play",
            type = PlayableItemType.MOVIE,
        )

        content.requireValidPlayableItems(listOf(item))
        assertThrows(IllegalArgumentException::class.java) {
            content.requireValidPlayableItems(emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            content.requireValidPlayableItems(listOf(item, item.copy(url = "/movie/example/extra")))
        }
    }

    @Test
    fun multipartMovieRequiresDistinctMovieItems() {
        val content = SContent(
            url = "/movie/example",
            title = "Example",
            contentType = ContentType.MOVIE,
            structure = ContentStructure.FLAT_ITEMS,
        )
        val firstPart = SPlayableItem(url = "/movie/example/part-1", type = PlayableItemType.MOVIE)
        val secondPart = SPlayableItem(url = "/movie/example/part-2", type = PlayableItemType.MOVIE)

        content.requireValidPlayableItems(listOf(firstPart, secondPart))
        assertThrows(IllegalArgumentException::class.java) {
            content.requireValidPlayableItems(listOf(firstPart))
        }
        assertThrows(IllegalArgumentException::class.java) {
            content.requireValidPlayableItems(
                listOf(firstPart, secondPart.copy(type = PlayableItemType.EPISODE, episodeNumber = EpisodeNumber(2))),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            content.requireValidPlayableItems(listOf(firstPart, firstPart.copy(title = "Duplicate")))
        }
    }

    @Test
    fun episodeRequiresNumber() {
        assertThrows(IllegalArgumentException::class.java) {
            SPlayableItem(
                url = "/series/example/episode",
                title = "Episode",
                type = PlayableItemType.EPISODE,
            )
        }
    }

    @Test
    fun explicitSeasonCanInlineItems() {
        val item = SPlayableItem(
            url = "/series/example/s2/e1",
            title = "Episode 1",
            type = PlayableItemType.EPISODE,
            seasonNumber = 2,
            episodeNumber = EpisodeNumber("1.0"),
        )
        val season = SSeason("/series/example/s2", 2, playableItems = listOf(item))

        assertEquals(2, season.number)
        assertEquals("1", requireNotNull(season.playableItems).single().episodeNumber.toString())
    }

    @Test
    fun mandatoryContentFieldsRejectBlanks() {
        assertThrows(IllegalArgumentException::class.java) { SContent("", "Title") }
        assertThrows(IllegalArgumentException::class.java) { SContent("/content", "") }
    }

    @Test
    fun episodeNumbersUseCanonicalEquality() {
        assertEquals(EpisodeNumber("1"), EpisodeNumber("1.0"))
        assertEquals(EpisodeNumber("1").hashCode(), EpisodeNumber("1.00").hashCode())
        assertNotEquals(EpisodeNumber("1"), EpisodeNumber("1.5"))
    }

    @Test
    fun sourceDateValidatesLeapYears() {
        assertEquals(SourceDate(2024, 2, 29), SourceDate(2024, 2, 29))
        assertThrows(IllegalArgumentException::class.java) { SourceDate(2023, 2, 29) }
    }
}
