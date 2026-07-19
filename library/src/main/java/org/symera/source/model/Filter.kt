package org.symera.source.model

import java.util.Collections
import java.util.IdentityHashMap

/** Optional traversal contract for source-defined composite filters. */
interface FilterContainer {
    val children: List<Filter<*>>
}

/**
 * Extensible filter primitive. Sources may return no filters or arbitrarily compose these types.
 * Subclasses remain open so source families can attach site-specific request values.
 */
open class Filter<T>(
    val name: String,
    initialState: T,
    val key: String? = name,
) {
    var state: T = initialState
        set(value) {
            validateState(value)
            field = value
        }

    protected open fun validateState(value: T) = Unit

    internal fun requireValidState() = validateState(state)

    init {
        require(key == null || key.isNotBlank()) { "Filter key cannot be blank" }
        require(name.isNotBlank()) { "Filter name cannot be blank" }
    }

    open class Header(name: String) : Filter<Unit>(name, Unit, null)

    open class Separator(name: String = "Separator") : Filter<Unit>(name, Unit, null)

    open class Text(name: String, state: String = "", key: String = name) : Filter<String>(name, state, key)

    open class CheckBox(name: String, state: Boolean = false, key: String = name) : Filter<Boolean>(name, state, key)

    open class Select<V>(
        name: String,
        values: List<V>,
        state: Int = 0,
        key: String = name,
    ) : Filter<Int>(name, state, key) {
        val values: List<V> = values.toList()

        init {
            require(this.values.isNotEmpty()) { "Select filter requires at least one value" }
            validateState(state)
        }

        override fun validateState(value: Int) {
            require(value in values.indices) { "Select filter state is outside its values" }
        }
    }

    open class MultiSelect<V>(
        name: String,
        values: List<V>,
        state: Set<Int> = emptySet(),
        key: String = name,
    ) : Filter<Set<Int>>(name, state, key) {
        val values: List<V> = values.toList()

        init {
            validateState(state)
        }

        override fun validateState(value: Set<Int>) {
            require(value.all(values.indices::contains)) { "Multi-select state contains an invalid index" }
        }
    }

    open class Group<V : Filter<*>>(
        name: String,
        filters: List<V>,
    ) : Filter<List<V>>(name, filters.toList(), null), FilterContainer {
        override val children: List<Filter<*>>
            get() = state
    }

    enum class TriStateValue {
        IGNORE,
        INCLUDE,
        EXCLUDE,
    }

    open class TriState(
        name: String,
        state: TriStateValue = TriStateValue.IGNORE,
        key: String = name,
    ) : Filter<TriStateValue>(name, state, key)

    open class Sort(
        name: String,
        values: List<String>,
        state: SortSelection? = null,
        key: String = name,
    ) : Filter<SortSelection?>(name, state, key) {
        val values: List<String> = values.toList()

        init {
            validateState(state)
        }

        override fun validateState(value: SortSelection?) {
            require(value == null || value.index in values.indices) { "Sort state is outside its values" }
        }
    }

    open class NumberRange(
        name: String,
        state: Range = Range(),
        key: String = name,
    ) : Filter<NumberRange.Range>(name, state, key) {
        data class Range(val minimum: Double? = null, val maximum: Double? = null) {
            init {
                require(minimum == null || minimum.isFinite()) { "Range minimum must be finite" }
                require(maximum == null || maximum.isFinite()) { "Range maximum must be finite" }
                require(minimum == null || maximum == null || minimum <= maximum) { "Range minimum exceeds maximum" }
            }
        }
    }

    open class DateRange(
        name: String,
        state: Range = Range(),
        key: String = name,
    ) : Filter<DateRange.Range>(name, state, key) {
        data class Range(val from: SourceDate? = null, val to: SourceDate? = null) {
            init {
                require(from == null || to == null || from <= to) { "Date range start is after its end" }
            }
        }
    }
}

data class SortSelection(val index: Int, val ascending: Boolean)

class FilterList private constructor(
    val list: List<Filter<*>>,
) : List<Filter<*>> by list {
    constructor(filters: Collection<Filter<*>> = emptyList()) : this(filters.toList())
    constructor(vararg filters: Filter<*>) : this(filters.toList())

    init {
        requireValid()
    }

    /** Revalidates mutable state before a filter list crosses the host/extension boundary. */
    fun requireValid(): FilterList {
        val flattened = flattenFilters(list)
        flattened.forEach(Filter<*>::requireValidState)
        val duplicateKeys = flattened
            .asSequence()
            .mapNotNull(Filter<*>::key)
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateKeys.isEmpty()) { "Duplicate filter keys: ${duplicateKeys.joinToString()}" }
        return this
    }
}

private fun flattenFilters(filters: List<Filter<*>>): List<Filter<*>> {
    val flattened = mutableListOf<Filter<*>>()
    val seen = Collections.newSetFromMap(IdentityHashMap<Filter<*>, Boolean>())
    fun visit(filter: Filter<*>) {
        require(seen.add(filter)) { "Filter containers cannot contain cycles or repeated filter instances" }
        flattened += filter
        if (filter is FilterContainer) filter.children.forEach(::visit)
    }
    filters.forEach(::visit)
    return flattened
}
