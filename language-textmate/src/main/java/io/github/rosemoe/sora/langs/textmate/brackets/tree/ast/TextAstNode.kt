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

    override fun computeMinIndentation(offset: Length, content: Content): Int {
        // Text ast nodes don't have partial indentation (ensured by the tokenizer).
        // Thus, if this text node does not start at column 0, the first line cannot have any indentation at all.
        val startLineNumber =
            (if (offset.columnCount == 0) offset.lineCount else offset.lineCount)
        val endLineNumber = (offset + this.length).lineCount

        var result = Int.MAX_VALUE

        for (lineNumber in startLineNumber..endLineNumber) {
            val firstNonWsColumn = content.getColumnCount(lineNumber)
            content.getLine(lineNumber)
            if (firstNonWsColumn == 0) {
                continue
            }

            // TODO: Implement visibleColumnFromColumn equivalent
            // For now, using a simplified version
            result = minOf(result, firstNonWsColumn)
        }

        return result
    }

    override fun toString(): String {
        return "TextAstNode(length=$length)"
    }
}
