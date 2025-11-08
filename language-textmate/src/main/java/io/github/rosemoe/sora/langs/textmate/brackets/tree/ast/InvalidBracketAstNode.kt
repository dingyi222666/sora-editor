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
import io.github.rosemoe.sora.text.ContentReference

class InvalidBracketAstNode(
    closingBrackets: SmallImmutableSet<OpeningBracketId>,
    length: Length
) : BaseAstNode(length) {

    override val kind: AstNodeKind
        get() = AstNodeKind.UnexpectedClosingBracket

    override val listHeight = 0

    override val childrenLength = 0

    override fun getChild(idx: Int): BaseAstNode? = null

    override val children: List<BaseAstNode>
        get() = emptyList()

    override val missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId> = closingBrackets

    override fun canBeReused(openBracketIds: SmallImmutableSet<OpeningBracketId>): Boolean {
        return !openBracketIds.intersects(this.missingOpeningBracketIds)
    }

    override fun flattenLists(): InvalidBracketAstNode {
        return this
    }

    override fun deepClone(): InvalidBracketAstNode {
        return this
    }

    override fun computeMinIndentation(offset: Length, content: ContentReference): Int {
        return Int.MAX_VALUE
    }

    override fun toString(): String {
        return "InvalidBracketAstNode(length=$length, closingBrackets=$missingOpeningBracketIds)"
    }
}
