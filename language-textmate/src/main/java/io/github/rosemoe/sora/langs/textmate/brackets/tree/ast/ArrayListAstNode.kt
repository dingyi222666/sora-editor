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

import io.github.rosemoe.sora.annotations.UnsupportedUserUsage
import io.github.rosemoe.sora.langs.textmate.brackets.tree.Length
import io.github.rosemoe.sora.langs.textmate.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.langs.textmate.brackets.tree.tokenizer.OpeningBracketId

/**
 * For debugging.
 */
@UnsupportedUserUsage
open class ArrayListAstNode(
    length: Length,
    listHeight: Int,
    private val _children: MutableList<BaseAstNode>,
    missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>
) : ListAstNode(length, listHeight, missingOpeningBracketIds) {

    override val childrenLength: Int
        get() = _children.size

    override fun getChild(idx: Int): BaseAstNode? = _children[idx]

    override fun setChild(idx: Int, child: BaseAstNode) {
        _children[idx] = child
    }

    override val children: List<BaseAstNode>
        get() = _children

    override fun deepClone(): ListAstNode {
        val children = _children.map { it.deepClone() }.toMutableList()
        return ArrayListAstNode(
            this.length,
            this.listHeight,
            children,
            this.missingOpeningBracketIds
        )
    }

    override fun appendChildOfSameHeight(node: BaseAstNode) {
        throwIfImmutable()
        _children.add(node)
        handleChildrenChanged()
    }

    override fun unappendChild(): BaseAstNode? {
        throwIfImmutable()
        val item = _children.removeLastOrNull()
        handleChildrenChanged()
        return item
    }

    override fun prependChildOfSameHeight(node: BaseAstNode) {
        throwIfImmutable()
        _children.add(0, node)
        handleChildrenChanged()
    }

    override fun unprependChild(): BaseAstNode? {
        throwIfImmutable()
        val item = if (_children.isNotEmpty()) _children.removeAt(0) else null
        handleChildrenChanged()
        return item
    }

    override fun toMutable(): ListAstNode {
        return this
    }

    override fun toString(): String {
        return buildString {
            appendLine("ArrayListAstNode(")
            appendLine("length=$length, ")
            appendLine("listHeight=$listHeight, ")
            appendLine("children=[")
            _children.forEachIndexed { index, child ->
                if (index > 0) append(", ")
                appendLine(child)
            }
            appendLine("])")
        }
    }
}