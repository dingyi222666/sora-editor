/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree

class TextEditInfo(
    val startOffset: Length,
    val endOffset: Length,
    val newLength: Length
) {

    override fun toString(): String {
        return "[${startOffset.charPosition}...${endOffset.charPosition}) -> ${newLength.charPosition}"
    }
}
