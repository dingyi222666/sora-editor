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

package io.github.rosemoe.sora.langs.textmate.brackets.tree.utils

/**
 * Lightweight callback-based iterable used for lazily produced sequences.
 * Uses inline helpers to avoid allocation-heavy iterator chains.
 */
fun interface CallbackIterable<T> {

    /**
     * Calls [callback] for every item. Returning false aborts the traversal.
     */
    fun iterate(callback: (item: T) -> Boolean)

    companion object {
        val empty: CallbackIterable<Nothing> = CallbackIterable { }

        @Suppress("UNCHECKED_CAST")
        fun <T> empty(): CallbackIterable<T> = empty as CallbackIterable<T>
    }
}

inline fun <T> CallbackIterable<T>.forEach(crossinline handler: (item: T) -> Unit) {
    iterate {
        handler(it)
        true
    }
}

inline fun <T> CallbackIterable<T>.toList(): List<T> {
    val result = mutableListOf<T>()
    iterate {
        result.add(it)
        true
    }
    return result
}

inline fun <T> CallbackIterable<T>.filter(crossinline predicate: (item: T) -> Boolean): CallbackIterable<T> =
    CallbackIterable { cb ->
        iterate { item ->
            if (predicate(item)) {
                cb(item)
            } else {
                true
            }
        }
    }

inline fun <T, R> CallbackIterable<T>.map(crossinline mapFn: (item: T) -> R): CallbackIterable<R> =
    CallbackIterable { cb ->
        iterate { item ->
            cb(mapFn(item))
        }
    }

inline fun <T> CallbackIterable<T>.any(crossinline predicate: (item: T) -> Boolean): Boolean {
    var result = false
    iterate { item ->
        if (predicate(item)) {
            result = true
            return@iterate false
        }
        true
    }
    return result
}

inline fun <T> CallbackIterable<T>.find(crossinline predicate: (item: T) -> Boolean): T? {
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

inline fun <T> CallbackIterable<T>.findLast(crossinline predicate: (item: T) -> Boolean): T? {
    var result: T? = null
    iterate { item ->
        if (predicate(item)) {
            result = item
        }
        true
    }
    return result
}

inline fun <T, R : Comparable<R>> CallbackIterable<T>.maxByOrNull(crossinline selector: (T) -> R): T? {
    var max: T? = null
    var maxValue: R? = null
    iterate { item ->
        val candidate = selector(item)
        if (maxValue == null || candidate > maxValue!!) {
            max = item
            maxValue = candidate
        }
        true
    }
    return max
}
