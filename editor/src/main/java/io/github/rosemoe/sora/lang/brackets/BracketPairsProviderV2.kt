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

import io.github.rosemoe.sora.lang.brackets.tree.utils.CallbackIterable
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.util.IntPair


interface BracketPairsProviderV2 {

    /**
     * Gets all bracket pairs that intersect the given position.
     * The result is sorted by the start position.
     */
    fun getBracketPairsInRange(range: TextRange): CallbackIterable<BracketPairInfo>

    /**
     * Gets all bracket pairs that intersect the given position.
     * The result is sorted by the start position.
     */
    fun getBracketPairsInRangeWithMinIndentation(range: TextRange): CallbackIterable<BracketPairWithMinIndentationInfo>

    fun getBracketsInRange(
        range: TextRange,
        onlyColorizedBrackets: Boolean = false
    ): CallbackIterable<BracketInfo>

    /**
     * Find the matching bracket of `request` up, counting brackets.
     * @param bracket The bracket we're searching for
     * @param position The position at which to start the search.
     * @param maxDuration Maximum duration in milliseconds
     * @return The range of the matching bracket, or null if the bracket match was not found.
     */
    fun findMatchingBracketUp(
        bracket: String,
        position: CharPosition,
        maxDuration: Int? = null
    ): IntPair?

    /**
     * Find the first bracket in the model before `position`.
     * @param position The position at which to start the search.
     * @return The info for the first bracket before `position`, or null if there are no more brackets before `positions`.
     */
    fun findPrevBracket(position: CharPosition): FoundBracket?

    /**
     * Find the first bracket in the model after `position`.
     * @param position The position at which to start the search.
     * @return The info for the first bracket after `position`, or null if there are no more brackets after `positions`.
     */
    fun findNextBracket(position: CharPosition): FoundBracket?

    /**
     * Find the enclosing brackets that contain `position`.
     * @param position The position at which to start the search.
     * @param maxDuration Maximum duration in milliseconds
     */
    fun findEnclosingBrackets(
        position: CharPosition,
        maxDuration: Int? = null
    ): Pair<IntPair, IntPair>?

    /**
     * Given a `position`, if the position is on top or near a bracket,
     * find the matching bracket of that bracket and return the ranges of both brackets.
     * @param position The position at which to look for a bracket.
     * @param maxDuration Maximum duration in milliseconds
     */
    fun matchBracket(position: CharPosition, maxDuration: Int? = null): Pair<IntPair, IntPair>?
}