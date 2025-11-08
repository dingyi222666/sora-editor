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


sealed class BaseAstNode(private var mLength: Length) {
    abstract val kind: AstNodeKind
    abstract val childrenLength: Int

    /**
     * Might return null even if {@link idx} is smaller than {@link BaseAstNode.childrenLength}.
     */
    abstract fun getChild(idx: Int): BaseAstNode?

    /**
     * Try to avoid using this property, as implementations might need to allocate the resulting array.
     */
    abstract val children: List<BaseAstNode>

    /**
     * Represents the set of all (potentially) missing opening bracket ids in this node.
     * E.g. in `{ ] ) }` that set is {`[`, `(` }.
     */
    abstract val missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>

    /**
     * In case of a list, determines the height of the (2,3) tree.
     */
    abstract val listHeight: Int


    val length: Length
        get() = mLength

    /**
     * @param openBracketIds The set of all opening brackets that have not yet been closed.
     */
    abstract fun canBeReused(
        openBracketIds: SmallImmutableSet<OpeningBracketId>
    ): Boolean

    /**
     * Flattens all lists in this AST. Only for debugging.
     */
    abstract fun flattenLists(): BaseAstNode

    /**
     * Creates a deep clone.
     */
    abstract fun deepClone(): BaseAstNode

    abstract fun computeMinIndentation(offset: Length, content: Content): Int
}
