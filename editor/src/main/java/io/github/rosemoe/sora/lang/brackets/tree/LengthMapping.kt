/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree

/**
 * Represents a mapping of content length transformations during text edits.
 *
 * @param modified If false, lengthBefore and lengthAfter are equal (unmodified region)
 * @param lengthBefore The length of the content before the edit
 * @param lengthAfter The length of the content after the edit
 */
data class LengthMapping(
    val modified: Boolean,
    val lengthBefore: Length,
    val lengthAfter: Length
) {
    /**
     * Splits this mapping at the specified lengthAfter position.
     *
     * @param lengthAfter The position to split at
     * @return A pair of [LengthMapping, LengthMapping?] where the second element is null if no split occurred
     */
    fun splitAt(lengthAfter: Length): Pair<LengthMapping, LengthMapping?> {
        val remainingLengthAfter = lengthAfter.diffNonNegative(this.lengthAfter)

        return if (remainingLengthAfter.isZero()) {
            Pair(this, null)
        } else if (modified) {
            Pair(
                LengthMapping(true, this.lengthBefore, lengthAfter),
                LengthMapping(true, Length.ZERO, remainingLengthAfter)
            )
        } else {
            Pair(
                LengthMapping(false, lengthAfter, lengthAfter),
                LengthMapping(false, remainingLengthAfter, remainingLengthAfter)
            )
        }
    }

    override fun toString(): String {
        val prefix = if (modified) "M" else "U"
        return "$prefix:${lengthBefore.charPosition} -> ${lengthAfter.charPosition}"
    }
}
