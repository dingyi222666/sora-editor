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

import io.github.rosemoe.sora.langs.textmate.brackets.BracketKind
import io.github.rosemoe.sora.langs.textmate.brackets.BracketsConfiguration
import io.github.rosemoe.sora.langs.textmate.brackets.tree.DenseKeyProvider
import io.github.rosemoe.sora.langs.textmate.brackets.tree.IDenseKeyProvider
import io.github.rosemoe.sora.langs.textmate.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.langs.textmate.brackets.tree.ast.BracketAstNode
import io.github.rosemoe.sora.langs.textmate.brackets.tree.toLength


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
        @JvmStatic
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
                    getId(closingBrackets.valueAt(0)),
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

val identityKeyProvider = IDenseKeyProvider<Int> { value -> value }