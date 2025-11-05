/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree

import io.github.rosemoe.sora.lang.brackets.tree.ast.BaseAstNode

/**
 * Allows to efficiently find a longest child at a given offset in a fixed node.
 * The requested offsets must increase monotonously.
 */
class NodeReader(node: BaseAstNode) {
    private val nextNodes: MutableList<BaseAstNode> = mutableListOf(node)
    private val offsets: MutableList<Length> = mutableListOf(Length.ZERO)
    private val idxs: MutableList<Int> = mutableListOf()
    private var lastOffset: Length = Length.ZERO

    /**
     * Returns the longest node at `offset` that satisfies the predicate.
     * @param offset must be greater than or equal to the last offset this method has been called with!
     */
    fun readLongestNodeAt(offset: Length, predicate: (BaseAstNode) -> Boolean): BaseAstNode? {
        if (offset.value < lastOffset.value) {
            throw IllegalArgumentException("Invalid offset")
        }
        lastOffset = offset

        // Find the longest node of all those that are closest to the current offset.
        while (true) {
            val curNode = nextNodes.lastOrNull() ?: return null
            val curNodeOffset = offsets.lastOrNull()!!

            if (offset.value < curNodeOffset.value) {
                // The next best node is not here yet.
                // The reader must advance before a cached node is hit.
                return null
            }

            if (curNodeOffset.value < offset.value) {
                // The reader is ahead of the current node.
                if ((curNodeOffset + curNode.length).value <= offset.value) {
                    // The reader is after the end of the current node.
                    nextNodeAfterCurrent()
                } else {
                    // The reader is somewhere in the current node.
                    val nextChildIdx = getNextChildIdx(curNode)
                    if (nextChildIdx != -1) {
                        // Go to the first child and repeat.
                        nextNodes.add(curNode.getChild(nextChildIdx)!!)
                        offsets.add(curNodeOffset)
                        idxs.add(nextChildIdx)
                    } else {
                        // We don't have children
                        nextNodeAfterCurrent()
                    }
                }
            } else {
                // readerOffsetBeforeChange === curNodeOffset
                if (predicate(curNode)) {
                    nextNodeAfterCurrent()
                    return curNode
                } else {
                    val nextChildIdx = getNextChildIdx(curNode)
                    // look for shorter node
                    if (nextChildIdx == -1) {
                        // There is no shorter node.
                        nextNodeAfterCurrent()
                        return null
                    } else {
                        // Descend into first child & repeat.
                        nextNodes.add(curNode.getChild(nextChildIdx)!!)
                        offsets.add(curNodeOffset)
                        idxs.add(nextChildIdx)
                    }
                }
            }
        }
    }

    // Navigates to the longest node that continues after the current node.
    private fun nextNodeAfterCurrent() {
        while (true) {
            val currentOffset = offsets.lastOrNull()
            val currentNode = nextNodes.lastOrNull()
            nextNodes.removeLastOrNull()
            offsets.removeLastOrNull()

            if (idxs.isEmpty()) {
                // We just popped the root node, there is no next node.
                break
            }

            // Parent is not undefined, because idxs is not empty
            val parent = nextNodes.lastOrNull()!!
            val nextChildIdx = getNextChildIdx(parent, idxs.last())

            if (nextChildIdx != -1) {
                nextNodes.add(parent.getChild(nextChildIdx)!!)
                offsets.add(currentOffset!! + currentNode!!.length)
                idxs[idxs.lastIndex] = nextChildIdx
                break
            } else {
                idxs.removeLastOrNull()
            }
            // We fully consumed the parent.
            // Current node is now parent, so call nextNodeAfterCurrent again
        }
    }
}

private fun getNextChildIdx(node: BaseAstNode, curIdx: Int = -1): Int {
    var idx = curIdx
    while (true) {
        idx++
        if (idx >= node.childrenLength) {
            return -1
        }
        if (node.getChild(idx) != null) {
            return idx
        }
    }
}
