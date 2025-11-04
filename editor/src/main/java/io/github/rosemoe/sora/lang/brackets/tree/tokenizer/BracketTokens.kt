/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package io.github.rosemoe.sora.lang.brackets.tree.tokenizer

import io.github.rosemoe.sora.lang.brackets.tree.DenseKeyProvider
import io.github.rosemoe.sora.lang.brackets.tree.IDenseKeyProvider
import io.github.rosemoe.sora.lang.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.lang.brackets.tree.ast.BracketAstNode
import io.github.rosemoe.sora.lang.brackets.tree.toLength

class BracketTokens private constructor(
    private val map: Map<String, Token>
) {
    private var hasRegExp = false
    private var _regExpGlobal: Regex? = null

    fun getRegExpStr(): String? {
        if (isEmpty) {
            return null
        } else {
            val keys = map.keys.toList().sorted().reversed()
            return keys.joinToString("|") { prepareBracketForRegExp(it) }
        }
    }

    /**
     * Returns null if there is no such regexp (because there are no brackets).
     */
    val regExpGlobal: Regex?
        get() {
            if (!hasRegExp) {
                val regExpStr = getRegExpStr()
                _regExpGlobal = regExpStr?.let { Regex(it, RegexOption.IGNORE_CASE) }
                hasRegExp = true
            }
            return _regExpGlobal
        }

    fun getToken(value: String): Token? {
        return map[value.lowercase()]
    }

    fun findClosingTokenText(openingBracketIds: SmallImmutableSet<OpeningBracketId>): String? {
        for ((closingText, info) in map) {
            if (info.kind == TokenKind.ClosingBracket && info.bracketIds.intersects(
                    openingBracketIds
                )
            ) {
                return closingText
            }
        }
        return null
    }

    val isEmpty: Boolean
        get() = map.isEmpty()

    companion object {
        fun createFromLanguage(
            configuration: BracketsConfiguration,
        ): BracketTokens {

            val denseKeyProvider = DenseKeyProvider<String>()

            configuration.openingBrackets.forEachIndexed { index, kind ->
                denseKeyProvider.set(kind.bracketText, index)
            }

            configuration.closingBrackets.forEachIndexed { index, kind ->
                denseKeyProvider.set(kind.bracketText, index)
            }

            fun getId(bracketInfo: BracketKind): OpeningBracketId {
                return denseKeyProvider.getKey(bracketInfo.bracketText)
            }

            val map = mutableMapOf<String, Token>()

            for (openingBracket in configuration.openingBrackets) {
                val length = toLength(0, openingBracket.bracketText.length)
                val openingTextId = getId(openingBracket)
                val bracketIds = SmallImmutableSet.getEmpty<OpeningBracketId>()
                    .add(openingTextId, identityKeyProvider)
                map[openingBracket.bracketText] = Token(
                    length,
                    TokenKind.OpeningBracket,
                    openingTextId,
                    bracketIds,
                    BracketAstNode.create(length, openingBracket, bracketIds)
                )
            }

            for (closingBracket in configuration.closingBrackets) {
                val length = toLength(0, closingBracket.bracketText.length)
                var bracketIds = SmallImmutableSet.getEmpty<OpeningBracketId>()
                val closingBrackets = closingBracket.openingBrackets
                for (bracket in closingBrackets) {
                    bracketIds = bracketIds.add(getId(bracket), identityKeyProvider)
                }
                map[closingBracket.bracketText] = Token(
                    length,
                    TokenKind.ClosingBracket,
                    getId(closingBrackets[0]),
                    bracketIds,
                    BracketAstNode.create(length, closingBracket, bracketIds)
                )
            }

            return BracketTokens(map)
        }
    }
}

private fun prepareBracketForRegExp(str: String): String {
    var escaped = Regex.escape(str)
    // These bracket pair delimiters start or end with letters
    // see https://github.com/microsoft/vscode/issues/132162 https://github.com/microsoft/vscode/issues/150440
    if (Regex("^[\\w ]+").matches(str)) {
        escaped = "\\b$escaped"
    }
    if (Regex("[\\w ]+$").containsMatchIn(str)) {
        escaped = "$escaped\\b"
    }
    return escaped
}


interface BracketsConfiguration {
    val openingBrackets: List<BracketKind>
    val closingBrackets: List<ClosingBracketKind>
}


open class BracketKind(
    open val bracketText: String
) {
    override fun toString(): String {
        return "BracketKind(bracketText='$bracketText')"
    }
}


data class ClosingBracketKind(
    val openingBrackets: List<BracketKind>,
    override val bracketText: String
) : BracketKind(bracketText)


val identityKeyProvider = IDenseKeyProvider<Int> { value -> value }