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
package io.github.rosemoe.sora.lang.brackets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.lang.analysis.StyleReceiver;
import io.github.rosemoe.sora.text.Content;

/**
 * Interface for providing brackets
 *
 * @author Rosemoe
 */
public interface BracketsProvider {

    /**
     * Register the {@link StyleReceiver} that should receive bracket updates.
     *
     * @param receiver the receiver to notify, or {@code null} to detach the current one
     */
    void setReceiver(@Nullable StyleReceiver receiver);

    /**
     * Release any resources held by this provider and stop dispatching updates.
     */
    void destroy();

    /**
     * Get left and right brackets position in text
     *
     * @param text  The text in editor
     * @param index Index of cursor in text
     */
    void getPairedBracketAt(@NonNull Content text, int index);

    void getPairedBracketsAtRange(@NonNull Content text, long leftPosition, long rightPosition);
}
