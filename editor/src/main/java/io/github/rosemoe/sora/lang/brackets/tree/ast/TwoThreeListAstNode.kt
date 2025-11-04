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

    override fun setChild(idx: Int, node: BaseAstNode) {
        when (idx) {
            0 -> _item1 = node
            1 -> _item2 = node
            2 -> _item3 = node
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
}