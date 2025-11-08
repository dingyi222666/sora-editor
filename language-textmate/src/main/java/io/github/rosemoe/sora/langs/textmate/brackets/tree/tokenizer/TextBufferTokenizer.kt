/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree.tokenizer

import io.github.rosemoe.sora.lang.brackets.tree.Length
import io.github.rosemoe.sora.lang.brackets.tree.toLength
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.lang.styling.StandardTokenType
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.text.ContentReference
import kotlin.math.max


class TextBufferTokenizer(
    private val content: ContentReference,
    spans: Spans,
    bracketTokens: BracketTokens
) : Tokenizer {
    private val reader: NonPeekableTextBufferTokenizer =
        NonPeekableTextBufferTokenizer(content, spans, bracketTokens)
    private val textBufferLineCount: Int
    private val textBufferLastLineLength: Int

    private var _offset: Length = Length.ZERO
    private var didPeek: Boolean
    private var peeked: Token?

    init {
        didPeek = false
        peeked = null
        textBufferLineCount = content.getLineCount() - 1
        textBufferLastLineLength = content.getColumnCount(textBufferLineCount)
    }

    override val offset: Length
        get() = _offset

    override val length: Length
        get() = toLength(textBufferLineCount, textBufferLastLineLength)

    override fun getText(): CharSequence {
        return content
    }

    override fun skip(length: Length) {
        didPeek = false
        _offset += length
        val lineCount = _offset.lineCount
        val columnCount = _offset.columnCount
        reader.setPosition(lineCount, columnCount)
    }

    override fun read(): Token? {
        val token: Token? = if (peeked != null) {
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
}

/**
 * Does not support peek.
 */
private class NonPeekableTextBufferTokenizer(
    private val textModel: ContentReference,
    private val spans: Spans,
    private val bracketTokens: BracketTokens
) {
    private val textBufferLineCount: Int = textModel.getLineCount() - 1
    private val textBufferLastLineLength: Int = textModel.getColumnCount(textBufferLineCount)

    private var lineIdx = 0
    private var line: CharSequence? = null
    private var lineCharOffset = 0
    private var lineTokenOffset = 0
    private var lineTokens: List<Span>? = null

    /** Must be a zero line token. The end of the document cannot be peeked. */
    private var peekedToken: Token? = null

    private var reader: Spans.Reader? = null

    /**
     * Finds the token index at the given offset.
     * Returns the index of the token that contains the given offset.
     */
    private fun findTokenIndexAtOffset(offset: Int): Int {
        val tokens = lineTokens ?: return 0
        if (tokens.isEmpty()) return 0

        // Binary search to find the token that contains the offset
        var left = 0
        var right = tokens.size - 1

        while (left < right) {
            val mid = (left + right + 1) / 2
            if (tokens[mid].column <= offset) {
                left = mid
            } else {
                right = mid - 1
            }
        }

        return left
    }

    fun setPosition(lineIdx: Int, column: Int) {
        // We must not jump into a token!
        if (lineIdx == this.lineIdx) {
            this.lineCharOffset = column
            if (this.line != null) {
                this.lineTokenOffset = if (this.lineCharOffset == 0) {
                    0
                } else {
                    findTokenIndexAtOffset(lineCharOffset)
                }
            }
        } else {
            this.lineIdx = lineIdx
            this.lineCharOffset = column
            this.line = null
        }
        this.peekedToken = null
    }

    fun read(): Token? {
        val reader = this.reader ?: spans.read()
        this.reader = reader

        if (peekedToken != null) {
            val token = peekedToken ?: return null
            peekedToken = null
            lineCharOffset += lengthGetColumnCountIfZeroLineCount(token.length)
            return token
        }

        if (lineIdx > textBufferLineCount ||
            (lineIdx == textBufferLineCount && lineCharOffset >= textBufferLastLineLength)
        ) {
            // We are after the end
            return null
        }

        if (line == null) {
            lineTokens = reader.getSpansOnLine(lineIdx)
            line = textModel.getLine(lineIdx)
            lineTokenOffset = if (lineCharOffset == 0) {
                0
            } else {
                findTokenIndexAtOffset(lineCharOffset)
            }
        }

        val startLineIdx = lineIdx
        val startLineCharOffset = lineCharOffset

        // limits the length of text tokens.
        // If text tokens get too long, incremental updates will be slow
        var lengthHeuristic = 0

        while (true) {
            val lineTokens = this.lineTokens ?: break
            val tokenCount = lineTokens.size
            val line = this.line ?: break

            var peekedBracketToken: Token? = null

            if (lineTokenOffset < tokenCount) {
                val tokenMetadata = lineTokens.getMetadata(lineTokenOffset)
                while (lineTokenOffset + 1 < tokenCount &&
                    tokenMetadata == lineTokens.getMetadata(lineTokenOffset + 1)
                ) {
                    // Skip tokens that are identical.
                    // Sometimes, (bracket) identifiers are split up into multiple tokens.
                    lineTokenOffset++
                }

                val isOther = tokenMetadata == StandardTokenType.Other
                // val containsBracketType = TokenMetadata.containsBalancedBrackets(tokenMetadata)

                val endOffset = lineTokens.getEndOffset(lineTokenOffset)
                // Is there a bracket token next? Only consume text.
                if (isOther && lineCharOffset < endOffset) {
                    val text = line.substring(lineCharOffset, endOffset)

                    val regexp = bracketTokens.regExpGlobal
                    if (regexp != null) {
                        val match = regexp.find(text)
                        if (match != null) {
                            peekedBracketToken = bracketTokens.getToken(match.value)
                            if (peekedBracketToken != null) {
                                // Consume leading text of the token
                                lineCharOffset += match.range.first
                            }
                        }
                    }
                }

                lengthHeuristic += endOffset - lineCharOffset

                if (peekedBracketToken != null) {
                    // Don't skip the entire token, as a single token could contain multiple brackets
                    if (startLineIdx != lineIdx || startLineCharOffset != lineCharOffset) {
                        // There is text before the bracket
                        this.peekedToken = peekedBracketToken
                        break
                    } else {
                        // Consume the peeked token
                        lineCharOffset += lengthGetColumnCountIfZeroLineCount(peekedBracketToken.length)
                        return peekedBracketToken
                    }
                } else {
                    // Skip the entire token, as the token contains no brackets at all.
                    lineTokenOffset++
                    lineCharOffset = endOffset
                }
            } else {
                if (lineIdx == textBufferLineCount) {
                    break
                }
                lineIdx++
                this.lineTokens = reader.getSpansOnLine(lineIdx)
                lineTokenOffset = 0
                this.line = textModel.getLine(lineIdx)
                lineCharOffset = 0

                lengthHeuristic += 33 // max 1000/33 = 30 lines
                // This limits the amount of work to recompute min-indentation

                if (lengthHeuristic > 1000) {
                    // only break (automatically) at the end of line.
                    break
                }
            }

            if (lengthHeuristic > 1500) {
                // Eventually break regardless of the line length so that
                // very long lines do not cause bad performance.
                // This effective limits max indentation to 500, as
                // indentation is not computed across multiple text nodes.
                break
            }
        }

        // If a token contains some proper indentation, it also contains \n{INDENTATION+}(?!{INDENTATION}),
        // unless the line is too long.
        // Thus, the min indentation of the document is the minimum min indentation of every text node.
        val length =
            Length.lengthDiff(
                startLineIdx,
                startLineCharOffset,
                lineIdx,
                lineCharOffset
            )
        return TokenAllocator.obtainTextToken(length)
    }

    fun List<Span>.getMetadata(offset: Int): Int? {
        val style = get(offset).style
        val tokenType = TextStyle.getTokenType(style)

        return if (tokenType == -1) null else tokenType
    }

    fun List<Span>.getEndOffset(offset: Int): Int {
        return if (offset == lastIndex) {
            max(line?.length ?: 0, 0)
        } else {
            val next = get(offset + 1)
            next.column
        }
    }
}


private fun lengthGetColumnCountIfZeroLineCount(length: Length): Int {
    return if (length.lineCount == 0) length.columnCount else 0
}

