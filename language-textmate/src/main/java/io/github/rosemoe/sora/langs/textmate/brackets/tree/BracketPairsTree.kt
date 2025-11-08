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

package io.github.rosemoe.sora.langs.textmate.brackets.tree

import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.langs.textmate.brackets.BracketInfo
import io.github.rosemoe.sora.langs.textmate.brackets.BracketPairWithMinIndentationInfo
import io.github.rosemoe.sora.langs.textmate.brackets.ClosingBracketKind
import io.github.rosemoe.sora.langs.textmate.brackets.FoundBracket
import io.github.rosemoe.sora.langs.textmate.brackets.OpeningBracketKind
import io.github.rosemoe.sora.langs.textmate.brackets.tree.ast.AstNodeKind
import io.github.rosemoe.sora.langs.textmate.brackets.tree.ast.BaseAstNode
import io.github.rosemoe.sora.langs.textmate.brackets.tree.ast.BracketAstNode
import io.github.rosemoe.sora.langs.textmate.brackets.tree.ast.ListAstNode
import io.github.rosemoe.sora.langs.textmate.brackets.tree.ast.PairAstNode
import io.github.rosemoe.sora.langs.textmate.brackets.tree.tokenizer.BracketTokens
import io.github.rosemoe.sora.langs.textmate.brackets.tree.tokenizer.FastTokenizer
import io.github.rosemoe.sora.langs.textmate.brackets.tree.tokenizer.TextBufferTokenizer
import io.github.rosemoe.sora.langs.textmate.brackets.tree.utils.CallbackIterable
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextRange
import java.util.concurrent.atomic.AtomicReference

fun interface TreeContentSnapshotProvider {
    fun snapshot(): TreeContentSnapshot?
}

data class TreeContentSnapshot(
    val content: Content,
    val spans: Spans?
)

private data class TreeState(
    val initialAstWithoutTokens: BaseAstNode? = null,
    val astWithTokens: BaseAstNode? = null,
    val queuedTextEdits: List<TextEditInfo> = emptyList(),
    val queuedInitialTextEdits: List<TextEditInfo> = emptyList(),
    val content: Content? = null,
    val expectedVersion: Long = -1L,
) {
    val activeAst: BaseAstNode?
        get() = initialAstWithoutTokens ?: astWithTokens
}

class BracketPairsTree(
    private val snapshotProvider: TreeContentSnapshotProvider,
    private val brackets: BracketTokens
) {
    /*
		There are two trees:
		* The initial tree that has no token information and is used for performant initial bracket colorization.
		* The tree that used token information to detect bracket pairs.

		To prevent flickering, we only switch from the initial tree to tree with token information
		when tokenization completes.
		Since the text can be edited while background tokenization is in progress, we need to update both trees.
	*/
    private val state = AtomicReference(TreeState())

    internal fun init() {
        val snapshot = snapshotProvider.snapshot() ?: return
        val (initialAst, astWithTokensResult) = if (snapshot.spans == null) {
            val tokenizer = FastTokenizer(snapshot.content, brackets)
            val ast = parseDocument(tokenizer, emptyList(), null, true)
            ast to ast
        } else {
            null to parseDocumentFromSnapshot(snapshot, emptyList(), null, false)
        }
        state.set(
            TreeState(
                initialAstWithoutTokens = initialAst,
                astWithTokens = astWithTokensResult,
                queuedTextEdits = emptyList(),
                queuedInitialTextEdits = emptyList(),
                content = snapshot.content,
                expectedVersion = snapshot.content.documentVersion
            )
        )
    }


    fun handleContentChanged(
        changeStart: CharPosition,
        changeEnd: CharPosition,
        changeText: CharSequence?
    ) {
        val startOffset = changeStart.toLength()
        val endOffset = changeEnd.toLength()
        val newLength = changeText?.let { lengthOfString(it) } ?: Length.ZERO
        val edit = TextEditInfo(startOffset, endOffset, newLength)
        handleEdits(listOf(edit))
    }

    private fun handleEdits(edits: List<TextEditInfo>) {
        if (edits.isEmpty()) {
            return
        }
        updateState { current ->
            val newQueued = combineTextEditInfos(current.queuedTextEdits, edits)
            val newInitialQueue = if (current.initialAstWithoutTokens == null) {
                emptyList()
            } else {
                combineTextEditInfos(current.queuedInitialTextEdits, edits)
            }
            current.copy(
                queuedTextEdits = newQueued,
                queuedInitialTextEdits = newInitialQueue
            )
        }
    }

    internal fun flushQueue(isTokenChange: Boolean) {
        val current = state.get()
        val snapshot = snapshotProvider.snapshot() ?: return

        val documentVersion = snapshot.content.documentVersion
        val shouldParseAstBase =
            ((snapshot.spans != null && current.astWithTokens == null) ||
                    current.queuedTextEdits.isNotEmpty())

        val shouldParseInitialBase = current.initialAstWithoutTokens != null &&
                (current.queuedInitialTextEdits.isNotEmpty())

        val shouldParseAst = shouldParseAstBase && isTokenChange
        val shouldParseInitial = shouldParseInitialBase && !isTokenChange

        println("$shouldParseInitial $shouldParseAst ${current.queuedInitialTextEdits} ${current.queuedTextEdits}")

        if (!shouldParseAst && !shouldParseInitial) {
            return
        }

        val updatedAst = if (shouldParseAst) {
            parseDocumentFromSnapshot(
                snapshot,
                current.queuedTextEdits,
                current.astWithTokens,
                false
            )
        } else {
            current.astWithTokens
        }
        val updatedInitialAst = if (shouldParseInitial) {
            parseDocumentFromSnapshot(
                snapshot,
                current.queuedInitialTextEdits,
                current.initialAstWithoutTokens,
                false
            )
        } else {
            current.initialAstWithoutTokens
        }

        val remainingQueued = if (shouldParseAst) emptyList() else current.queuedTextEdits
        val remainingInitialQueued =
            if (shouldParseInitial) emptyList() else current.queuedInitialTextEdits
        val nextState = current.copy(
            initialAstWithoutTokens = if (shouldParseInitial) {
                updatedInitialAst
            } else {
                /*
                 * When only the token-aware tree is refreshed we still need a fallback AST for
                 * range queries before the initial tree (which ignores tokens) runs again. Reusing
                 * the freshly parsed token tree avoids serving stale bracket data.
                 */
                /*updatedAst ?: */current.initialAstWithoutTokens
            },
            astWithTokens = if (shouldParseAst) updatedAst else current.astWithTokens,
            queuedTextEdits = remainingQueued,
            queuedInitialTextEdits = remainingInitialQueued,
            content = snapshot.content,
            expectedVersion = documentVersion
        )

        state.compareAndSet(current, nextState)

    }

    /**
     * @pure (only if isPure = true)
     */
    private fun parseDocumentFromSnapshot(
        snapshot: TreeContentSnapshot,
        edits: List<TextEditInfo>,
        previousAst: BaseAstNode?,
        immutable: Boolean
    ): BaseAstNode {
        // Is much faster if `isPure = false`.
        val isPure = false
        val previousAstClone = if (isPure) previousAst?.deepClone() else previousAst
        val tokenizer = snapshot.spans?.let {
            TextBufferTokenizer(
                snapshot.content,
                it,
                brackets
            )
        } ?: FastTokenizer(snapshot.content, brackets)

        return parseDocument(tokenizer, edits, previousAstClone, immutable)
    }

    fun getBracketsInRange(
        range: TextRange,
        onlyColorizedBrackets: Boolean
    ): CallbackIterable<BracketInfo> {
        val startOffset = range.start.toLength()
        val endOffset = range.end.toLength()
        return CallbackIterable { cb ->
            val current = state.get()
            val node = current.activeAst ?: return@CallbackIterable
            val currentContent = current.content
            if (currentContent != null && !currentContent.hasVersion(current.expectedVersion)) {
                return@CallbackIterable
            }
            collectBrackets(
                node,
                Length.ZERO,
                node.length,
                startOffset,
                endOffset,
                cb,
                0,
                0,
                mutableMapOf(),
                onlyColorizedBrackets
            )
        }
    }

    fun getBracketPairsInRange(
        range: TextRange,
        includeMinIndentation: Boolean
    ): CallbackIterable<BracketPairWithMinIndentationInfo> {
        val startOffset = range.start.toLength()
        val endOffset = range.end.toLength()

        return CallbackIterable { cb ->
            val current = state.get()
            val node = current.activeAst ?: return@CallbackIterable
            val currentContent = current.content
            if (currentContent != null && !currentContent.hasVersion(current.expectedVersion)) {
                return@CallbackIterable
            }
            val context = CollectBracketPairsContext(
                cb,
                includeMinIndentation,
                if (includeMinIndentation) currentContent else null
            )
            collectBracketPairs(
                node,
                Length.ZERO,
                node.length,
                startOffset,
                endOffset,
                context,
                0,
                mutableMapOf()
            )
        }
    }

    fun getFirstBracketAfter(position: CharPosition): FoundBracket? {
        val current = state.get()
        val node = current.activeAst ?: return null
        val currentContent = current.content
        if (currentContent != null && !currentContent.hasVersion(current.expectedVersion)) {
            return null
        }
        return getFirstBracketAfter(
            node,
            Length.ZERO,
            node.length,
            position.toLength()
        )
    }

    fun getFirstBracketBefore(position: CharPosition): FoundBracket? {
        val current = state.get()
        val node = current.activeAst ?: return null
        val currentContent = current.content
        if (currentContent != null && !currentContent.hasVersion(current.expectedVersion)) {
            return null
        }
        return getFirstBracketBefore(
            node,
            Length.ZERO,
            node.length,
            position.toLength()
        )
    }

    private inline fun updateState(transform: (TreeState) -> TreeState?): Unit {
        while (true) {
            val current = state.get()
            val next = transform(current) ?: return
            if (state.compareAndSet(current, next)) {
                return
            }
        }
    }

    private fun Content.hasVersion(expectedVersion: Long): Boolean {
        if (expectedVersion < 0) {
            return true
        }
        return documentVersion == expectedVersion
    }
}

/**
 * Gets the first bracket before the given position.
 */
private fun getFirstBracketBefore(
    node: BaseAstNode,
    nodeOffsetStart: Length,
    nodeOffsetEnd: Length,
    position: Length
): FoundBracket? {
    var currentOffsetStart = nodeOffsetStart
    var currentOffsetEnd = nodeOffsetEnd

    return when (node) {
        is ListAstNode, PairAstNode -> {
            val lengths = mutableListOf<Pair<Length, Length>>()
            for (child in node.children) {
                currentOffsetEnd = currentOffsetStart + child.length
                lengths.add(currentOffsetStart to currentOffsetEnd)
                currentOffsetStart = currentOffsetEnd
            }

            for (i in lengths.size - 1 downTo 0) {
                val (childOffsetStart, childOffsetEnd) = lengths[i]
                if (childOffsetStart.value < position.value) {
                    val result = getFirstBracketBefore(
                        node.children[i],
                        childOffsetStart,
                        childOffsetEnd,
                        position
                    )
                    if (result != null) {
                        return result
                    }
                }
            }
            null
        }

        is BracketAstNode -> {
            val range = nodeOffsetStart.toRange(nodeOffsetEnd)
            FoundBracket(
                range = range,
                bracketInfo = node.bracketInfo
            )
        }

        else -> null
    }
}

/**
 * Gets the first bracket after the given position.
 */
private fun getFirstBracketAfter(
    node: BaseAstNode,
    nodeOffsetStart: Length,
    nodeOffsetEnd: Length,
    position: Length
): FoundBracket? {
    var currentOffsetStart = nodeOffsetStart
    var currentOffsetEnd = nodeOffsetEnd

    when (node.kind) {
        AstNodeKind.List, AstNodeKind.Pair -> {
            for (child in node.children) {
                currentOffsetEnd = currentOffsetStart + child.length
                if (position.value < currentOffsetEnd.value) {
                    val result = getFirstBracketAfter(
                        child,
                        currentOffsetStart,
                        currentOffsetEnd,
                        position
                    )
                    if (result != null) {
                        return result
                    }
                }
                currentOffsetStart = currentOffsetEnd
            }
            return null
        }

        AstNodeKind.UnexpectedClosingBracket -> {
            return null
        }

        AstNodeKind.Bracket -> {
            val range = nodeOffsetStart.toRange(nodeOffsetEnd)
            return FoundBracket(
                range = range,
                bracketInfo = (node as BracketAstNode).bracketInfo
            )
        }

        else -> return null
    }
}

/**
 * Collects all brackets in the given range.
 * @param node The AST node to collect brackets from
 * @param nodeOffsetStart The start offset of the node
 * @param nodeOffsetEnd The end offset of the node
 * @param startOffset The start offset of the range to collect brackets from
 * @param endOffset The end offset of the range to collect brackets from
 * @param push A callback that is called for each bracket found. Returns false to stop collecting.
 * @param level The current nesting level
 * @param nestingLevelOfEqualBracketType The nesting level of brackets of the same type
 * @param levelPerBracketType A map that tracks the nesting level per bracket type
 * @param onlyColorizedBrackets Whether to only collect colorized brackets
 * @param parentPairIsIncomplete Whether the parent pair is incomplete (unclosed)
 * @return true to continue collecting, false to stop
 */
private fun collectBrackets(
    node: BaseAstNode,
    nodeOffsetStart: Length,
    nodeOffsetEnd: Length,
    startOffset: Length,
    endOffset: Length,
    push: (BracketInfo) -> Boolean,
    level: Int,
    nestingLevelOfEqualBracketType: Int,
    levelPerBracketType: MutableMap<String, Int>,
    onlyColorizedBrackets: Boolean,
    parentPairIsIncomplete: Boolean = false
): Boolean {
    if (level > 200) {
        return true
    }

    var currentNode = node
    var currentOffsetStart = nodeOffsetStart
    var currentOffsetEnd = nodeOffsetEnd
    var currentLevel = level
    var currentNestingLevel = nestingLevelOfEqualBracketType

    whileLoop@ while (true) {
        when (currentNode.kind) {
            AstNodeKind.List -> {
                val childCount = currentNode.childrenLength
                for (i in 0 until childCount) {
                    val child = currentNode.getChild(i) ?: continue
                    currentOffsetEnd = currentOffsetStart + child.length

                    if (currentOffsetStart.value <= endOffset.value &&
                        currentOffsetEnd.value >= startOffset.value
                    ) {
                        val childEndsAfterEnd = currentOffsetEnd.value >= endOffset.value
                        if (childEndsAfterEnd) {
                            // No child after this child in the requested window, don't recurse
                            currentNode = child
                            continue@whileLoop
                        }

                        val shouldContinue = collectBrackets(
                            child,
                            currentOffsetStart,
                            currentOffsetEnd,
                            startOffset,
                            endOffset,
                            push,
                            currentLevel,
                            0,
                            levelPerBracketType,
                            onlyColorizedBrackets
                        )
                        if (!shouldContinue) {
                            return false
                        }
                    }
                    currentOffsetStart = currentOffsetEnd
                }
                return true
            }

            AstNodeKind.Pair -> {
                val pairNode = currentNode as PairAstNode
                val colorize = !onlyColorizedBrackets ||
                        pairNode.closingBracket == null ||
                        (pairNode.closingBracket.bracketInfo as? ClosingBracketKind)?.closesColorized(
                            pairNode.openingBracket.bracketInfo as OpeningBracketKind
                        ) == true

                var levelPerBracket = 0
                run {
                    val existing = levelPerBracketType[pairNode.openingBracket.text] ?: 0
                    levelPerBracket = existing
                    if (colorize) {
                        levelPerBracketType[pairNode.openingBracket.text] = existing + 1
                    }
                }

                val childCount = currentNode.childrenLength
                for (i in 0 until childCount) {
                    val child = currentNode.getChild(i) ?: continue
                    currentOffsetEnd = currentOffsetStart + child.length

                    if (currentOffsetStart.value <= endOffset.value &&
                        currentOffsetEnd.value >= startOffset.value
                    ) {
                        val childEndsAfterEnd = currentOffsetEnd.value >= endOffset.value
                        if (childEndsAfterEnd && child.kind != AstNodeKind.Bracket) {
                            // No child after this child in the requested window, don't recurse
                            // Don't do this for brackets because of unclosed/unopened brackets
                            currentNode = child
                            if (colorize) {
                                currentLevel++
                                currentNestingLevel = levelPerBracket + 1
                            } else {
                                currentNestingLevel = levelPerBracket
                            }
                            continue@whileLoop
                        }

                        if (colorize || child.kind != AstNodeKind.Bracket) {
                            val shouldContinue = collectBrackets(
                                child,
                                currentOffsetStart,
                                currentOffsetEnd,
                                startOffset,
                                endOffset,
                                push,
                                if (colorize) currentLevel + 1 else currentLevel,
                                if (colorize) levelPerBracket + 1 else levelPerBracket,
                                levelPerBracketType,
                                onlyColorizedBrackets,
                                pairNode.closingBracket == null
                            )
                            if (!shouldContinue) {
                                return false
                            }
                        }
                    }
                    currentOffsetStart = currentOffsetEnd
                }

                levelPerBracketType[pairNode.openingBracket.text] = levelPerBracket
                return true
            }

            AstNodeKind.UnexpectedClosingBracket -> {
                val range = currentOffsetStart.toRange(currentOffsetEnd)
                return push(BracketInfo(range, currentLevel - 1, 0, true))
            }

            AstNodeKind.Bracket -> {
                val range = currentOffsetStart.toRange(currentOffsetEnd)
                return push(
                    BracketInfo(
                        range,
                        currentLevel - 1,
                        currentNestingLevel - 1,
                        parentPairIsIncomplete
                    )
                )
            }

            AstNodeKind.Text -> {
                return true
            }
        }
    }
}

/**
 * Context for collecting bracket pairs.
 */
private class CollectBracketPairsContext(
    val push: (BracketPairWithMinIndentationInfo) -> Boolean,
    val includeMinIndentation: Boolean,
    val textModel: Content?
)

/**
 * Collects all bracket pairs in the given range.
 * @param node The AST node to collect bracket pairs from
 * @param nodeOffsetStart The start offset of the node
 * @param nodeOffsetEnd The end offset of the node
 * @param startOffset The start offset of the range to collect bracket pairs from
 * @param endOffset The end offset of the range to collect bracket pairs from
 * @param context The context for collecting bracket pairs
 * @param level The current nesting level
 * @param levelPerBracketType A map that tracks the nesting level per bracket type
 * @return true to continue collecting, false to stop
 */
private fun collectBracketPairs(
    node: BaseAstNode,
    nodeOffsetStart: Length,
    nodeOffsetEnd: Length,
    startOffset: Length,
    endOffset: Length,
    context: CollectBracketPairsContext,
    level: Int,
    levelPerBracketType: MutableMap<String, Int>
): Boolean {
    if (level > 200) {
        return true
    }

    var shouldContinue = true

    if (node.kind == AstNodeKind.Pair) {
        val pairNode = node as PairAstNode
        var levelPerBracket = 0
        val existing = levelPerBracketType[pairNode.openingBracket.text] ?: 0
        levelPerBracket = existing
        levelPerBracketType[pairNode.openingBracket.text] = existing + 1

        val openingBracketEnd = nodeOffsetStart + pairNode.openingBracket.length

        // println("" + nodeOffsetStart.charPosition + " " + pairNode.openingBracket.length.charPosition)
        var minIndentation = -1
        if (context.includeMinIndentation) {
            val textModel = context.textModel
            if (textModel != null) {
                minIndentation = pairNode.computeMinIndentation(
                    nodeOffsetStart,
                    textModel
                )
            }
        }


        /*println(
            " " + pairNode.closingBracket + " " + openingBracketEnd.charPosition + " " + pairNode.child?.length?.charPosition + " "
                    + ((openingBracketEnd + (pairNode.child?.length
                ?: Length.ZERO)).charPosition)
        )*/

        shouldContinue = context.push(
            BracketPairWithMinIndentationInfo(
                nodeOffsetStart.toRange(nodeOffsetEnd),
                nodeOffsetStart.toRange(openingBracketEnd),
                if (pairNode.closingBracket != null)
                    (openingBracketEnd + (pairNode.child?.length ?: Length.ZERO)).toRange(
                        nodeOffsetEnd
                    )
                else null,
                level,
                levelPerBracket,
                pairNode,
                minIndentation
            )
        )

        if (shouldContinue && pairNode.child != null) {
            val child = pairNode.child
            val currentOffsetEnd = openingBracketEnd + child.length
            if (openingBracketEnd.value <= endOffset.value &&
                currentOffsetEnd.value >= startOffset.value
            ) {
                shouldContinue = collectBracketPairs(
                    child,
                    openingBracketEnd,
                    currentOffsetEnd,
                    startOffset,
                    endOffset,
                    context,
                    level + 1,
                    levelPerBracketType
                )
                if (!shouldContinue) {
                    return false
                }
            }
        }

        levelPerBracketType[pairNode.openingBracket.text] = levelPerBracket
    } else {
        var curOffset = nodeOffsetStart
        for (child in node.children) {
            val childOffset = curOffset
            curOffset += child.length

            if (childOffset.value <= endOffset.value &&
                startOffset.value <= curOffset.value
            ) {
                shouldContinue = collectBracketPairs(
                    child,
                    childOffset,
                    curOffset,
                    startOffset,
                    endOffset,
                    context,
                    level,
                    levelPerBracketType
                )
                if (!shouldContinue) {
                    return false
                }
            }
        }
    }
    return shouldContinue
}

