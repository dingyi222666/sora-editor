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