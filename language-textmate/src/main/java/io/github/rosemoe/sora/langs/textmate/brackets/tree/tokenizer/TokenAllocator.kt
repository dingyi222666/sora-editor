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
import io.github.rosemoe.sora.langs.textmate.brackets.tree.ast.TextAstNode

/**
 * Simple object pool for text tokens and their backing [TextAstNode]s.
 * Tokens are immutable, so pooling them by [Length] allows the tokenizer to
 * reuse instances safely across incremental parses.
 */
internal object TokenAllocator {

    private const val MAX_TOTAL_TEXT_TOKENS = 4096
    private const val MAX_TOKENS_PER_LENGTH = 16

    private val lock = Any()
    private val textTokenPools = HashMap<Long, ArrayDeque<Token>>()
    private val textNodePools = HashMap<Long, ArrayDeque<TextAstNode>>()
    private var pooledTokenCount = 0
    private var pooledTextNodeCount = 0
    private val nodeListPool = ArrayDeque<ArrayList<BaseAstNode>>()
    private var pooledNodeListCount = 0
    private val emptyBracketIds = SmallImmutableSet.getEmpty<OpeningBracketId>()

    fun obtainTextToken(length: Length): Token {
        val key = length.value
        synchronized(lock) {
            val pool = textTokenPools[key]
            if (pool != null) {
                val pooled = pool.removeLastOrNull()
                if (pooled != null) {
                    pooledTokenCount--
                    return pooled
                }
                if (pool.isEmpty()) {
                    textTokenPools.remove(key)
                }
            }
        }
        val node = obtainTextAstNode(length)
        return Token(
            length,
            TokenKind.Text,
            -1,
            emptyBracketIds,
            node
        )
    }

    private fun obtainTextAstNode(length: Length): TextAstNode {
        val key = length.value
        synchronized(lock) {
            val pool = textNodePools[key]
            if (pool != null) {
                val node = pool.removeLastOrNull()
                if (node != null) {
                    pooledTextNodeCount--
                    if (pool.isEmpty()) {
                        textNodePools.remove(key)
                    }
                    return node
                }
                if (pool.isEmpty()) {
                    textNodePools.remove(key)
                }
            }
        }
        return TextAstNode(length)
    }

    fun recycle(token: Token?) {
        if (token == null) {
            return
        }
        if (token.kind != TokenKind.Text || token.bracketId != -1) {
            return
        }
        val textNode = token.astNode as? TextAstNode ?: return
        val key = token.length.value
        synchronized(lock) {
            if (pooledTokenCount < MAX_TOTAL_TEXT_TOKENS) {
                val pool = textTokenPools.getOrPut(key) { ArrayDeque() }
                if (pool.size < MAX_TOKENS_PER_LENGTH) {
                    pool.addLast(token)
                    pooledTokenCount++
                }
            }
            if (pooledTextNodeCount < MAX_TOTAL_TEXT_TOKENS) {
                val nodePool = textNodePools.getOrPut(key) { ArrayDeque() }
                if (nodePool.size < MAX_TOKENS_PER_LENGTH) {
                    nodePool.addLast(textNode)
                    pooledTextNodeCount++
                }
            }
        }
    }

    fun recycle(tokens: Iterable<Token?>) {
        tokens.forEach { recycle(it) }
    }

    fun obtainNodeList(): ArrayList<BaseAstNode> {
        synchronized(lock) {
            val pooled = nodeListPool.removeLastOrNull()
            if (pooled != null) {
                pooledNodeListCount--
                pooled.clear()
                return pooled
            }
        }
        return ArrayList(8)
    }

    fun recycleNodeList(list: MutableList<BaseAstNode>) {
        val arrayList = list as? ArrayList<BaseAstNode> ?: return
        synchronized(lock) {
            if (arrayList.size > MAX_NODE_LIST_SIZE || pooledNodeListCount >= MAX_NODE_LIST_POOL) {
                arrayList.clear()
                return
            }
            arrayList.clear()
            nodeListPool.addLast(arrayList)
            pooledNodeListCount++
        }
    }

    private const val MAX_NODE_LIST_POOL = 1024
    private const val MAX_NODE_LIST_SIZE = 1024
}
