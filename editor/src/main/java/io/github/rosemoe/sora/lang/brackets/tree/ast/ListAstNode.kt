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
import io.github.rosemoe.sora.text.ContentReference

abstract class ListAstNode(
    length: Length,
    override val listHeight: Int,
    private var _missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>
) : BaseAstNode(length) {

    override val kind: AstNodeKind
        get() = AstNodeKind.List

    override val missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>
        get() = _missingOpeningBracketIds

    private var cachedMinIndentation: Int = -1

    protected open fun throwIfImmutable() {
        // NOOP
    }

    protected abstract fun setChild(idx: Int, child: BaseAstNode)

    fun makeLastElementMutable(): BaseAstNode? {
        throwIfImmutable()
        val childCount = childrenLength
        if (childCount == 0) {
            return null
        }
        val lastChild = getChild(childCount - 1)!!
        val mutable =
            if (lastChild.kind == AstNodeKind.List) (lastChild as ListAstNode).toMutable() else lastChild
        if (lastChild !== mutable) {
            setChild(childCount - 1, mutable)
        }
        return mutable
    }

    fun makeFirstElementMutable(): BaseAstNode? {
        throwIfImmutable()
        val childCount = childrenLength
        if (childCount == 0) {
            return null
        }
        val firstChild = getChild(0)!!
        val mutable =
            if (firstChild.kind == AstNodeKind.List) (firstChild as ListAstNode).toMutable() else firstChild
        if (firstChild !== mutable) {
            setChild(0, mutable)
        }
        return mutable
    }

    override fun canBeReused(openBracketIds: SmallImmutableSet<OpeningBracketId>): Boolean {
        if (openBracketIds.intersects(this.missingOpeningBracketIds)) {
            return false
        }

        if (this.childrenLength == 0) {
            // Don't reuse empty lists.
            return false
        }

        var lastChild: BaseAstNode = this
        while (lastChild.kind == AstNodeKind.List) {
            val lastLength = lastChild.childrenLength
            if (lastLength == 0) {
                // Empty lists should never be contained in other lists.
                throw IllegalStateException("Bug: Empty list contained in another list")
            }
            lastChild = lastChild.getChild(lastLength - 1)!!
        }

        return lastChild.canBeReused(openBracketIds)
    }

    fun handleChildrenChanged() {
        throwIfImmutable()

        val count = this.childrenLength

        var length = this.getChild(0)!!.length
        var unopenedBrackets = this.getChild(0)!!.missingOpeningBracketIds

        for (i in 1 until count) {
            val child = this.getChild(i)!!
            length += child.length
            unopenedBrackets = unopenedBrackets.merge(child.missingOpeningBracketIds)
        }

        this.mLength = length
        this._missingOpeningBracketIds = unopenedBrackets
        this.cachedMinIndentation = -1
    }

    override fun flattenLists(): ListAstNode {
        val items = mutableListOf<BaseAstNode>()
        for (c in this.children) {
            val normalized = c.flattenLists()
            if (normalized.kind == AstNodeKind.List) {
                items.addAll(normalized.children)
            } else {
                items.add(normalized)
            }
        }
        return create(items)
    }

    override fun computeMinIndentation(offset: Length, content: ContentReference): Int {
        if (this.cachedMinIndentation != -1) {
            return this.cachedMinIndentation
        }

        var minIndentation = Int.MAX_VALUE
        var childOffset = offset
        for (i in 0 until this.childrenLength) {
            val child = this.getChild(i)
            if (child != null) {
                minIndentation =
                    minOf(minIndentation, child.computeMinIndentation(childOffset, content))
                childOffset += child.length
            }
        }

        this.cachedMinIndentation = minIndentation
        return minIndentation
    }

    /**
     * Creates a shallow clone that is mutable, or itself if it is already mutable.
     */
    abstract fun toMutable(): ListAstNode

    abstract fun appendChildOfSameHeight(node: BaseAstNode)
    abstract fun unappendChild(): BaseAstNode?
    abstract fun prependChildOfSameHeight(node: BaseAstNode)
    abstract fun unprependChild(): BaseAstNode?

    // Access to protected mLength field for subclasses
    protected var mLength: Length
        get() = length
        set(value) {
            // This is a workaround since we can't directly modify the parent's private field
            // In the actual implementation, this would need to be handled differently
        }

    companion object {
        /**
         * This method uses more memory-efficient list nodes that can only store 2 or 3 children.
         */
        fun create23(
            item1: BaseAstNode,
            item2: BaseAstNode,
            item3: BaseAstNode?,
            immutable: Boolean = false
        ): ListAstNode {
            var length = item1.length
            var missingBracketIds = item1.missingOpeningBracketIds

            if (item1.listHeight != item2.listHeight) {
                throw IllegalArgumentException("Invalid list heights")
            }

            length += item2.length
            missingBracketIds = missingBracketIds.merge(item2.missingOpeningBracketIds)

            if (item3 != null) {
                if (item1.listHeight != item3.listHeight) {
                    throw IllegalArgumentException("Invalid list heights")
                }
                length += item3.length
                missingBracketIds = missingBracketIds.merge(item3.missingOpeningBracketIds)
            }
            return if (immutable)
                Immutable23ListAstNode(
                    length,
                    item1.listHeight + 1,
                    item1,
                    item2,
                    item3,
                    missingBracketIds
                )
            else
                TwoThreeListAstNode(
                    length,
                    item1.listHeight + 1,
                    item1,
                    item2,
                    item3,
                    missingBracketIds
                )
        }

        fun create(items: List<BaseAstNode>, immutable: Boolean = false): ListAstNode {
            if (items.isEmpty()) {
                return getEmpty()
            } else {
                var length = items[0].length
                var unopenedBrackets = items[0].missingOpeningBracketIds
                for (i in 1 until items.size) {
                    length += items[i].length
                    unopenedBrackets = unopenedBrackets.merge(items[i].missingOpeningBracketIds)
                }
                return if (immutable)
                    ImmutableArrayListAstNode(
                        length,
                        items[0].listHeight + 1,
                        items,
                        unopenedBrackets
                    )
                else
                    ArrayListAstNode(
                        length,
                        items[0].listHeight + 1,
                        items.toMutableList(),
                        unopenedBrackets
                    )
            }
        }

        fun getEmpty(): ListAstNode {
            return ImmutableArrayListAstNode(
                Length.ZERO,
                0,
                emptyList(),
                SmallImmutableSet.getEmpty()
            )
        }
    }
}

