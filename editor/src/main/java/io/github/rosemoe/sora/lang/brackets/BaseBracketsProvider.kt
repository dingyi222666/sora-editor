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

import io.github.rosemoe.sora.lang.analysis.StyleReceiver
import io.github.rosemoe.sora.text.Content
import java.lang.ref.WeakReference

/**
 * Base implementation for bracket providers that compute results synchronously on the
 * caller thread. Implementations only need to provide the computation logic while this base class
 * handles dispatching results back to the registered [StyleReceiver].
 */
abstract class BaseBracketsProvider : BracketsProvider {

    private var receiverRef = WeakReference<StyleReceiver?>(null)

    override fun setReceiver(receiver: StyleReceiver?) {
        receiverRef = WeakReference(receiver)
    }

    override fun destroy() {
        receiverRef.clear()
    }

    protected fun receiver(): StyleReceiver? = receiverRef.get()

    final override fun getPairedBracketAt(text: Content, index: Int) {
        val result = onGetPairedBracketAt(text, index)
        receiver()?.updateMatchedBracketPair(this, result)
    }

    final override fun getPairedBracketsAtRange(text: Content, leftPosition: Long, rightPosition: Long) {
        val result = onGetPairedBracketsAtRange(text, leftPosition, rightPosition)
        receiver()?.updateBracketPairsInRange(this, result)
    }

    protected abstract fun onGetPairedBracketAt(text: Content, index: Int): PairedBracket?

    protected open fun onGetPairedBracketsAtRange(
        text: Content,
        leftPosition: Long,
        rightPosition: Long
    ): List<PairedBracket>? = null
}
