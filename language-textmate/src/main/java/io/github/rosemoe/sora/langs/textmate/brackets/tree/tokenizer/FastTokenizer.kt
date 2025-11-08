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

package io.github.rosemoe.sora.langs.textmate.brackets.tree.tokenizer


import io.github.rosemoe.sora.langs.textmate.brackets.tree.Length
import io.github.rosemoe.sora.langs.textmate.brackets.tree.toLength
import io.github.rosemoe.sora.text.Content
import kotlin.text.MatchResult

internal class FastTokenizer(
    private val text: Content,
    private val brackets: BracketTokens
) : Tokenizer {

    private val reader: FastContentReader
    private var _offset: Length = Length.ZERO
    private var didPeek = false
    private var peeked: Token? = null

    private val lastLineIndex: Int
    private val lastLineLength: Int

    init {
        val lineCount = text.lineCount
        if (lineCount <= 0) {
            lastLineIndex = 0
            lastLineLength = 0
        } else {
            lastLineIndex = lineCount - 1
            lastLineLength = text.getColumnCount(lastLineIndex)
        }
        reader = FastContentReader(text, brackets, lastLineIndex, lastLineLength)
    }

    override val offset: Length
        get() = _offset

    override val length: Length
        get() = toLength(lastLineIndex, lastLineLength)

    override fun read(): Token? {
        val token = if (didPeek) {
            didPeek = false
            val value = peeked
            peeked = null
            value
        } else {
            reader.read()
        }
        if (token != null) {
            _offset += token.length
        }
        return token
    }

    override fun peek(): Token? {
        if (!didPeek) {
            peeked = reader.read()
            didPeek = true
        }
        return peeked
    }

    override fun skip(length: Length) {
        if (length.value == 0L) {
            return
        }
        didPeek = false
        peeked = null
        _offset += length
        reader.setPosition(_offset.lineCount, _offset.columnCount)
    }

    override fun getText(): CharSequence {
        return text
    }
}

private class FastContentReader(
    private val content: Content,
    private val brackets: BracketTokens,
    private val lastLineIndex: Int,
    private val lastLineLength: Int
) {
    private val regex = brackets.regExpGlobal
    private val endLineIndex = lastLineIndex + 1

    private var lineIndex = 0
    private var column = 0
    private var currentLine: CharSequence? = null
    private var currentLineLength = 0
    private var pendingMatch: MatchResult? = null

    fun setPosition(line: Int, column: Int) {
        val normalizedLine = line.coerceAtLeast(0)
        if (normalizedLine >= endLineIndex) {
            this.lineIndex = endLineIndex
            this.column = 0
            this.currentLine = null
            this.pendingMatch = null
            return
        }
        val clampedLine = normalizedLine.coerceAtMost(lastLineIndex)
        val maxColumn = if (clampedLine == lastLineIndex) {
            lastLineLength
        } else {
            content.getColumnCount(clampedLine)
        }
        this.lineIndex = clampedLine
        this.column = column.coerceIn(0, maxColumn)
        this.currentLine = null
        this.pendingMatch = null
    }

    fun read(): Token? {
        if (lineIndex >= endLineIndex || (lineIndex == lastLineIndex && column >= lastLineLength)) {
            return null
        }

        ensureLineLoaded()

        if (column >= currentLineLength) {
            if (lineIndex >= lastLineIndex) {
                lineIndex = endLineIndex
                return null
            }
            lineIndex++
            column = 0
            currentLine = null
            pendingMatch = null
            return TokenAllocator.obtainTextToken(toLength(1, 0))
        }

        val lineText = currentLine ?: return null
        val match = pendingMatch ?: regex?.find(lineText, column)

        if (match == null) {
            val remaining = currentLineLength - column
            column = currentLineLength
            return TokenAllocator.obtainTextToken(toLength(0, remaining))
        }

        if (match.range.first > column) {
            pendingMatch = match
            val length = match.range.first - column
            column = match.range.first
            return TokenAllocator.obtainTextToken(toLength(0, length))
        }

        pendingMatch = null
        val token = brackets.getToken(match.value)
        if (token != null) {
            column += match.value.length
            return token
        }

        val fallbackLength = match.value.length
        column += fallbackLength
        return TokenAllocator.obtainTextToken(toLength(0, fallbackLength))
    }

    private fun ensureLineLoaded() {
        if (currentLine != null && column <= currentLineLength) {
            return
        }
        if (lineIndex >= endLineIndex) {
            return
        }
        currentLine = content.getLine(lineIndex)
        currentLineLength = content.getColumnCount(lineIndex)
        if (column > currentLineLength) {
            column = currentLineLength
        }
        pendingMatch = null
    }
}
