package org.symera.source.model

sealed class Filter<T>(val name: String, var state: T) {
    open class Header(name: String) : Filter<Unit>(name, Unit)
    open class Separator(name: String = "") : Filter<Unit>(name, Unit)
    open class Text(name: String, state: String = "") : Filter<String>(name, state)
    open class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)
    open class Select<V>(name: String, val values: List<V>, state: Int = 0) : Filter<Int>(name, state)
    open class MultiSelect<V>(name: String, val values: List<V>, state: Set<Int> = emptySet()) : Filter<Set<Int>>(name, state)
    open class Group<V : Filter<*>>(name: String, filters: List<V>) : Filter<List<V>>(name, filters)

    open class TriState(name: String, state: Int = STATE_IGNORE) : Filter<Int>(name, state) {
        fun isIgnored(): Boolean = state == STATE_IGNORE
        fun isIncluded(): Boolean = state == STATE_INCLUDE
        fun isExcluded(): Boolean = state == STATE_EXCLUDE

        companion object {
            const val STATE_IGNORE = 0
            const val STATE_INCLUDE = 1
            const val STATE_EXCLUDE = 2
        }
    }

    open class Sort(name: String, val values: List<String>, state: Selection? = null) : Filter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Filter<*>) return false
        return name == other.name && state == other.state
    }

    override fun hashCode(): Int = 31 * name.hashCode() + (state?.hashCode() ?: 0)
}

data class FilterList(val list: List<Filter<*>> = emptyList()) : List<Filter<*>> by list {
    constructor(vararg filters: Filter<*>) : this(filters.asList())
}
