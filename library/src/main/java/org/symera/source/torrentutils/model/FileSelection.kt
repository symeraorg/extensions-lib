package org.symera.source.torrentutils.model

import java.io.Serializable

class FileSelection private constructor(
    val indices: Set<Int>,
) : Serializable {
    init {
        require(indices.isNotEmpty()) { "A file selection cannot be empty" }
        require(indices.all { it >= 0 }) { "File indices cannot be negative" }
    }

    fun contains(index: Int): Boolean = index in indices

    fun toParameter(): String {
        val sorted = indices.sorted()
        return buildString {
            var rangeStart = sorted.first()
            var previous = rangeStart
            for (index in sorted.drop(1)) {
                if (index == previous + 1) {
                    previous = index
                    continue
                }
                appendRange(rangeStart, previous)
                append(',')
                rangeStart = index
                previous = index
            }
            appendRange(rangeStart, previous)
        }
    }

    override fun equals(other: Any?): Boolean = other is FileSelection && indices == other.indices

    override fun hashCode(): Int = indices.hashCode()

    override fun toString(): String = toParameter()

    companion object {
        fun single(index: Int): FileSelection = fromIndices(setOf(index))

        fun fromIndices(indices: Collection<Int>): FileSelection = FileSelection(indices.toSortedSet())

        fun parse(value: String, maxSelectedFiles: Int): FileSelection {
            if (value.isBlank()) throw DeadTorrentException("Magnet file selection is empty")
            val indices = sortedSetOf<Int>()
            value.split(',').forEach { token ->
                if (token.isEmpty()) throw DeadTorrentException("Magnet file selection contains an empty range")
                val separator = token.indexOf('-')
                if (separator < 0) {
                    indices.addIndex(token.parseIndex(), maxSelectedFiles)
                } else {
                    if (separator == 0 || separator != token.lastIndexOf('-') || separator == token.lastIndex) {
                        throw DeadTorrentException("Invalid magnet file range: $token")
                    }
                    val start = token.substring(0, separator).parseIndex()
                    val end = token.substring(separator + 1).parseIndex()
                    if (end < start) throw DeadTorrentException("Magnet file range is descending: $token")
                    val count = end.toLong() - start.toLong() + 1L
                    if (count > maxSelectedFiles || indices.size.toLong() + count > maxSelectedFiles) {
                        throw DeadTorrentException("Magnet file selection exceeds the configured limit")
                    }
                    for (index in start..end) indices += index
                }
            }
            return FileSelection(indices)
        }
    }
}

private fun String.parseIndex(): Int {
    if (isEmpty() || any { !it.isDigit() } || (length > 1 && first() == '0')) {
        throw DeadTorrentException("Invalid magnet file index: $this")
    }
    return toIntOrNull() ?: throw DeadTorrentException("Magnet file index is too large")
}

private fun MutableSet<Int>.addIndex(index: Int, limit: Int) {
    add(index)
    if (size > limit) throw DeadTorrentException("Magnet file selection exceeds the configured limit")
}

private fun StringBuilder.appendRange(start: Int, end: Int) {
    append(start)
    if (end != start) {
        append('-')
        append(end)
    }
}
