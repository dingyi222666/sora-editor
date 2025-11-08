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

package io.github.rosemoe.sora.langs.textmate.brackets





import androidx.collection.ArraySet
import io.github.rosemoe.sora.langs.textmate.brackets.tree.ast.PairAstNode
import io.github.rosemoe.sora.langs.textmate.brackets.tree.parseDocument
import io.github.rosemoe.sora.text.TextRange
import org.eclipse.tm4e.languageconfiguration.internal.model.CharacterPair
import java.util.regex.Pattern



/**
 * Captures all bracket related configurations for a single language.
 * Immutable.
 */
class BracketsConfiguration(
    val brackets: List<CharacterPair> = emptyList(),
    val colorizedBracketPairs: List<CharacterPair>? = null
) {
    private val _openingBrackets: Map<String, OpeningBracketKind>
    private val _closingBrackets: Map<String, ClosingBracketKind>

    init {
        val bracketPairs = filterValidBrackets(brackets)

        // Cache for opening bracket infos
        val openingBracketInfos =
            mutableMapOf<String, Pair<OpeningBracketKind, MutableSet<ClosingBracketKind>>>()

        fun getOrCreateOpeningBracket(bracket: String): Pair<OpeningBracketKind, MutableSet<ClosingBracketKind>> {
            return openingBracketInfos.getOrPut(bracket) {
                val closing = mutableSetOf<ClosingBracketKind>()
                val info = OpeningBracketKind(
                    bracket,
                    ArraySet<ClosingBracketKind>().apply { addAll(closing) })
                info to closing
            }
        }

        // Cache for closing bracket infos
        val closingBracketInfos =
            mutableMapOf<String, Triple<ClosingBracketKind, MutableSet<OpeningBracketKind>, MutableSet<OpeningBracketKind>>>()

        fun getOrCreateClosingBracket(bracket: String): Triple<ClosingBracketKind, MutableSet<OpeningBracketKind>, MutableSet<OpeningBracketKind>> {
            return closingBracketInfos.getOrPut(bracket) {
                val opening = mutableSetOf<OpeningBracketKind>()
                val openingColorized = mutableSetOf<OpeningBracketKind>()
                val info = ClosingBracketKind(
                    bracket,
                    ArraySet<OpeningBracketKind>().apply { addAll(opening) },
                    ArraySet<OpeningBracketKind>().apply { addAll(openingColorized) }
                )
                Triple(info, opening, openingColorized)
            }
        }

        for (pair in bracketPairs) {
            val open = pair.open
            val close = pair.close
            val (openingInfo, openingClosing) = getOrCreateOpeningBracket(open)
            val (closingInfo, closingOpening, _) = getOrCreateClosingBracket(close)

            openingClosing.add(closingInfo)
            closingOpening.add(openingInfo)
        }

        // Treat colorized brackets as brackets, and mark them as colorized.
        val colorizedBracketPairs = colorizedBracketPairs?.let { filterValidBrackets(it) }
        // If not configured: Take all brackets except `<` ... `>`
        // Many languages set < ... > as bracket pair, even though they also use it as comparison operator.
        // This leads to problems when colorizing this bracket, so we exclude it if not explicitly configured otherwise.
        // https://github.com/microsoft/vscode/issues/132476
            ?: bracketPairs.filter { pair -> !(pair.open == "<" && pair.close == ">") }

        for (pair in colorizedBracketPairs) {
            val open = pair.open
            val close = pair.close
            val (openingInfo, openingClosing) = getOrCreateOpeningBracket(open)
            val (closingInfo, closingOpening, closingOpeningColorized) = getOrCreateClosingBracket(
                close
            )

            openingClosing.add(closingInfo)
            closingOpeningColorized.add(openingInfo)
            closingOpening.add(openingInfo)
        }

        // Update ArraySets with final values
        openingBracketInfos.forEach { (_, pair) ->
            pair.first.openedBrackets.clear()
            pair.first.openedBrackets.addAll(pair.second)
        }
        closingBracketInfos.forEach { (_, triple) ->
            triple.first.openingBrackets.clear()
            triple.first.openingBrackets.addAll(triple.second)
            triple.first.openingColorizedBrackets.clear()
            triple.first.openingColorizedBrackets.addAll(triple.third)
        }

        _openingBrackets = openingBracketInfos.mapValues { it.value.first }
        _closingBrackets = closingBracketInfos.mapValues { it.value.first }
    }

    /**
     * No two brackets have the same bracket text.
     */
    val openingBrackets: List<OpeningBracketKind>
        get() = _openingBrackets.values.toList()

    /**
     * No two brackets have the same bracket text.
     */
    val closingBrackets: List<ClosingBracketKind>
        get() = _closingBrackets.values.toList()

    fun getOpeningBracketInfo(bracketText: String): OpeningBracketKind? =
        _openingBrackets[bracketText]

    fun getClosingBracketInfo(bracketText: String): ClosingBracketKind? =
        _closingBrackets[bracketText]

    fun getBracketInfo(bracketText: String): BracketKind? =
        getOpeningBracketInfo(bracketText) ?: getClosingBracketInfo(bracketText)

    fun getBracketRegExp(
        flags: Int,
        wholeWord: Boolean = false
    ): Pattern {
        val brackets = _openingBrackets.keys + _closingBrackets.keys
        return createBracketOrRegExp(brackets.toList(), flags, wholeWord)
    }

    private fun filterValidBrackets(brackets: List<CharacterPair>): List<CharacterPair> =
        brackets.filter { pair -> pair.open.isNotEmpty() && pair.close.isNotEmpty() }

    private fun createBracketOrRegExp(
        brackets: List<String>,
        flags: Int,
        wholeWord: Boolean = false
    ): Pattern {
        val escapedBrackets = brackets.map { Pattern.quote(it) }
        val pattern = escapedBrackets.joinToString("|")


        val finalPattern = if (wholeWord) {
            "\\b(?:$pattern)\\b"
        } else {
            pattern
        }

        return Pattern.compile(finalPattern, flags)
    }
}


interface BracketKind {
    val bracketText: String
}

class OpeningBracketKind(
    override val bracketText: String,
    internal val openedBrackets: ArraySet<ClosingBracketKind>
) : BracketKind {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OpeningBracketKind) return false
        return bracketText == other.bracketText
    }

    override fun hashCode(): Int = bracketText.hashCode()

    override fun toString(): String = "OpeningBracketKind(bracketText='$bracketText')"
}

class ClosingBracketKind(
    override val bracketText: String,
    /**
     * Non empty array of all opening brackets this bracket closes.
     */
    internal val openingBrackets: ArraySet<OpeningBracketKind>,
    internal val openingColorizedBrackets: ArraySet<OpeningBracketKind>
) : BracketKind {

    /**
     * Checks if this bracket closes the given other bracket.
     * If the bracket infos come from different configurations, this method will return false.
     */
    fun closes(other: OpeningBracketKind): Boolean =
        openingBrackets.contains(other)

    fun closesColorized(other: OpeningBracketKind): Boolean =
        openingColorizedBrackets.contains(other)

    fun getOpeningBrackets(): List<OpeningBracketKind> =
        openingBrackets.toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClosingBracketKind) return false
        return bracketText == other.bracketText
    }

    override fun hashCode(): Int = bracketText.hashCode()

    override fun toString(): String = "ClosingBracketKind(bracketText='$bracketText')"
}


data class FoundBracket(
    val range: TextRange,
    val bracketInfo: BracketKind
)

data class BracketInfo(
    val range: TextRange,
    /** 0-based level */
    val nestingLevel: Int,
    val nestingLevelOfEqualBracketType: Int,
    val isInvalid: Boolean
)

open class BracketPairInfo(
    val range: TextRange,
    val openingBracketRange: TextRange,
    val closingBracketRange: TextRange?,
    /** 0-based */
    val nestingLevel: Int,
    val nestingLevelOfEqualBracketType: Int,
    private val bracketPairNode: PairAstNode
) {
    val openingBracketInfo: OpeningBracketKind
        get() = bracketPairNode.openingBracket.bracketInfo as OpeningBracketKind

    val closingBracketInfo: ClosingBracketKind?
        get() = bracketPairNode.closingBracket?.bracketInfo as? ClosingBracketKind

    override fun toString(): String {
        return "BracketPairInfo(range=$range, openingBracketRange=$openingBracketRange, closingBracketRange=$closingBracketRange, nestingLevel=$nestingLevel, nestingLevelOfEqualBracketType=$nestingLevelOfEqualBracketType, openingBracketInfo=$openingBracketInfo, closingBracketInfo=$closingBracketInfo)"
    }
}

class BracketPairWithMinIndentationInfo(
    range: TextRange,
    openingBracketRange: TextRange,
    closingBracketRange: TextRange?,
    /**
     * 0-based
     */
    nestingLevel: Int,
    nestingLevelOfEqualBracketType: Int,
    bracketPairNode: PairAstNode,
    /**
     * -1 if not requested, otherwise the size of the minimum indentation in the bracket pair in terms of visible columns.
     */
    val minVisibleColumnIndentation: Int
) : BracketPairInfo(
    range,
    openingBracketRange,
    closingBracketRange,
    nestingLevel,
    nestingLevelOfEqualBracketType,
    bracketPairNode
)