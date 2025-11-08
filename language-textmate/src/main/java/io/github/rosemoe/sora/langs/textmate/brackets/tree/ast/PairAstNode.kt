/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2025  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/

package io.github.rosemoe.sora.langs.textmate.brackets.tree.ast

import io.github.rosemoe.sora.langs.textmate.brackets.tree.Length
import io.github.rosemoe.sora.langs.textmate.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.langs.textmate.brackets.tree.tokenizer.OpeningBracketId
import io.github.rosemoe.sora.text.Content

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
        0 -> openingBracket
        1 -> child
        2 -> closingBracket
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

            return false
        }

        if (openBracketIds.intersects(this.missingOpeningBracketIds)) {
            return false
        }

        return true
    }

    override fun flattenLists(): BaseAstNode {
        return create(
            openingBracket.flattenLists(),
            child?.flattenLists(),
            closingBracket?.flattenLists()
        )
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

    override fun computeMinIndentation(offset: Length, content: Content): Int {
        return child?.computeMinIndentation(
            offset + openingBracket.length,
            content
        )
            ?: Int.MAX_VALUE
    }

    override fun toString(): String {
        return buildString {
            appendLine("PairAstNode(")
            appendLine("length=$length, ")
            appendLine("opening=${openingBracket.text}, ")
            appendLine("child=$child, ")
            appendLine("closing=${closingBracket?.text ?: "null"}")
            appendLine(")")
        }
    }

    companion object {
        fun create(
            openingBracket: BracketAstNode,
            child: BaseAstNode?,
            closingBracket: BracketAstNode?
        ): PairAstNode {
            var length = openingBracket.length
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
            )
        }
    }
}
