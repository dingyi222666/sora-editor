/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree

import io.github.rosemoe.sora.lang.brackets.tree.ast.AstNodeKind
import io.github.rosemoe.sora.lang.brackets.tree.ast.BaseAstNode
import io.github.rosemoe.sora.lang.brackets.tree.ast.BracketAstNode
import io.github.rosemoe.sora.lang.brackets.tree.ast.InvalidBracketAstNode
import io.github.rosemoe.sora.lang.brackets.tree.ast.ListAstNode
import io.github.rosemoe.sora.lang.brackets.tree.ast.PairAstNode
import io.github.rosemoe.sora.lang.brackets.tree.ast.TextAstNode
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.OpeningBracketId
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.TokenAllocator
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.TokenKind
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.Tokenizer

/**
 * Non incrementally built ASTs are immutable.
 */
fun parseDocument(
    tokenizer: Tokenizer,
    edits: List<TextEditInfo>,
    oldNode: BaseAstNode?,
    createImmutableLists: Boolean
): BaseAstNode {
    val parser = Parser(tokenizer, edits, oldNode, createImmutableLists)
    return parser.parseDocument()
}

/**
 * Non incrementally built ASTs are immutable.
 */
class Parser(
    private val tokenizer: Tokenizer,
    edits: List<TextEditInfo>,
    oldNode: BaseAstNode?,
    private val createImmutableLists: Boolean
) {
    private val oldNodeReader: NodeReader?
    private val positionMapper: BeforeEditPositionMapper
    private var _itemsConstructed: Int = 0
    private var _itemsFromCache: Int = 0

    /**
     * Reports how many nodes were constructed in the last parse operation.
     */
    val nodesConstructed: Int
        get() = _itemsConstructed

    /**
     * Reports how many nodes were reused in the last parse operation.
     */
    val nodesReused: Int
        get() = _itemsFromCache

    init {
        if (oldNode != null && createImmutableLists) {
            throw IllegalArgumentException("Not supported")
        }

        oldNodeReader = if (oldNode != null) NodeReader(oldNode) else null
        positionMapper = BeforeEditPositionMapper(edits)
    }

    fun parseDocument(): BaseAstNode {
        _itemsConstructed = 0
        _itemsFromCache = 0

        var result = parseList(SmallImmutableSet.getEmpty(), 0)
        if (result == null) {
            result = ListAstNode.getEmpty()
        }

        return result
    }

    private fun parseList(
        openedBracketIds: SmallImmutableSet<OpeningBracketId>,
        level: Int
    ): BaseAstNode? {
        val items = TokenAllocator.obtainNodeList()

        while (true) {
            var child = tryReadChildFromCache(openedBracketIds)

            if (child == null) {
                val token = tokenizer.peek()
                if (
                    token == null ||
                    (token.kind == TokenKind.ClosingBracket &&
                            token.bracketIds.intersects(openedBracketIds))
                ) {
                    break
                }

                child = parseChild(openedBracketIds, level + 1)
            }

            if (child.kind == AstNodeKind.List && child.childrenLength == 0) {
                continue
            }

            items.add(child)
        }

        // When there is no oldNodeReader, all items are created from scratch and must have the same height.
        val result = if (oldNodeReader != null) {
            concat23Trees(items)
        } else {
            concat23TreesOfSameHeight(items, createImmutableLists)
        }
        TokenAllocator.recycleNodeList(items)
        return result
    }

    private fun tryReadChildFromCache(openedBracketIds: SmallImmutableSet<OpeningBracketId>): BaseAstNode? {
        if (oldNodeReader != null) {
            val maxCacheableLength = positionMapper.getDistanceToNextChange(tokenizer.offset)
            if (maxCacheableLength == null || !maxCacheableLength.isZero()) {
                val cachedNode = oldNodeReader.readLongestNodeAt(
                    positionMapper.getOffsetBeforeChange(tokenizer.offset)
                ) { curNode ->
                    // The edit could extend the ending token, thus we cannot re-use nodes that touch the edit.
                    // If there is no edit anymore, we can re-use the node in any case.
                    if (maxCacheableLength != null && curNode.length.value >= maxCacheableLength.value) {
                        // Either the node contains edited text or touches edited text.
                        // In the latter case, brackets might have been extended (`end` -> `ending`), so even touching nodes cannot be reused.
                        return@readLongestNodeAt false
                    }
                    val canBeReused = curNode.canBeReused(openedBracketIds)
                    canBeReused
                }

                if (cachedNode != null) {
                    _itemsFromCache++
                    tokenizer.skip(cachedNode.length)
                    return cachedNode
                }
            }
        }
        return null
    }

    private fun parseChild(
        openedBracketIds: SmallImmutableSet<OpeningBracketId>,
        level: Int
    ): BaseAstNode {
        _itemsConstructed++

        val token = tokenizer.read()!!

        when (token.kind) {
            TokenKind.ClosingBracket -> {
                return InvalidBracketAstNode(token.bracketIds, token.length)
            }

            TokenKind.Text -> {
                val node = token.astNode as TextAstNode
                TokenAllocator.recycle(token)
                return node
            }

            TokenKind.OpeningBracket -> {
                if (level > 300) {
                    // To prevent stack overflows
                    return TextAstNode(token.length)
                }

                val set = openedBracketIds.merge(token.bracketIds)
                val child = parseList(set, level + 1)

                val nextToken = tokenizer.peek()
                if (
                    nextToken != null &&
                    nextToken.kind == TokenKind.ClosingBracket &&
                    (nextToken.bracketId == token.bracketId || nextToken.bracketIds.intersects(token.bracketIds))
                ) {
                    tokenizer.read()
                    return PairAstNode.create(
                        token.astNode as BracketAstNode,
                        child,
                        nextToken.astNode as BracketAstNode
                    )
                } else {
                    return PairAstNode.create(
                        token.astNode as BracketAstNode,
                        child,
                        null
                    )
                }
            }
        }
    }
}
