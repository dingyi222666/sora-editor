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
        get() = CharPosition(lineCount , columnCount)

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
        if (other.value - value <= 0) {
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
                val deltaColumn = endColumnCount - startColumnCount
                require(deltaColumn >= 0) {
                    "endColumnCount must be >= startColumnCount (start=$startColumnCount, end=$endColumnCount)"
                }
                toLength(0, deltaColumn)
            }
        }
    }
}

internal fun toLength(lineCount: Int, columnCount: Int): Length {
    return Length(IntPair.pack(lineCount, columnCount))
}

fun CharPosition.toLength(): Length {
    return toLength(this.line, this.column)
}

internal fun lengthOfString(text: CharSequence): Length {
    var lineCount = 0
    var columnCount = 0
    var index = 0

    while (index < text.length) {
        when (text[index]) {
            '\r' -> {
                if (index + 1 < text.length && text[index + 1] == '\n') {
                    index++
                }
                lineCount++
                columnCount = 0
            }

            '\n' -> {
                lineCount++
                columnCount = 0
            }

            else -> {
                columnCount++
            }
        }
        index++
    }

    return toLength(lineCount, columnCount)
}


internal fun <T> Iterable<T>.sumLengths(lengthFn: (T) -> Length): Length {
    return fold(ZERO) { acc, item -> acc + lengthFn(item) }
}
