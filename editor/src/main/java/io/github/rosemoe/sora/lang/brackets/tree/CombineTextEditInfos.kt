/*---------------------------------------------------------------------------------------------
*  Copyright (c) Microsoft Corporation. All rights reserved.
*  Licensed under the MIT License. See License.txt in the project root for license information.
*--------------------------------------------------------------------------------------------*/

package io.github.rosemoe.sora.lang.brackets.tree

/**
 * Combines two sequences of text edits into a single unified sequence.
 *
 * This function takes two arrays of text edits and merges them:
 * - textEditInfoFirst: transforms state s0 to s1
 * - textEditInfoSecond: transforms state s1 to s2
 * - result: transforms state s0 directly to s2
 *
 * @param textEditInfoFirst The first sequence of text edits
 * @param textEditInfoSecond The second sequence of text edits
 * @return A combined sequence of text edits that represents both transformations
 */
fun combineTextEditInfos(
    textEditInfoFirst: List<TextEditInfo>,
    textEditInfoSecond: List<TextEditInfo>
): List<TextEditInfo> {
    if (textEditInfoFirst.isEmpty()) {
        return textEditInfoSecond
    }
    if (textEditInfoSecond.isEmpty()) {
        return textEditInfoFirst
    }

    // s0: State before any edits
    val s0ToS1Map = ArrayDeque(toLengthMapping(textEditInfoFirst))

    // s1: State after first edit, but before second edit
    val s1ToS2Map = toLengthMapping(textEditInfoSecond).toMutableList()
    // Add sentinel: Copy everything from old to new
    s1ToS2Map.add(LengthMapping(false, Length.ZERO, Length.ZERO))

    // s2: State after both edits
    var curItem: LengthMapping? = s0ToS1Map.removeFirstOrNull()

    /**
     * @param s1Length Use null for length "infinity"
     */
    fun nextS0ToS1MapWithS1LengthOf(s1Length: Length?): List<LengthMapping> {
        if (s1Length == null) {
            val arr = mutableListOf<LengthMapping>()
            curItem?.let { arr.add(it) }
            while (s0ToS1Map.isNotEmpty()) {
                s0ToS1Map.removeFirstOrNull()?.let { arr.add(it) }
            }
            return arr
        }

        val result = mutableListOf<LengthMapping>()
        var remainingLength: Length = s1Length

        while (curItem != null && !remainingLength.isZero()) {
            val (item, remainingItem) = curItem?.splitAt(remainingLength) ?: break
            result.add(item)
            remainingLength = item.lengthAfter.diffNonNegative(remainingLength)
            curItem = remainingItem ?: s0ToS1Map.removeFirstOrNull()
        }

        if (!remainingLength.isZero()) {
            result.add(LengthMapping(false, remainingLength, remainingLength))
        }

        return result
    }

    val result = mutableListOf<TextEditInfo>()

    fun pushEdit(startOffset: Length, endOffset: Length, newLength: Length) {
        if (result.isNotEmpty() && result.last().endOffset.value == startOffset.value) {
            val lastResult = result.last()
            result[result.lastIndex] = TextEditInfo(
                lastResult.startOffset,
                endOffset,
                lastResult.newLength + newLength
            )
        } else {
            result.add(TextEditInfo(startOffset, endOffset, newLength))
        }
    }

    var s0offset = Length.ZERO

    for (s1ToS2 in s1ToS2Map) {
        val s0ToS1MapItems = nextS0ToS1MapWithS1LengthOf(s1ToS2.lengthBefore)

        if (s1ToS2.modified) {
            val s0Length = s0ToS1MapItems.sumLengths { it.lengthBefore }
            val s0EndOffset = s0offset + s0Length
            pushEdit(s0offset, s0EndOffset, s1ToS2.lengthAfter)
            s0offset = s0EndOffset
        } else {
            for (s1 in s0ToS1MapItems) {
                val s0startOffset = s0offset
                s0offset += s1.lengthBefore
                if (s1.modified) {
                    pushEdit(s0startOffset, s0offset, s1.lengthAfter)
                }
            }
        }
    }

    return result
}

/**
 * Converts a list of TextEditInfo into a list of LengthMapping.
 *
 * This function identifies both modified regions (edits) and unmodified regions (gaps between edits).
 *
 * @param textEditInfos The list of text edits to convert
 * @return A list of LengthMapping representing the edit transformations
 */
fun toLengthMapping(textEditInfos: List<TextEditInfo>): List<LengthMapping> {
    val result = mutableListOf<LengthMapping>()
    var lastOffset = Length.ZERO

    for (textEditInfo in textEditInfos) {
        // Add unmodified region (gap) before this edit
        val spaceLength = lastOffset.diffNonNegative(textEditInfo.startOffset)
        if (!spaceLength.isZero()) {
            result.add(LengthMapping(false, spaceLength, spaceLength))
        }

        // Add the modified region (the edit itself)
        val lengthBefore = textEditInfo.startOffset.diffNonNegative(textEditInfo.endOffset)
        result.add(LengthMapping(true, lengthBefore, textEditInfo.newLength))
        lastOffset = textEditInfo.endOffset
    }

    return result
}
