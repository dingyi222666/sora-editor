/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree

import io.github.rosemoe.sora.lang.brackets.tree.Length.Companion.ZERO
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.util.IntPair

@JvmInline
value class Length(val value: Long) {

    val lineCount: Int
        get() = IntPair.getFirst(value)

    val columnCount: Int
        get() = IntPair.getSecond(value)

    val charPosition: CharPosition
        get() = CharPosition(lineCount + 1, columnCount + 1)

    // [10 lines, 5 cols] + [ 0 lines, 3 cols] = [10 lines, 8 cols]
    // [10 lines, 5 cols] + [20 lines, 3 cols] = [30 lines, 3 cols]
    operator fun plus(other: Length): Length {
        return if (other.lineCount > 0) {
            toLength(this.lineCount + other.lineCount, other.columnCount)
        } else {
            toLength(this.lineCount, this.columnCount + other.columnCount)
        }
    }

    fun toRange(other: Length): TextRange = TextRange(this.charPosition, other.charPosition)

    fun max(other: Length): Length {
        return if (this.value > other.value) this else other
    }

    fun isZero(): Boolean {
        return value == 0L
    }

    /**
     * Returns a non negative length `result` such that `lengthAdd(length1, result) = length2`, or zero if such length does not exist.
     */
    fun diffNonNegative(other: Length): Length {
        // line-count of length1 is higher than line-count of length2
        // or they are equal and column-count of length1 is higher than column-count of length2
        if (this.value >= other.value) {
            return ZERO
        }

        val line1 = this.lineCount
        val col1 = this.columnCount
        val line2 = other.lineCount
        val col2 = other.columnCount

        return if (line1 == line2) {
            toLength(0, col2 - col1)
        } else {
            toLength(line2 - line1, col2)
        }
    }

    companion object {
        val ZERO = Length(0L)

        /**
         * The end must be greater than or equal to the start.
         */
        fun lengthDiff(
            startLineCount: Int,
            startColumnCount: Int,
            endLineCount: Int,
            endColumnCount: Int
        ): Length {
            return if (startLineCount != endLineCount) {
                toLength(endLineCount - startLineCount, endColumnCount)
            } else {
                toLength(0, endLineCount - startColumnCount)
            }
        }
    }
}

internal fun toLength(lineCount: Int, columnCount: Int): Length {
    return Length(IntPair.pack(lineCount, columnCount))
}


internal fun TextRange.lengthOfRange(): CharPosition {
    return if (start.line == end.line) {
        CharPosition(0, start.column - end.column)
    } else {
        CharPosition(end.line - start.line, end.column)
    }
}


internal fun String.toLength(): Length {
    val lines = splitLine()
    return toLength(lines.size, lines.last().length)
}

internal fun String.lengthOfStringObj(): CharPosition {
    val lines = splitLine()
    return CharPosition(lines.size - 1, lines.last().length - 1)
}

val splitLineRegex = Regex("\r\n|\r|\n")


internal fun String.splitLine(): List<String> {
    return split(splitLineRegex)
}

/**
 * Computes a numeric hash of the given length.
 */
fun lengthHash(length: Length): Int {
    // The value class automatically provides a suitable hashCode implementation.
    return length.hashCode()
}

internal fun <T> Iterable<T>.sumLengths(lengthFn: (T) -> Length): Length {
    return fold(ZERO) { acc, item -> acc + lengthFn(item) }
}