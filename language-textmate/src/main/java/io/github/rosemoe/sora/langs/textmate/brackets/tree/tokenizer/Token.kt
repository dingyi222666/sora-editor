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
import io.github.rosemoe.sora.langs.textmate.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.langs.textmate.brackets.tree.ast.BaseAstNode

enum class TokenKind(val value: Int) {
    Text(0),
    OpeningBracket(1),
    ClosingBracket(2)
}


class Token(
    val length: Length,
    val kind: TokenKind,
    /**
     * If this token is an opening bracket, this is the id of the opening bracket.
     * If this token is a closing bracket, this is the id of the first opening bracket that is closed by this bracket.
     * Otherwise, it is -1.
     */
    val bracketId: OpeningBracketId,
    /**
     * If this token is an opening bracket, this just contains `bracketId`.
     * If this token is a closing bracket, this lists all opening bracket ids, that it closes.
     * Otherwise, it is empty.
     */
    val bracketIds: SmallImmutableSet<OpeningBracketId>,
    val astNode: BaseAstNode?
)

typealias OpeningBracketId = Int