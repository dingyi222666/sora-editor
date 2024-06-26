/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
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

package io.github.rosemoe.sora.langs.monarch.theme

class ThemeTrieElement(private val mainRule: ThemeTrieElementRule) {
    private val children = mutableMapOf<String, ThemeTrieElement>()

    fun toExternalThemeTrieElement(): ExternalThemeTrieElement {
        val children = children.mapValues { it.value.toExternalThemeTrieElement() }.toMutableMap()
        return ExternalThemeTrieElement(mainRule, children)
    }

    fun match(token: String): ThemeTrieElementRule {
        if (token.isEmpty()) {
            return mainRule
        }

        val dotIndex = token.indexOf('.')
        val (head, tail) = if (dotIndex == -1) {
            token to ""
        } else {
            token.substring(0, dotIndex) to token.substring(dotIndex + 1)
        }

        val child = children[head]
        return child?.match(tail) ?: mainRule
    }

    fun insert(token: String, fontStyle: Int, foreground: Int, background: Int) {
        if (token.isEmpty()) {
            mainRule.acceptOverwrite(fontStyle, foreground, background)
            return
        }

        val dotIndex = token.indexOf('.')
        val (head, tail) = if (dotIndex == -1) {
            token to ""
        } else {
            token.substring(0, dotIndex) to token.substring(dotIndex + 1)
        }

        val child = children.getOrPut(head) { ThemeTrieElement(mainRule.clone()) }
        child.insert(tail, fontStyle, foreground, background)
    }

    override fun toString(): String {
        return "ThemeTrieElement(mainRule=$mainRule, children=$children)"
    }


}
