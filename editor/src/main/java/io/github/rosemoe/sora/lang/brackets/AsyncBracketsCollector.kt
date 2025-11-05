/*
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
 */
package io.github.rosemoe.sora.lang.brackets

import android.util.Log
import androidx.annotation.WorkerThread
import io.github.rosemoe.sora.lang.analysis.StyleReceiver
import io.github.rosemoe.sora.text.Content
import java.util.Collections
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Base implementation of [BracketsProvider] that executes heavy computations on a single
 * background thread. Results are dispatched to the registered [StyleReceiver], and the
 * latest values are cached so that repeated calls may return immediately while a fresh computation
 * is scheduled.
 */
abstract class AsyncBracketsCollector(private val threadNamePrefix: String) : BracketsProvider {

    private val matchedGeneration = AtomicLong()
    private val rangeGeneration = AtomicLong()
    private val matchedFuture = AtomicReference<Future<*>?>(null)
    private val rangeFuture = AtomicReference<Future<*>?>(null)
    private val destroyed = AtomicBoolean(false)

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, nextThreadName(threadNamePrefix)).apply {
            priority = Thread.NORM_PRIORITY - 1
            isDaemon = true
        }
    }

    @Volatile
    private var receiver: StyleReceiver? = null

    @Volatile
    private var lastMatchedPair: PairedBracket? = null

    @Volatile
    private var lastBracketPairs: List<PairedBracket>? = null

    override fun setReceiver(receiver: StyleReceiver?) {
        this.receiver = receiver
        if (receiver != null) {
            receiver.updateMatchedBracketPair(this, lastMatchedPair)
            receiver.updateBracketPairsInRange(this, lastBracketPairs)
        }
    }

    override fun destroy() {
        if (destroyed.compareAndSet(false, true)) {
            cancelFuture(matchedFuture.getAndSet(null))
            cancelFuture(rangeFuture.getAndSet(null))
            executor.shutdownNow()
            receiver = null
            lastMatchedPair = null
            lastBracketPairs = null
        }
    }

    override fun getPairedBracketAt(text: Content, index: Int) {
        if (destroyed.get()) {
            return
        }
        val generation = matchedGeneration.incrementAndGet()
        runCatching {
            error("getPairedBracketAt")
        }.onFailure {
            println(it)
        }
        val future = executor.submit {
            val token = CancellationToken(matchedGeneration, generation)
            try {
                val result = computeMatchedBracket(text, index, token)
                if (!token.isCancelled) {
                    dispatchMatched(result)
                }
            } catch (_: CancellationException) {
                // Expected when cancelled via token
            } catch (throwable: Throwable) {
                handleComputationError(throwable)
            }
        }
        cancelFuture(matchedFuture.getAndSet(future))
    }

    override fun getPairedBracketsAtRange(text: Content, leftPosition: Long, rightPosition: Long) {
        if (destroyed.get()) {
            return
        }
        runCatching {
            error("getPairedBracketsAtRange")
        }.onFailure {
            println(it)
        }
        val generation = rangeGeneration.incrementAndGet()
        val future = executor.submit {
            val token = CancellationToken(rangeGeneration, generation)
            try {
                val result = computeBracketPairsInRange(text, leftPosition, rightPosition, token)
                if (!token.isCancelled) {
                    dispatchRange(result)
                }
            } catch (_: CancellationException) {
                // Expected when cancelled via token
            } catch (throwable: Throwable) {
                handleComputationError(throwable)
            }
        }
        cancelFuture(rangeFuture.getAndSet(future))
    }

    protected fun dispatchMatched(result: PairedBracket?) {
        lastMatchedPair = result
        receiver?.updateMatchedBracketPair(this, result)
    }

    protected fun dispatchRange(result: List<PairedBracket>?) {
        lastBracketPairs = result?.let { Collections.unmodifiableList(ArrayList(it)) }
        receiver?.updateBracketPairsInRange(this, lastBracketPairs)
    }

    protected open fun handleComputationError(throwable: Throwable) {
        Log.w(TAG, "Bracket computation failed", throwable)
    }

    private fun cancelFuture(future: Future<*>?) {
        future?.cancel(true)
    }

    protected class CancellationToken internal constructor(
        private val generation: AtomicLong,
        private val target: Long
    ) {

        val isCancelled: Boolean
            get() = target != generation.get() || Thread.currentThread().isInterrupted

        fun throwIfCancelled() {
            if (isCancelled) {
                throw CancellationException()
            }
        }
    }

    @WorkerThread
    protected abstract fun computeMatchedBracket(
        text: Content,
        index: Int,
        cancellationToken: CancellationToken
    ): PairedBracket?

    @WorkerThread
    protected abstract fun computeBracketPairsInRange(
        text: Content,
        leftPosition: Long,
        rightPosition: Long,
        cancellationToken: CancellationToken
    ): List<PairedBracket>?

    companion object {
        private const val TAG = "AsyncBracketsCollector"
        private val THREAD_ID = AtomicInteger(0)

        private fun nextThreadName(prefix: String): String {
            val id = THREAD_ID.incrementAndGet()
            return "$prefix-$id"
        }
    }
}
