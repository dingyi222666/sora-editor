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

class TextAstNode(length: Length) : BaseAstNode(length) {

    override val kind: AstNodeKind
        get() = AstNodeKind.Text

    override val listHeight = 0

    override val childrenLength = 0

    override fun getChild(idx: Int): BaseAstNode? = null

    override val children: List<BaseAstNode>
        get() = emptyList()

    override val missingOpeningBracketIds: SmallImmutableSet<OpeningBracketId>
        get() = SmallImmutableSet.getEmpty()

    override fun canBeReused(openBracketIds: SmallImmutableSet<OpeningBracketId>): Boolean {
        return true
    }

    override fun flattenLists(): TextAstNode {
        return this
    }

    override fun deepClone(): TextAstNode {
        return this
    }

    override fun computeMinIndentation(offset: Length, content: ContentReference): Int {
        // Text ast nodes don't have partial indentation (ensured by the tokenizer).
        // Thus, if this text node does not start at column 0, the first line cannot have any indentation at all.
        val startLineNumber = (if (offset.columnCount == 0) offset.lineCount else offset.lineCount + 1) + 1
        val endLineNumber = (offset + this.length).lineCount + 1

        var result = Int.MAX_VALUE

        for (lineNumber in startLineNumber..endLineNumber) {
            val firstNonWsColumn = content.getColumnCount(lineNumber)
            val lineContent = content.getLine(lineNumber)
            if (firstNonWsColumn == 0) {
                continue
            }

            // TODO: Implement visibleColumnFromColumn equivalent
            // For now, using a simplified version
            result = minOf(result, firstNonWsColumn)
        }

        return result
    }
}
