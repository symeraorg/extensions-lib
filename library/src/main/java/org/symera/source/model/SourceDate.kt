package org.symera.source.model

/** Calendar date without a time zone or Android API-level dependency. */
data class SourceDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<SourceDate> {
    init {
        require(year > 0) { "Year must be positive" }
        require(month in 1..12) { "Month must be between 1 and 12" }
        require(day in 1..daysInMonth(year, month)) { "Day is invalid for the supplied month" }
    }

    override fun compareTo(other: SourceDate): Int =
        compareValuesBy(this, other, SourceDate::year, SourceDate::month, SourceDate::day)
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 400 == 0 || year % 4 == 0 && year % 100 != 0) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}
