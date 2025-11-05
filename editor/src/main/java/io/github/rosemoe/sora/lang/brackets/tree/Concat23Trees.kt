/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package io.github.rosemoe.sora.lang.brackets.tree

import io.github.rosemoe.sora.lang.brackets.tree.ast.BaseAstNode
import io.github.rosemoe.sora.lang.brackets.tree.ast.ListAstNode
import kotlin.math.abs

/**
 * Concatenates a list of (2,3) AstNode's into a single (2,3) AstNode.
 * This mutates the items of the input array!
 * If all items have the same height, this method has runtime O(items.length).
 * Otherwise, it has runtime O(items.length * max(log(items.length), items.max(i => i.height))).
 */
fun concat23Trees(items: MutableList<BaseAstNode>): BaseAstNode? {
    if (items.isEmpty()) {
        return null
    }
    if (items.size == 1) {
        return items[0]
    }

    var i = 0

    /**
     * Reads nodes of same height and concatenates them to a single node.
     */
    fun readNode(): BaseAstNode? {
        if (i >= items.size) {
            return null
        }
        val start = i
        val height = items[start].listHeight

        i++
        while (i < items.size && items[i].listHeight == height) {
            i++
        }

        return if (i - start >= 2) {
            val slice = if (start == 0 && i == items.size) {
                items
            } else {
                items.subList(start, i)
            }
            concat23TreesOfSameHeight(slice.toMutableList(), false)
        } else {
            items[start]
        }
    }

    // The items might not have the same height.
    // We merge all items by using a binary concat operator.
    var first = readNode()!! // There must be a first item
    var second = readNode() ?: return first

    var item = readNode()
    while (item != null) {
        // Prefer concatenating smaller trees, as the runtime of concat depends on the tree height.
        if (first.heightDiff(second) <= second.heightDiff(item)) {
            first = first.concat(second)
            second = item
        } else {
            second = second.concat(item)
        }
        item = readNode()
    }

    return first.concat(second)
}

/**
 * Concatenates a list of (2,3) AstNode's of the same height into a single (2,3) AstNode.
 */
fun concat23TreesOfSameHeight(
    items: MutableList<BaseAstNode>,
    createImmutableLists: Boolean = false
): BaseAstNode? {
    if (items.isEmpty()) {
        return null
    }
    if (items.size == 1) {
        return items[0]
    }

    var length = items.size
    // All trees have same height, just create parent nodes.
    while (length > 3) {
        val newLength = length shr 1
        for (i in 0 until newLength) {
            val j = i shl 1
            items[i] = ListAstNode.create23(
                items[j],
                items[j + 1],
                if (j + 3 == length) items[j + 2] else null,
                createImmutableLists
            )
        }
        length = newLength
    }
    return ListAstNode.create23(
        items[0],
        items[1],
        if (length >= 3) items[2] else null,
        createImmutableLists
    )
}

/**
 * Calculates the absolute difference in list height between two nodes.
 */
fun BaseAstNode.heightDiff(other: BaseAstNode): Int {
    return abs(this.listHeight - other.listHeight)
}

/**
 * Concatenates two (2,3) tree nodes into a single (2,3) tree node.
 */
fun BaseAstNode.concat(other: BaseAstNode): BaseAstNode {
    return when {
        this.listHeight == other.listHeight -> {
            ListAstNode.create23(this, other, null, false)
        }

        this.listHeight > other.listHeight -> {
            // this is the tree we want to insert into
            (this as ListAstNode).append(other)
        }

        else -> {
            (other as ListAstNode).prepend(this)
        }
    }
}

/**
 * Appends the given node to the end of this (2,3) tree.
 * Returns the new root.
 */
fun ListAstNode.append(nodeToAppend: BaseAstNode): BaseAstNode {
    val list = this.toMutable()
    var curNode: BaseAstNode = list
    val parents = mutableListOf<ListAstNode>()
    var nodeToAppendOfCorrectHeight: BaseAstNode? = null

    while (true) {
        // assert nodeToInsert.listHeight <= curNode.listHeight
        if (nodeToAppend.listHeight == curNode.listHeight) {
            nodeToAppendOfCorrectHeight = nodeToAppend
            break
        }
        // assert 0 <= nodeToInsert.listHeight < curNode.listHeight
        if (curNode !is ListAstNode) {
            throw IllegalStateException("unexpected")
        }
        parents.add(curNode)
        // assert 2 <= curNode.childrenLength <= 3
        curNode = curNode.makeLastElementMutable()!!
    }

    // assert nodeToAppendOfCorrectHeight!.listHeight === curNode.listHeight
    for (i in parents.size - 1 downTo 0) {
        val parent = parents[i]
        if (nodeToAppendOfCorrectHeight != null) {
            // Can we take the element?
            if (parent.childrenLength >= 3) {
                // assert parent.childrenLength === 3 && parent.listHeight === nodeToAppendOfCorrectHeight.listHeight + 1

                // we need to split to maintain (2,3)-tree property.
                // Send the third element + the new element to the parent.
                nodeToAppendOfCorrectHeight = ListAstNode.create23(
                    parent.unappendChild()!!,
                    nodeToAppendOfCorrectHeight,
                    null,
                    false
                )
            } else {
                parent.appendChildOfSameHeight(nodeToAppendOfCorrectHeight)
                nodeToAppendOfCorrectHeight = null
            }
        } else {
            parent.handleChildrenChanged()
        }
    }

    return if (nodeToAppendOfCorrectHeight != null) {
        ListAstNode.create23(list, nodeToAppendOfCorrectHeight, null, false)
    } else {
        list
    }
}

/**
 * Prepends the given node to the beginning of this (2,3) tree.
 * Returns the new root.
 */
fun ListAstNode.prepend(nodeToPrepend: BaseAstNode): BaseAstNode {
    val list = this.toMutable()
    var curNode: BaseAstNode = list
    val parents = mutableListOf<ListAstNode>()

    // assert nodeToInsert.listHeight <= curNode.listHeight
    while (nodeToPrepend.listHeight != curNode.listHeight) {
        // assert 0 <= nodeToInsert.listHeight < curNode.listHeight
        if (curNode !is ListAstNode) {
            throw IllegalStateException("unexpected")
        }
        parents.add(curNode)
        // assert 2 <= curNode.childrenFast.length <= 3
        curNode = curNode.makeFirstElementMutable()!!
    }

    var nodeToPrependOfCorrectHeight: BaseAstNode? = nodeToPrepend

    // assert nodeToAppendOfCorrectHeight!.listHeight === curNode.listHeight
    for (i in parents.size - 1 downTo 0) {
        val parent = parents[i]
        if (nodeToPrependOfCorrectHeight != null) {
            // Can we take the element?
            if (parent.childrenLength >= 3) {
                // assert parent.childrenLength === 3 && parent.listHeight === nodeToAppendOfCorrectHeight.listHeight + 1

                // we need to split to maintain (2,3)-tree property.
                // Send the third element + the new element to the parent.
                nodeToPrependOfCorrectHeight = ListAstNode.create23(
                    nodeToPrependOfCorrectHeight,
                    parent.unprependChild()!!,
                    null,
                    false
                )
            } else {
                parent.prependChildOfSameHeight(nodeToPrependOfCorrectHeight)
                nodeToPrependOfCorrectHeight = null
            }
        } else {
            parent.handleChildrenChanged()
        }
    }

    return if (nodeToPrependOfCorrectHeight != null) {
        ListAstNode.create23(nodeToPrependOfCorrectHeight, list, null, false)
    } else {
        list
    }
}
