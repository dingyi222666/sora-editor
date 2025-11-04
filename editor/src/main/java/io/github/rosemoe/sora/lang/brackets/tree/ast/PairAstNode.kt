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

/**
 * Represents a bracket pair including its child (e.g. `{ ... }`).
 * Might be unclosed.
 * Immutable, if all children are immutable.
 */
class PairAstNode private constructor(
    length: Length,
    val openingBracket: BracketAstNode,
    val child: BaseAstNode?,
    val closingBracket: BracketAstNode?,
    override val missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>
) : BaseAstNode(length) {


    override val kind: AstNodeKind
        get() = AstNodeKind.Pair


    override val listHeight = 0

    override val childrenLength = 3

    override fun getChild(idx: Int): BaseAstNode? = when (idx) {
        0 -> openingBracket;
        1 -> child;
        2 -> closingBracket;
        else -> error("Invalid child index")
    }

    override val children: List<BaseAstNode>
        get() = listOfNotNull(this.openingBracket, this.child, this.closingBracket)


    override fun canBeReused(openBracketIds: SmallImmutableSet<OpeningBracketId>): Boolean {
        if (this.closingBracket === null) {
            // Unclosed pair ast nodes only
            // end at the end of the document
            // or when a parent node is closed.

            // This could be improved:
            // Only return false if some next token is neither "undefined" nor a bracket that closes a parent.

            return false;
        }

        if (openBracketIds.intersects(this.missingOpeningBracketIds)) {
            return false;
        }

        return true;
    }

    override fun flattenLists(): BaseAstNode {
        return create(
            openingBracket.flattenLists(),
            child?.flattenLists(),
            closingBracket?.flattenLists()
        );
    }

    override fun deepClone(): PairAstNode {
        return PairAstNode(
            this.length,
            openingBracket.deepClone(),
            child?.deepClone(),
            closingBracket?.deepClone(),
            missingOpeningBracketIds
        )
    }

    override fun computeMinIndentation(offset: Length, content: ContentReference): Int {
        return child?.computeMinIndentation(
            offset + openingBracket.length,
            content
        )
            ?: Int.MAX_VALUE
    }

    companion object {
        fun create(
            openingBracket: BracketAstNode,
            child: BaseAstNode?,
            closingBracket: BracketAstNode?
        ): PairAstNode {
            var length = openingBracket.length;
            if (child != null) {
                length += child.length
            }
            if (closingBracket != null) {
                length += closingBracket.length
            }
            return PairAstNode(
                length,
                openingBracket,
                child,
                closingBracket,
                child?.missingOpeningBracketIds ?: SmallImmutableSet.getEmpty()
            );
        }
    }
}