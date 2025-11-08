/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree.ast

import io.github.rosemoe.sora.lang.brackets.tree.Length
import io.github.rosemoe.sora.lang.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.OpeningBracketId

/**
 * Immutable, if all children are immutable.
 */
class Immutable23ListAstNode(
    length: Length,
    listHeight: Int,
    item1: BaseAstNode,
    item2: BaseAstNode,
    item3: BaseAstNode?,
    missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>
) : TwoThreeListAstNode(length, listHeight, item1, item2, item3, missingOpeningBracketIds) {

    override fun toMutable(): ListAstNode {
        return TwoThreeListAstNode(
            this.length,
            this.listHeight,
            this.item1,
            this.item2,
            this.item3,
            this.missingOpeningBracketIds
        )
    }

    override fun throwIfImmutable() {
        throw IllegalStateException("this instance is immutable")
    }
}