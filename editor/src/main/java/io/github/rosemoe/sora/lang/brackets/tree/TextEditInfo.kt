/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package io.github.rosemoe.sora.lang.brackets.tree

import io.github.rosemoe.sora.text.CharPosition

class TextEditInfo(
    val startOffset: Length,
    val endOffset: Length,
    val newLength: Length
) {

    override fun toString(): String {
        return "[${startOffset.charPosition}...${endOffset.charPosition}) -> ${newLength.charPosition}"
    }

    companion object {
        // TODO: Implement this method when IModelContentChange is available
        // fun fromModelContentChanges(changes: List<IModelContentChange>): List<TextEditInfo> {
        //     // Must be sorted in ascending order
        //     val edits = changes.map { c ->
        //         val range = Range.lift(c.range)
        //         TextEditInfo(
        //             positionToLength(range.getStartPosition()),
        //             positionToLength(range.getEndPosition()),
        //             lengthOfString(c.text)
        //         )
        //     }.reversed()
        //     return edits
        // }
    }
}
