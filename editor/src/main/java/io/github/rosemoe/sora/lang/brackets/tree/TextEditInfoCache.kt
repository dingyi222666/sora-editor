/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree

import io.github.rosemoe.sora.text.CharPosition

internal class TextEditInfoCache(
    startOffset: Length,
    endOffset: Length,
    textLength: Length
) {
    val endOffsetBeforeObj: CharPosition = endOffset.charPosition
    val endOffsetAfterObj: CharPosition = (startOffset + textLength).charPosition
    val offsetObj: CharPosition = startOffset.charPosition

    companion object {
        fun from(edit: TextEditInfo): TextEditInfoCache {
            return TextEditInfoCache(edit.startOffset, edit.endOffset, edit.newLength)
        }
    }
}
