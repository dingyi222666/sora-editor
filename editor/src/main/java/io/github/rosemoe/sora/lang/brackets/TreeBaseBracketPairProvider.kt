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

package io.github.rosemoe.sora.lang.brackets

import androidx.annotation.WorkerThread
import io.github.rosemoe.sora.lang.analysis.StyleUpdateRange
import io.github.rosemoe.sora.lang.brackets.tree.BracketPairsTree
import io.github.rosemoe.sora.lang.brackets.tree.TreeContentSnapshotProvider
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.BracketTokens
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.Indexer
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.util.IntPair
import kotlin.math.max
import kotlin.math.min

/**
 * Base implementation of [BracketsProvider] backed by [io.github.rosemoe.sora.lang.brackets.tree.BracketPairsTree] that works with
 * asynchronous analyzers. The provider expects tree updates to be performed from a worker
 * thread, while read operations may happen from any thread. All accesses to the underlying
 * tree are serialized by an internal lock.
 */
class TreeBaseBracketPairProvider(
    snapshotProvider: TreeContentSnapshotProvider,
    bracketTokens: BracketTokens
) : BracketsProvider {

    private val tree = BracketPairsTree(snapshotProvider, bracketTokens)

    fun handleContentChanged(start: CharPosition, end: CharPosition) {
        tree.handleContentChanged(start, end)
    }

    fun handleDidChangeTokens(range: StyleUpdateRange) {
        tree.handleDidChangeTokens(range)
    }

    @WorkerThread
    fun flush() {
        tree.flushQueue()
    }

    fun init() {
        tree.init()
    }

    override fun getPairedBracketAt(text: Content, index: Int): PairedBracket? {
        if (index < 0 || index > text.length) {
            return null
        }

        val indexer = text.getIndexer()
        val result = findPairAt(indexer, text, index)?.let { convertToPairedBracket(indexer, it) }
            ?: findPairAt(indexer, text, index)?.let { convertToPairedBracket(indexer, it) }

        println("11 $result")

        return result
    }

    override fun getPairedBracketsAtRange(
        text: Content,
        leftPosition: Long,
        rightPosition: Long
    ): List<PairedBracket?>? {
        val startLine = IntPair.getFirst(leftPosition)
        val startColumn = IntPair.getSecond(leftPosition)
        val endLine = IntPair.getFirst(rightPosition)
        val endColumn = IntPair.getSecond(rightPosition)

        val start = clampPosition(text, startLine, startColumn)
        val end = clampPosition(text, endLine, endColumn)
        val (normalizedStart, normalizedEnd) = if (start.line > end.line ||
            (start.line == end.line && start.column > end.column)
        ) {
            end to start
        } else {
            start to end
        }
        val range = TextRange(normalizedStart, normalizedEnd)
        val indexer = text.getIndexer()

        val pairs = mutableListOf<PairedBracket>()

        tree.getBracketPairsInRange(range, includeMinIndentation = false)
            .iterate { info ->
                convertToPairedBracket(indexer, info)?.let { pairs.add(it) }
                true
            }

        return if (pairs.isEmpty()) null else pairs
    }

    private fun findPairAt(
        indexer: Indexer,
        text: Content,
        index: Int
    ): BracketPairWithMinIndentationInfo? {
        if (index < 0 || index >= text.length) {
            return null
        }
        val position = indexer.getCharPosition(index)
        val nextIndex = min(index + 1, text.length)
        val endPosition = indexer.getCharPosition(nextIndex)
        val searchRange = TextRange(position.fromThis(), endPosition.fromThis())
        var matched: BracketPairWithMinIndentationInfo? = null
        tree.getBracketPairsInRange(searchRange, includeMinIndentation = false)
            .iterate { info ->
                println("$info $position")
                if (info.openingBracketRange.isInPosition(position) ||
                    info.closingBracketRange?.isInPosition(position) == true
                ) {
                    matched = info
                    return@iterate false
                }
                true
            }
        return matched
    }

    private fun convertToPairedBracket(
        indexer: Indexer,
        info: BracketPairWithMinIndentationInfo
    ): PairedBracket? {
        val closingRange = info.closingBracketRange ?: return null
        val openingStartIndex = indexer.getCharIndex(
            info.openingBracketRange.start.line,
            info.openingBracketRange.start.column
        )
        val openingEndIndex = indexer.getCharIndex(
            info.openingBracketRange.end.line,
            info.openingBracketRange.end.column
        )
        val closingStartIndex = indexer.getCharIndex(
            closingRange.start.line,
            closingRange.start.column
        )
        val closingEndIndex = indexer.getCharIndex(
            closingRange.end.line,
            closingRange.end.column
        )

        val openingLength = max(0, openingEndIndex - openingStartIndex)
        val closingLength = max(0, closingEndIndex - closingStartIndex)
        if (openingLength <= 0 || closingLength <= 0) {
            return null
        }

        return PairedBracket(
            openingStartIndex,
            openingLength,
            closingStartIndex,
            closingLength,
            info.nestingLevel
        )
    }

    private fun clampPosition(text: Content, line: Int, column: Int): CharPosition {
        val maxLine = max(0, text.lineCount - 1)
        val safeLine = line.coerceIn(0, maxLine)
        val lineLength = text.getColumnCount(safeLine)
        val safeColumn = column.coerceIn(0, lineLength)
        return CharPosition(safeLine, safeColumn)
    }
}



internal fun TextRange.isInPosition(position: CharPosition): Boolean {
    val start = start
    val end = end

    // Check if position is before the start
    if (position.line < start.line) {
        return false
    }
    if (position.line == start.line && position.column < start.column ) {
        return false
    }

    // Check if position is after the end
    if (position.line > end.line) {
        return false
    }
    if (position.line == end.line && position.column > end.column) {
        return false
    }

    return true
}