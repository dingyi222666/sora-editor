/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package io.github.rosemoe.sora.lang.brackets.tree.tokenizer

import io.github.rosemoe.sora.lang.brackets.tree.Length
import io.github.rosemoe.sora.lang.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.lang.brackets.tree.ast.TextAstNode
import io.github.rosemoe.sora.lang.brackets.tree.toLength
import io.github.rosemoe.sora.text.ContentReference

internal class FastTokenizer(
    private val text: ContentReference,
    brackets: BracketTokens
) : Tokenizer {
    private var _offset: Length = Length.ZERO
    private val tokens: List<Token>
    private var idx = 0

    override val length: Length

    init {
        val regExpStr = brackets.getRegExpStr()
        val regexp = regExpStr?.let { Regex("$it|\\n", RegexOption.IGNORE_CASE) }

        val tokensList = mutableListOf<Token>()

        var curLineCount = 0
        var lastLineBreakOffset = 0

        var lastTokenEndOffset = 0
        var lastTokenEndLine = 0

        val smallTextTokens0Line = Array(60) { i ->
            Token(
                toLength(0, i),
                TokenKind.Text,
                -1,
                SmallImmutableSet.getEmpty(),
                TextAstNode(toLength(0, i))
            )
        }

        val smallTextTokens1Line = Array(60) { i ->
            Token(
                toLength(1, i),
                TokenKind.Text,
                -1,
                SmallImmutableSet.getEmpty(),
                TextAstNode(toLength(1, i))
            )
        }

        if (regexp != null) {
            // If a token contains indentation, it also contains \n{INDENTATION+}(?!{INDENTATION})
            var match = regexp.find(text)
            while (match != null) {
                val curOffset = match.range.first
                val value = match.value

                if (value == "\n") {
                    curLineCount++
                    lastLineBreakOffset = curOffset + 1
                } else {
                    if (lastTokenEndOffset != curOffset) {
                        val token: Token = if (lastTokenEndLine == curLineCount) {
                            val colCount = curOffset - lastTokenEndOffset
                            if (colCount < smallTextTokens0Line.size) {
                                smallTextTokens0Line[colCount]
                            } else {
                                val length = toLength(0, colCount)
                                Token(
                                    length,
                                    TokenKind.Text,
                                    -1,
                                    SmallImmutableSet.getEmpty(),
                                    TextAstNode(length)
                                )
                            }
                        } else {
                            val lineCount = curLineCount - lastTokenEndLine
                            val colCount = curOffset - lastLineBreakOffset
                            if (lineCount == 1 && colCount < smallTextTokens1Line.size) {
                                smallTextTokens1Line[colCount]
                            } else {
                                val length = toLength(lineCount, colCount)
                                Token(
                                    length,
                                    TokenKind.Text,
                                    -1,
                                    SmallImmutableSet.getEmpty(),
                                    TextAstNode(length)
                                )
                            }
                        }
                        tokensList.add(token)
                    }

                    // value is matched by regexp, so the token must exist
                    tokensList.add(brackets.getToken(value)!!)

                    lastTokenEndOffset = curOffset + value.length
                    lastTokenEndLine = curLineCount
                }

                match = match.next()
            }
        }

        val offset = text.length

        if (lastTokenEndOffset != offset) {
            val length = if (lastTokenEndLine == curLineCount) {
                toLength(0, offset - lastTokenEndOffset)
            } else {
                toLength(curLineCount - lastTokenEndLine, offset - lastLineBreakOffset)
            }
            tokensList.add(
                Token(
                    length,
                    TokenKind.Text,
                    -1,
                    SmallImmutableSet.getEmpty(),
                    TextAstNode(length)
                )
            )
        }

        this.length = toLength(curLineCount, offset - lastLineBreakOffset)
        this.tokens = tokensList
    }

    override val offset: Length
        get() = _offset

    override fun read(): Token? {
        return tokens.getOrNull(idx++)
    }

    override fun peek(): Token? {
        return tokens.getOrNull(idx)
    }

    override fun skip(length: Length) {
        throw NotSupportedError()
    }

    override fun getText(): String {
        return text.toString()
    }
}

class NotSupportedError : UnsupportedOperationException("Operation not supported")
