/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package io.github.rosemoe.sora.lang.brackets.tree

import io.github.rosemoe.sora.text.CharPosition

/**
 * @param edits Must be sorted by offset in ascending order.
 */
class BeforeEditPositionMapper(
    edits: List<TextEditInfo>
) {
    private var nextEditIdx = 0
    private var deltaOldToNewLineCount = 0
    private var deltaOldToNewColumnCount = 0
    private var deltaLineIdxInOld = -1
    private val edits: List<TextEditInfoCache> = edits.map { TextEditInfoCache.from(it) }

    /**
     * @param offset Must be equal to or greater than the last offset this method has been called with.
     */
    fun getOffsetBeforeChange(offset: Length): Length {
        adjustNextEdit(offset)
        return translateCurToOld(offset)
    }

    /**
     * @param offset Must be equal to or greater than the last offset this method has been called with.
     * Returns null if there is no edit anymore.
     */
    fun getDistanceToNextChange(offset: Length): Length? {
        adjustNextEdit(offset)

        val nextEdit = edits.getOrNull(nextEditIdx)
        val nextChangeOffset = nextEdit?.let { translateOldToCur(it.offsetObj) }
        if (nextChangeOffset == null) {
            return null
        }

        return offset.diffNonNegative(nextChangeOffset)
    }

    private fun translateOldToCur(oldOffsetObj: CharPosition): Length {
        return if (oldOffsetObj.line  == deltaLineIdxInOld) {
            toLength(
                oldOffsetObj.line  + deltaOldToNewLineCount,
                oldOffsetObj.column  + deltaOldToNewColumnCount
            )
        } else {
            toLength(
                oldOffsetObj.line  + deltaOldToNewLineCount,
                oldOffsetObj.column
            )
        }
    }

    private fun translateCurToOld(newOffset: Length): Length {
        val offsetObj = newOffset.charPosition
        return if (offsetObj.line  - deltaOldToNewLineCount == deltaLineIdxInOld) {
            toLength(
                offsetObj.line  - deltaOldToNewLineCount,
                offsetObj.column  - deltaOldToNewColumnCount
            )
        } else {
            toLength(
                offsetObj.line  - deltaOldToNewLineCount,
                offsetObj.column
            )
        }
    }

    private fun adjustNextEdit(offset: Length) {
        while (nextEditIdx < edits.size) {
            val nextEdit = edits[nextEditIdx]

            // After applying the edit, what is its end offset (considering all previous edits)?
            val nextEditEndOffsetInCur = translateOldToCur(nextEdit.endOffsetAfterObj)

            if (nextEditEndOffsetInCur.value <= offset.value) {
                // We are after the edit, skip it
                nextEditIdx++

                val nextEditEndOffsetInCurObj = nextEditEndOffsetInCur.charPosition

                // Before applying the edit, what is its end offset (considering all previous edits)?
                val nextEditEndOffsetBeforeInCurObj =
                    translateOldToCur(nextEdit.endOffsetBeforeObj).charPosition

                val lineDelta =
                    nextEditEndOffsetInCurObj.line - nextEditEndOffsetBeforeInCurObj.line
                deltaOldToNewLineCount += lineDelta

                val previousColumnDelta =
                    if (deltaLineIdxInOld == nextEdit.endOffsetBeforeObj.line ) {
                        deltaOldToNewColumnCount
                    } else {
                        0
                    }
                val columnDelta =
                    nextEditEndOffsetInCurObj.column - nextEditEndOffsetBeforeInCurObj.column
                deltaOldToNewColumnCount = previousColumnDelta + columnDelta
                deltaLineIdxInOld = nextEdit.endOffsetBeforeObj.line
            } else {
                // We are in or before the edit.
                break
            }
        }
    }
}
