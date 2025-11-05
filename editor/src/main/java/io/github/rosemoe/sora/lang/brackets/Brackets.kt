/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets

import android.util.ArraySet
import io.github.rosemoe.sora.lang.brackets.tree.ast.PairAstNode
import io.github.rosemoe.sora.text.TextRange
import java.util.regex.Pattern


data class RawBracketsConfiguration(
    val brackets: List<Pair<String, String>> = emptyList(),
    val colorizedBracketPairs: List<Pair<String, String>> = emptyList()
)

/**
 * Captures all bracket related configurations for a single language.
 * Immutable.
 */
class BracketsConfiguration(
    config: RawBracketsConfiguration
) {
    private val _openingBrackets: Map<String, OpeningBracketKind>
    private val _closingBrackets: Map<String, ClosingBracketKind>

    init {
        val bracketPairs = filterValidBrackets(config.brackets)

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

        for ((open, close) in bracketPairs) {
            val (openingInfo, openingClosing) = getOrCreateOpeningBracket(open)
            val (closingInfo, closingOpening, _) = getOrCreateClosingBracket(close)

            openingClosing.add(closingInfo)
            closingOpening.add(openingInfo)
        }

        // Treat colorized brackets as brackets, and mark them as colorized.
        val colorizedBracketPairs = config.colorizedBracketPairs?.let { filterValidBrackets(it) }
        // If not configured: Take all brackets except `<` ... `>`
        // Many languages set < ... > as bracket pair, even though they also use it as comparison operator.
        // This leads to problems when colorizing this bracket, so we exclude it if not explicitly configured otherwise.
        // https://github.com/microsoft/vscode/issues/132476
            ?: bracketPairs.filter { (open, close) -> !(open == "<" && close == ">") }

        for ((open, close) in colorizedBracketPairs) {
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

    private fun filterValidBrackets(brackets: List<Pair<String, String>>): List<Pair<String, String>> =
        brackets.filter { (open, close) -> open.isNotEmpty() && close.isNotEmpty() }

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