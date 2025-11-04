/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  Licensed under the MIT License. See License.txt in the project root for license information.
 *--------------------------------------------------------------------------------------------*/

package io.github.rosemoe.sora.lang.brackets.tree.tokenizer

import io.github.rosemoe.sora.lang.brackets.tree.Length
import io.github.rosemoe.sora.lang.brackets.tree.SmallImmutableSet
import io.github.rosemoe.sora.lang.brackets.tree.ast.BaseAstNode

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