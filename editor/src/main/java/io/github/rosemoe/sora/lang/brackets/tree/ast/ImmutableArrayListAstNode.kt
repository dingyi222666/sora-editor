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
class ImmutableArrayListAstNode(
    length: Length,
    listHeight: Int,
    children: List<BaseAstNode>,
    missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>
) : ArrayListAstNode(length, listHeight, children.toMutableList(), missingOpeningBracketIds) {

    override fun toMutable(): ListAstNode {
        return ArrayListAstNode(this.length, this.listHeight, this.children.toMutableList(), this.missingOpeningBracketIds)
    }

    override fun throwIfImmutable() {
        throw IllegalStateException("this instance is immutable")
    }
}