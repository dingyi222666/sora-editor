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

open class TwoThreeListAstNode(
    length: Length,
    listHeight: Int,
    private var _item1: BaseAstNode,
    private var _item2: BaseAstNode,
    private var _item3: BaseAstNode?,
    missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>
) : ListAstNode(length, listHeight, missingOpeningBracketIds) {

    override val childrenLength: Int
        get() = if (_item3 != null) 3 else 2

    override fun getChild(idx: Int): BaseAstNode? = when (idx) {
        0 -> _item1
        1 -> _item2
        2 -> _item3
        else -> throw IllegalArgumentException("Invalid child index")
    }

    override fun setChild(idx: Int, child: BaseAstNode) {
        when (idx) {
            0 -> _item1 = child
            1 -> _item2 = child
            2 -> _item3 = child
            else -> throw IllegalArgumentException("Invalid child index")
        }
    }

    override val children: List<BaseAstNode>
        get() = if (_item3 != null) listOf(_item1, _item2, _item3!!) else listOf(_item1, _item2)

    val item1: BaseAstNode
        get() = _item1

    val item2: BaseAstNode
        get() = _item2

    val item3: BaseAstNode?
        get() = _item3

    override fun deepClone(): ListAstNode {
        return TwoThreeListAstNode(
            this.length,
            this.listHeight,
            this._item1.deepClone(),
            this._item2.deepClone(),
            this._item3?.deepClone(),
            this.missingOpeningBracketIds
        )
    }

    override fun appendChildOfSameHeight(node: BaseAstNode) {
        if (_item3 != null) {
            throw IllegalStateException("Cannot append to a full (2,3) tree node")
        }
        throwIfImmutable()
        _item3 = node
        handleChildrenChanged()
    }

    override fun unappendChild(): BaseAstNode? {
        if (_item3 == null) {
            throw IllegalStateException("Cannot remove from a non-full (2,3) tree node")
        }
        throwIfImmutable()
        val result = _item3
        _item3 = null
        handleChildrenChanged()
        return result
    }

    override fun prependChildOfSameHeight(node: BaseAstNode) {
        if (_item3 != null) {
            throw IllegalStateException("Cannot prepend to a full (2,3) tree node")
        }
        throwIfImmutable()
        _item3 = _item2
        _item2 = _item1
        _item1 = node
        handleChildrenChanged()
    }

    override fun unprependChild(): BaseAstNode? {
        if (_item3 == null) {
            throw IllegalStateException("Cannot remove from a non-full (2,3) tree node")
        }
        throwIfImmutable()
        val result = _item1
        _item1 = _item2
        _item2 = _item3!!
        _item3 = null

        handleChildrenChanged()
        return result
    }

    override fun toMutable(): ListAstNode {
        return this
    }

    override fun toString(): String {
        return buildString {
            append("TwoThreeListAstNode(")
            appendLine("length=$length, ")
            appendLine("listHeight=$listHeight, ")
            appendLine("children=[")
            appendLine(_item1)
            appendLine(", ")
            appendLine(_item2)
            if (_item3 != null) {
                appendLine(", ")
                appendLine(_item3)
            }
            appendLine("])")
        }
    }
}