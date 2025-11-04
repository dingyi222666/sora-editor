/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree

import kotlin.math.max
import kotlin.math.min

private val emptyArr: IntArray = intArrayOf()

/**
 * Represents an immutable set that works best for a small number of elements (less than 32).
 * It uses bits to encode element membership efficiently.
 */
class SmallImmutableSet<T> private constructor(
    private val items: Int,
    private val additionalItems: IntArray
) {
    companion object {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        private val cache = arrayOfNulls<SmallImmutableSet<*>>(129)

        private fun <T> create(items: Int, additionalItems: IntArray): SmallImmutableSet<T> {
            if (items <= 128 && additionalItems.isEmpty()) {
                // We create a cache of 128=2^7 elements to cover all sets with up to 7 (dense) elements.
                var cached = cache[items]
                if (cached == null) {
                    cached = SmallImmutableSet<Any?>(items, additionalItems)
                    cache[items] = cached
                }
                @Suppress("UNCHECKED_CAST")
                return cached as SmallImmutableSet<T>
            }

            return SmallImmutableSet(items, additionalItems)
        }

        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        private val empty = create<Any?>(0, emptyArr)
        fun <T> getEmpty(): SmallImmutableSet<T> {
            @Suppress("UNCHECKED_CAST")
            return this.empty as SmallImmutableSet<T>
        }
    }

    fun add(value: T, keyProvider: IDenseKeyProvider<T>): SmallImmutableSet<T> {
        val key = keyProvider.getKey(value)
        var idx = key ushr 5 // divided by 32
        if (idx == 0) {
            // fast path
            val newItem = (1 shl key) or this.items
            if (newItem == this.items) {
                return this
            }
            return create(newItem, this.additionalItems)
        }
        idx--

        val newItems = if (idx >= additionalItems.size) {
            // Grow array
            val newArr = IntArray(idx + 1)
            additionalItems.copyInto(newArr)
            newArr
        } else {
            additionalItems.copyOf()
        }

        newItems[idx] = newItems[idx] or (1 shl (key and 31))

        return create(this.items, newItems)
    }

    fun has(value: T, keyProvider: IDenseKeyProvider<T>): Boolean {
        val key = keyProvider.getKey(value)
        var idx = key ushr 5 // divided by 32
        if (idx == 0) {
            // fast path
            return (this.items and (1 shl key)) != 0
        }
        idx--

        val valueAtIndex = this.additionalItems.getOrNull(idx) ?: 0
        return (valueAtIndex and (1 shl (key and 31))) != 0
    }

    fun merge(other: SmallImmutableSet<T>): SmallImmutableSet<T> {
        val merged = this.items or other.items

        if (this.additionalItems.isEmpty() && other.additionalItems.isEmpty()) {
            // fast path
            if (merged == this.items) {
                return this
            }
            if (merged == other.items) {
                return other
            }
            return create(merged, emptyArr)
        }

        // This can be optimized, but it's not a common case
        val newItems = IntArray(max(this.additionalItems.size, other.additionalItems.size)) { i ->
            val item1 = this.additionalItems.getOrElse(i) { 0 }
            val item2 = other.additionalItems.getOrElse(i) { 0 }
            item1 or item2
        }

        return create(merged, newItems)
    }

    fun intersects(other: SmallImmutableSet<T>): Boolean {
        if ((this.items and other.items) != 0) {
            return true
        }

        for (i in 0 until min(this.additionalItems.size, other.additionalItems.size)) {
            if ((this.additionalItems[i] and other.additionalItems[i]) != 0) {
                return true
            }
        }

        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SmallImmutableSet<*>) return false

        if (this.items != other.items) return false
        if (!this.additionalItems.contentEquals(other.additionalItems)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = items
        result = 31 * result + additionalItems.contentHashCode()
        return result
    }
}

fun interface IDenseKeyProvider<T> {
    fun getKey(value: T): Int
}

/**
 * Assigns values a unique incrementing key.
 */
class DenseKeyProvider<T> : IDenseKeyProvider<T> {
    private val items = mutableMapOf<T, Int>()

    override fun getKey(value: T): Int {
        return items.getOrPut(value) { items.size }
    }

    fun reverseLookup(value: Int): T? {
        // This is not efficient, but matches the original's intent.
        // For performance, a reverse map would be needed.
        return items.entries.find { (_, v) -> v == value }?.key
    }

    fun reverseLookupSet(set: SmallImmutableSet<T>): List<T> {
        val result = mutableListOf<T>()
        for ((key) in this.items) {
            if (set.has(key, this)) {
                result.add(key)
            }
        }
        return result
    }

    fun set(value: T, id: Int) {
        this.items[value] = id
    }

    fun keys(): Iterator<T> {
        return this.items.keys.iterator()
    }
}