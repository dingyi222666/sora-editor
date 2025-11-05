/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree.ast

import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.analysis.StyleUpdateRange
import io.github.rosemoe.sora.lang.brackets.BracketInfo
import io.github.rosemoe.sora.lang.brackets.BracketPairWithMinIndentationInfo
import io.github.rosemoe.sora.lang.brackets.ClosingBracketKind
import io.github.rosemoe.sora.lang.brackets.FoundBracket
import io.github.rosemoe.sora.lang.brackets.OpeningBracketKind
import io.github.rosemoe.sora.lang.brackets.tree.Length
import io.github.rosemoe.sora.lang.brackets.tree.TextEditInfo
import io.github.rosemoe.sora.lang.brackets.tree.combineTextEditInfos
import io.github.rosemoe.sora.lang.brackets.tree.parseDocument
import io.github.rosemoe.sora.lang.brackets.tree.toLength
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.BracketTokens
import io.github.rosemoe.sora.lang.brackets.tree.tokenizer.FastTokenizer
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.widget.CodeEditor
import java.lang.ref.WeakReference

class BracketPairsTree(
    private val editor: WeakReference<CodeEditor>,
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
    private var initialAstWithoutTokens: BaseAstNode? = null
    private var astWithTokens: BaseAstNode? = null


    private var queuedTextEditsForInitialAstWithoutTokens = mutableListOf<TextEditInfo>();
    private var queuedTextEdits = mutableListOf<TextEditInfo>();

    init {
        init()
    }

    private fun init() {
        val editorValue = editor.get() ?: return
        if (editorValue.styles == null) {

            val tokenizer = FastTokenizer(ContentReference(editorValue.text), brackets);
            initialAstWithoutTokens = parseDocument(tokenizer, emptyList(), null, true);
            astWithTokens = initialAstWithoutTokens;
        } else if (editorValue.styles != null) {
            // Skip the initial ast, as there is no flickering.
            // Directly create the tree with token information.
            initialAstWithoutTokens = null;
            astWithTokens = parseDocumentFromTextBuffer(emptyList(), null, false);
        } else {
            // We missed some token changes already, so we cannot use the fast tokenizer + delta increments
            initialAstWithoutTokens =
                parseDocumentFromTextBuffer(emptyList(), null, true);
            astWithTokens = initialAstWithoutTokens;
        }
    }

    fun handleDidChangeTokens(event: StyleUpdateRange) {
        val range = event.toRange()
        val edit = TextEditInfo(
            toLength(range.start.line, range.start.column),
            toLength(range.end.line, range.end.column),
            toLength(range.end.line - range.start.line + 1, 0)
        )

        handleEdits(edit, true);

        /*if (!this.initialAstWithoutTokens) {
            this.didChangeEmitter.fire();
        }*/
    }

    fun handleContentChanged(change: ContentChangeEvent) {
        val edit = TextEditInfo(
            toLength(change.changeStart.line, change.changeStart.column),
            toLength(change.changeStart.line, change.changeEnd.column),
            toLength(change.changeEnd.line - change.changeStart.line + 1, 0)
        )
        handleEdits(edit, false);
    }

    private fun handleEdits(edits: TextEditInfo, tokenChange: Boolean) {
        // Lazily queue the edits and only apply them when the tree is accessed.
        val result = combineTextEditInfos(this.queuedTextEdits, listOf(edits));

        this.queuedTextEdits = result.toMutableList()
        if (this.initialAstWithoutTokens != null && !tokenChange) {
            this.queuedTextEditsForInitialAstWithoutTokens =
                combineTextEditInfos(this.queuedTextEditsForInitialAstWithoutTokens, listOf(edits))
                    .toMutableList()
        }
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
    val textModel: ContentReference
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
        var minIndentation = -1
        if (context.includeMinIndentation) {
            minIndentation = pairNode.computeMinIndentation(
                nodeOffsetStart,
                context.textModel
            )
        }

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

        var currentOffsetStart = openingBracketEnd
        if (shouldContinue && pairNode.child != null) {
            val child = pairNode.child
            val currentOffsetEnd = currentOffsetStart + child.length
            if (currentOffsetStart.value <= endOffset.value &&
                currentOffsetEnd.value >= startOffset.value
            ) {
                shouldContinue = collectBracketPairs(
                    child,
                    currentOffsetStart,
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

fun StyleUpdateRange.toRange(): TextRange {

    var startLine = 0
    var endLine = 0
    val iterator = lineIndexIterator(Int.MAX_VALUE)
    while (iterator.hasNext()) {
        val line = iterator.nextInt()
        if (startLine == 0) {
            startLine = line
        }
        endLine = line
    }
    // TODO: check range
    return TextRange(CharPosition(startLine, 0), CharPosition(endLine + 1, 0))
}