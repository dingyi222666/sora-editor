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

    abstract fun computeMinIndentation(offset: Length, content: ContentReference): Int
}