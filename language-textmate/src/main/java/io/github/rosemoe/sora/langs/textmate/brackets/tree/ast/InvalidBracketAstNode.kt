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

    override fun computeMinIndentation(offset: Length, content: Content): Int {
        return Int.MAX_VALUE
    }

    override fun toString(): String {
        return "InvalidBracketAstNode(length=$length, closingBrackets=$missingOpeningBracketIds)"
    }
}
