/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree.ast

import io.github.rosemoe.sora.annotations.UnsupportedUserUsage
import io.github.rosemoe.sora.lang.brackets.tree.Length
import io.github.rosemoe.sora.lang.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.OpeningBracketId

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
            append("ArrayListAstNode(")
            append("length=$length, ")
            append("listHeight=$listHeight, ")
            append("children=[")
            _children.forEachIndexed { index, child ->
                if (index > 0) append(", ")
                append(child)
            }
            append("])")
        }
    }
}