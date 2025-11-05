/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree.ast

import io.github.rosemoe.sora.lang.brackets.BracketKind
import io.github.rosemoe.sora.lang.brackets.tree.Length
import io.github.rosemoe.sora.lang.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.OpeningBracketId
import io.github.rosemoe.sora.text.ContentReference

class BracketAstNode private constructor(
    length: Length,
    val bracketInfo: BracketKind,
    /**
     * In case of a opening bracket, this is the id of the opening bracket.
     * In case of a closing bracket, this contains the ids of all opening brackets it can close.
     */
    val bracketIds: SmallImmutableSet<OpeningBracketId>
) : BaseAstNode(length) {

    override val kind: AstNodeKind
        get() = AstNodeKind.Bracket

    override val listHeight = 0

    override val childrenLength = 0

    override fun getChild(idx: Int): BaseAstNode? = null

    override val children: List<BaseAstNode>
        get() = emptyList()

    override val missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>
        get() = SmallImmutableSet.getEmpty()

    val text: String
        get() = bracketInfo.bracketText

    override fun canBeReused(openBracketIds: SmallImmutableSet<OpeningBracketId>): Boolean {
        // These nodes could be reused,
        // but not in a general way.
        // Their parent may be reused.
        return false
    }

    override fun flattenLists(): BracketAstNode {
        return this
    }

    override fun deepClone(): BracketAstNode {
        return this
    }

    override fun computeMinIndentation(offset: Length, content: ContentReference): Int {
        return Int.MAX_VALUE
    }

    override fun toString(): String {
        return "BracketAstNode(length=$length, text='$text', kind=${bracketInfo.bracketText})"
    }

    companion object {
        fun create(
            length: Length,
            bracketInfo: BracketKind,
            bracketIds: SmallImmutableSet<OpeningBracketId>
        ): BracketAstNode {
            return BracketAstNode(length, bracketInfo, bracketIds)
        }
    }
}

