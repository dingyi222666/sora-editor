/*
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
 */
package io.github.rosemoe.sora.widget;

import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

import io.github.rosemoe.sora.event.ScrollEvent;
import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager;
import io.github.rosemoe.sora.lang.analysis.StyleReceiver;
import io.github.rosemoe.sora.lang.analysis.StyleUpdateRange;
import io.github.rosemoe.sora.lang.brackets.BracketsProvider;
import io.github.rosemoe.sora.lang.brackets.PairedBracket;
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.util.IntPair;

public class EditorStyleDelegate implements StyleReceiver {

    private final WeakReference<CodeEditor> editorRef;
    private PairedBracket matchedBracketPair;
    private List<PairedBracket> matchedBracketsInRange;
    private BracketsProvider bracketsProvider;

    EditorStyleDelegate(@NonNull CodeEditor editor) {
        editorRef = new WeakReference<>(editor);
        editor.subscribeEvent(SelectionChangeEvent.class, (event, __) -> {
            if (!event.isSelected()) {
                postUpdateBracketPair();
            }
        });
        editor.subscribeEvent(ScrollEvent.class, (event, __) -> {
            if (!editor.isSelected()) {
                postUpdateBracketPair();
            }
        });
    }

    void onTextChange() {
        //  Should we do this?
        //bracketsProvider = null;
        //foundPair = null;
    }

    void postUpdateBracketPair() {
        runOnUiThread(() -> {
            final var provider = bracketsProvider;
            final var editor = editorRef.get();
            if (provider == null || editor == null) {
                setMatchedBracketPair(null);
                setMatchedBracketPairsInRange(null);
                return;
            }
            if (editor.getCursor().isSelected() || !editor.isHighlightBracketPair()) {
                setMatchedBracketPair(null);
                setMatchedBracketPairsInRange(null);
                return;
            }
            var cursor = editor.getCursor();
            provider.getPairedBracketAt(editor.getText(), cursor.getLeft());
            var firstVisible = editor.getFirstVisibleLine();
            var lastVisible = editor.getLastVisibleLine();
            if (firstVisible >= 0 && lastVisible >= firstVisible) {
                var text = editor.getText();
                var lineCount = Math.max(0, text.getLineCount() - 1);
                var clampedLast = Math.min(lastVisible, lineCount);
                var clampedFirst = Math.min(firstVisible, clampedLast);
                var rightColumn = text.getColumnCount(clampedLast);
                provider.getPairedBracketsAtRange(
                        text,
                        IntPair.pack(clampedFirst, 0),
                        IntPair.pack(clampedLast, rightColumn)
                );
            } else {
                setMatchedBracketPairsInRange(null);
            }
        });
    }


    @Nullable
    public PairedBracket getMatchedBracketPair() {
        return matchedBracketPair;
    }

    @Nullable
    public List<PairedBracket> getBracketsInRange() {
        return matchedBracketsInRange;
    }

    void reset() {
        setMatchedBracketPair(null);
        setMatchedBracketPairsInRange(null);
        if (bracketsProvider != null) {
            bracketsProvider.setReceiver(null);
        }
        bracketsProvider = null;
    }

    private void runOnUiThread(Runnable operation) {
        var editor = editorRef.get();
        if (editor == null) {
            return;
        }
        if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
            operation.run();
        } else {
            editor.postInLifecycle(operation);
        }
    }

    @Override
    public void setStyles(@NonNull AnalyzeManager sourceManager, @Nullable Styles styles) {
        setStyles(sourceManager, styles, null);
    }

    @Override
    public void setStyles(@NonNull AnalyzeManager sourceManager, @Nullable Styles styles, @Nullable Runnable action) {
        var editor = editorRef.get();
        if (editor != null && sourceManager == editor.getEditorLanguage().getAnalyzeManager()) {
            runOnUiThread(() -> {
                if (action != null) {
                    action.run();
                }
                editor.setStyles(styles);
            });
        }
    }

    @Override
    public void setDiagnostics(@NonNull AnalyzeManager sourceManager, @Nullable DiagnosticsContainer diagnostics) {
        var editor = editorRef.get();
        if (editor != null && sourceManager == editor.getEditorLanguage().getAnalyzeManager()) {
            runOnUiThread(() -> editor.setDiagnostics(diagnostics));
        }
    }

    @Override
    public void updateBracketProvider(@NonNull AnalyzeManager sourceManager, @Nullable BracketsProvider provider) {
        var editor = editorRef.get();
        if (editor != null && sourceManager == editor.getEditorLanguage().getAnalyzeManager() && bracketsProvider != provider) {
            if (bracketsProvider != null) {
                bracketsProvider.setReceiver(null);
            }
            this.bracketsProvider = provider;
            if (provider != null) {
                provider.setReceiver(this);
            }
            postUpdateBracketPair();
        }
    }

    @Override
    public void updateStyles(@NonNull AnalyzeManager sourceManager, @NonNull Styles styles, @NonNull StyleUpdateRange range) {
        var editor = editorRef.get();
        if (editor != null && sourceManager == editor.getEditorLanguage().getAnalyzeManager()) {
            runOnUiThread(() -> editor.updateStyles(styles, range));
        }
    }

    public void clearMatchedBracketPair() {
        setMatchedBracketPair(null);
    }

    public void setMatchedBracketPair(@Nullable PairedBracket pair) {
        runOnUiThread(() -> {
            matchedBracketPair = pair;
            invalidateEditor();
        });
    }

    private void setMatchedBracketPairsInRange(@Nullable List<PairedBracket> pairs) {
        runOnUiThread(() -> {
            matchedBracketsInRange = pairs;
            invalidateEditor();
        });
    }

    private void invalidateEditor() {
        var editor = editorRef.get();
        if (editor != null) {
            editor.invalidate();
        }
    }

    @Override
    public void updateMatchedBracketPair(@NonNull BracketsProvider provider, @Nullable PairedBracket pair) {
        if (provider == bracketsProvider && shouldHighlight()) {
            setMatchedBracketPair(pair);
        }
    }

    @Override
    public void updateBracketPairsInRange(@NonNull BracketsProvider provider, @Nullable List<PairedBracket> pairs) {
        if (provider == bracketsProvider && shouldHighlight()) {
            System.out.println(pairs);
            setMatchedBracketPairsInRange(pairs);
        }
    }

    private boolean shouldHighlight() {
        var editor = editorRef.get();
        return editor != null && editor.isHighlightBracketPair() && !editor.getCursor().isSelected();
    }
}
