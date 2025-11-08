/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree

import io.github.rosemoe.sora.lang.brackets.BracketInfo
import io.github.rosemoe.sora.lang.brackets.BracketPairWithMinIndentationInfo
import io.github.rosemoe.sora.lang.brackets.ClosingBracketKind
import io.github.rosemoe.sora.lang.brackets.FoundBracket
import io.github.rosemoe.sora.lang.brackets.OpeningBracketKind
import io.github.rosemoe.sora.lang.brackets.tree.ast.AstNodeKind
import io.github.rosemoe.sora.lang.brackets.tree.ast.BaseAstNode
import io.github.rosemoe.sora.lang.brackets.tree.ast.BracketAstNode
import io.github.rosemoe.sora.lang.brackets.tree.ast.ListAstNode
import io.github.rosemoe.sora.lang.brackets.tree.ast.PairAstNode
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.BracketTokens
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.FastTokenizer
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.TextBufferTokenizer
import io.github.rosemoe.sora.lang.brackets.tree.utils.CallbackIterable
import io.github.rosemoe.sora.lang.styling.Spans
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextRange
import java.util.concurrent.locks.ReentrantReadWriteLock

fun interface TreeContentSnapshotProvider {
    fun snapshot(): TreeContentSnapshot?
}

data class TreeContentSnapshot(
    val content: ContentReference,
    val spans: Spans?
)

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
    internal var initialAstWithoutTokens: BaseAstNode? = null
    internal var astWithTokens: BaseAstNode? = null

    private var queuedTextEditsForInitialAstWithoutTokens = mutableListOf<TextEditInfo>()
    private var queuedTextEdits = mutableListOf<TextEditInfo>()
    private val lock = ReentrantReadWriteLock()
    private val readLock = lock.readLock()
    private val writeLock = lock.writeLock()
    private var content: ContentReference? = null
    private var expectedVersion: Long = -1L
    @Volatile
    private var dirty: Boolean = true

    init {
        init()
    }

    internal fun init() {
        val snapshot = snapshotProvider.snapshot() ?: return
        val (initialAst, astWithTokensResult) = if (snapshot.spans == null) {
            val tokenizer = FastTokenizer(snapshot.content, brackets)
            val ast = parseDocument(tokenizer, emptyList(), null, true)
            ast to ast
        } else {
            null to parseDocumentFromSnapshot(snapshot, emptyList(), null, false)
        }

        writeLock.lock()
        try {
            initialAstWithoutTokens = initialAst
            astWithTokens = astWithTokensResult
            queuedTextEditsForInitialAstWithoutTokens.clear()
            queuedTextEdits.clear()
            content = snapshot.content
            expectedVersion = snapshot.content.documentVersion
            dirty = false
        } finally {
            writeLock.unlock()
        }
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
        handleEdits(edit)
    }

    private fun handleEdits(edits: TextEditInfo) {
        // Lazily queue the edits and only apply them when the tree is accessed.
        writeLock.lock()
        try {
            val result = combineTextEditInfos(this.queuedTextEdits, listOf(edits))
            queuedTextEdits = result.toMutableList()

            if (initialAstWithoutTokens != null) {
                queuedTextEditsForInitialAstWithoutTokens =
                    combineTextEditInfos(
                        queuedTextEditsForInitialAstWithoutTokens,
                        listOf(edits)
                    ).toMutableList()
            }
            dirty = true
        } finally {
            writeLock.unlock()
        }
    }

    internal fun flushQueue() {
        while (true) {
            val snapshot = snapshotProvider.snapshot() ?: return
            val documentVersion = snapshot.content.documentVersion
            var work: FlushWork? = null
            writeLock.lock()
            try {
                val pendingEdits = queuedTextEdits
                val pendingInitialEdits = queuedTextEditsForInitialAstWithoutTokens
                val previousAst = astWithTokens
                val previousInitial = initialAstWithoutTokens
                val shouldParseAst =
                    previousAst == null || dirty || pendingEdits.isNotEmpty() || expectedVersion != documentVersion
                val shouldParseInitial =
                    previousInitial != null && (dirty || pendingInitialEdits.isNotEmpty() || expectedVersion != documentVersion)
                if (!shouldParseAst && !shouldParseInitial) {
                    return
                }
                queuedTextEdits = mutableListOf()
                queuedTextEditsForInitialAstWithoutTokens = mutableListOf()
                val edits = if (shouldParseAst) pendingEdits else emptyList()
                val initialEdits = if (shouldParseInitial) pendingInitialEdits else emptyList()
                work = FlushWork(
                    edits = edits,
                    initialEdits = initialEdits,
                    previousAst = previousAst,
                    previousInitialAst = previousInitial,
                    parseAstWithTokens = shouldParseAst,
                    parseInitialAst = shouldParseInitial
                )
            } finally {
                writeLock.unlock()
            }
            val actualWork = work

            val updatedAst = if (actualWork.parseAstWithTokens) {
                parseDocumentFromSnapshot(snapshot, actualWork.edits, actualWork.previousAst, false)
            } else {
                actualWork.previousAst
            }
            val updatedInitialAst = if (actualWork.parseInitialAst) {
                parseDocumentFromSnapshot(snapshot, actualWork.initialEdits, actualWork.previousInitialAst, false)
            } else {
                actualWork.previousInitialAst
            }

            val hasRemainingWork: Boolean
            writeLock.lock()
            try {
                if (actualWork.parseAstWithTokens) {
                    astWithTokens = updatedAst
                }
                if (actualWork.parseInitialAst) {
                    initialAstWithoutTokens = updatedInitialAst
                    if (updatedInitialAst == null) {
                        queuedTextEditsForInitialAstWithoutTokens.clear()
                    }
                }
                content = snapshot.content
                expectedVersion = documentVersion
                val hasQueued =
                    queuedTextEdits.isNotEmpty() || queuedTextEditsForInitialAstWithoutTokens.isNotEmpty()
                dirty = hasQueued
                hasRemainingWork = hasQueued
            } finally {
                writeLock.unlock()
            }

            if (!hasRemainingWork) {
                return
            }
        }
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

    private data class FlushWork(
        val edits: List<TextEditInfo>,
        val initialEdits: List<TextEditInfo>,
        val previousAst: BaseAstNode?,
        val previousInitialAst: BaseAstNode?,
        val parseAstWithTokens: Boolean,
        val parseInitialAst: Boolean
    )

    fun getBracketsInRange(
        range: TextRange,
        onlyColorizedBrackets: Boolean
    ): CallbackIterable<BracketInfo> {
        val startOffset = range.start.toLength()
        val endOffset = range.end.toLength()
        return CallbackIterable({ cb ->
            readLock.lock()
            try {
                val node = initialAstWithoutTokens ?: this.astWithTokens ?: return@CallbackIterable
                val currentContent = content
                if (currentContent != null && !currentContent.hasVersion(expectedVersion)) {
                    markVersionMismatch()
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
            } finally {
                readLock.unlock()
            }
        })
    }

    fun getBracketPairsInRange(
        range: TextRange,
        includeMinIndentation: Boolean
    ): CallbackIterable<BracketPairWithMinIndentationInfo> {
        val startOffset = range.start.toLength()
        val endOffset = range.end.toLength()

        return CallbackIterable { cb ->
            readLock.lock()
            try {
                val node = initialAstWithoutTokens ?: this.astWithTokens ?: return@CallbackIterable
                val currentContent = content
                if (currentContent != null && !currentContent.hasVersion(expectedVersion)) {
                    markVersionMismatch()
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
            } finally {
                readLock.unlock()
            }
        }
    }

    fun getFirstBracketAfter(position: CharPosition): FoundBracket? {
        readLock.lock()
        return try {
            val node = this.initialAstWithoutTokens ?: this.astWithTokens ?: return null
            val currentContent = content
            if (currentContent != null && !currentContent.hasVersion(expectedVersion)) {
                markVersionMismatch()
                return null
            }
            getFirstBracketAfter(
                node,
                Length.ZERO,
                node.length,
                position.toLength()
            )
        } finally {
            readLock.unlock()
        }
    }

    fun getFirstBracketBefore(position: CharPosition): FoundBracket? {

        readLock.lock()
        return try {
            val node = this.initialAstWithoutTokens ?: this.astWithTokens ?: return null
            val currentContent = content
            if (currentContent != null && !currentContent.hasVersion(expectedVersion)) {
                markVersionMismatch()
                return null
            }
            getFirstBracketBefore(
                node,
                Length.ZERO,
                node.length,
                position.toLength()
            )
        } finally {
            readLock.unlock()
        }
    }

    private fun ContentReference.hasVersion(expectedVersion: Long): Boolean {
        if (expectedVersion < 0) {
            return true
        }
        return documentVersion == expectedVersion
    }

    private fun markVersionMismatch() {
        dirty = true
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
        is ListAstNode, PairAstNode.Companion -> {
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
    val textModel: ContentReference?
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

