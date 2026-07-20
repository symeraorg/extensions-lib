package org.symera.source.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterTest {
    @Test
    fun filtersAreOptional() {
        assertEquals(0, FilterList().size)
    }

    @Test
    fun arbitraryFilterCountsRemainSupported() {
        val filters = FilterList((1..1_000).map { Filter.Text("Filter $it", key = "filter_$it") })
        assertEquals(1_000, filters.size)
    }

    @Test
    fun duplicateKeysAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FilterList(Filter.Text("One", key = "same"), Filter.CheckBox("Two", key = "same"))
        }
    }

    @Test
    fun decorativeFiltersNeedNoKeys() {
        val filters = FilterList(Filter.Separator(), Filter.Header("Genres"), Filter.Separator())
        assertEquals(3, filters.size)
    }

    @Test
    fun duplicateNestedKeysAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            FilterList(
                Filter.Group("One", listOf(Filter.Text("Child", key = "duplicate"))),
                Filter.Group("Two", listOf(Filter.CheckBox("Child", key = "duplicate"))),
            )
        }
    }

    @Test
    fun invalidMutableStatesAreRejectedWithoutReplacingPreviousState() {
        val select = Filter.Select("Quality", listOf("SD", "HD"), state = 1)
        val multi = Filter.MultiSelect("Genres", listOf("Action", "Drama"), state = setOf(0))
        val sort = Filter.Sort("Sort", listOf("Title"), SortSelection(0, true))

        assertThrows(IllegalArgumentException::class.java) { select.state = 2 }
        assertThrows(IllegalArgumentException::class.java) { multi.state = setOf(3) }
        assertThrows(IllegalArgumentException::class.java) { sort.state = SortSelection(4, false) }

        assertEquals(1, select.state)
        assertEquals(setOf(0), multi.state)
        assertEquals(0, sort.state?.index)
    }

    @Test
    fun customContainersParticipateInRecursiveValidation() {
        val child = Filter.Text("Child", key = "shared")
        val container = object : Filter<Unit>("Custom", Unit, null), FilterContainer {
            override val children = listOf(child)
        }
        val filters = FilterList(container)
        assertTrue(filters.requireValid() === filters)
        assertThrows(IllegalArgumentException::class.java) {
            FilterList(container, Filter.CheckBox("Duplicate", key = "shared"))
        }
    }

    @Test
    fun cyclicCustomContainersAreRejected() {
        val cyclic = object : Filter<Unit>("Cycle", Unit, null), FilterContainer {
            override val children: List<Filter<*>>
                get() = listOf(this)
        }
        assertThrows(IllegalArgumentException::class.java) { FilterList(cyclic) }
    }
}
