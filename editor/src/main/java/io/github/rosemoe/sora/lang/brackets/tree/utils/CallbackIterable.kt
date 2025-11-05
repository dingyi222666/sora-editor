/*******************************************************************************
 * ---------------------------------------------------------------------------------------------
 *  *  Copyright (c) Microsoft Corporation. All rights reserved.
 *  *  Licensed under the MIT License. See License.txt in the project root for license information.
 *  *--------------------------------------------------------------------------------------------
 ******************************************************************************/

package io.github.rosemoe.sora.lang.brackets.tree.utils

/**
 * This class is faster than an iterator and array for lazy computed data.
 */
class CallbackIterable<T>(
    /**
     * Calls the callback for every item.
     * Stops when the callback returns false.
     */
    val iterate: (callback: (item: T) -> Boolean) -> Unit
) {

    companion object {
        val empty = CallbackIterable<Nothing> { }
    }

    fun forEach(handler: (item: T) -> Unit) {
        iterate { item ->
            handler(item)
            true
        }
    }

    fun toList(): List<T> {
        val result = mutableListOf<T>()
        iterate { item ->
            result.add(item)
            true
        }
        return result
    }

    fun filter(predicate: (item: T) -> Boolean): CallbackIterable<T> {
        return CallbackIterable { cb ->
            this.iterate { item ->
                if (predicate(item)) cb(item) else true
            }
        }
    }

    fun <R> map(mapFn: (item: T) -> R): CallbackIterable<R> {
        return CallbackIterable { cb ->
            this.iterate { item ->
                cb(mapFn(item))
            }
        }
    }

    fun any(predicate: (item: T) -> Boolean): Boolean {
        var result = false
        iterate { item ->
            result = predicate(item)
            !result
        }
        return result
    }

    fun find(predicate: (item: T) -> Boolean): T? {
        var result: T? = null
        iterate { item ->
            if (predicate(item)) {
                result = item
                return@iterate false
            }
            true
        }
        return result
    }

    fun findLast(predicate: (item: T) -> Boolean): T? {
        var result: T? = null
        iterate { item ->
            if (predicate(item)) {
                result = item
            }
            true
        }
        return result
    }

    fun <R : Comparable<R>> maxByOrNull(selector: (T) -> R): T? {
        var max: T? = null
        var maxValue: R? = null
        iterate { item ->
            val v = selector(item)
            if (maxValue == null || v > maxValue!!) {
                max = item
                maxValue = v
            }
            true
        }
        return max
    }
}