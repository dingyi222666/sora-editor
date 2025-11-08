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
package io.github.rosemoe.sora.langs.textmate;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;

import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.EncodedTokenAttributes;
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.StandardTokenType;
import org.eclipse.tm4e.core.internal.oniguruma.OnigRegExp;
import org.eclipse.tm4e.core.internal.oniguruma.OnigResult;
import org.eclipse.tm4e.core.internal.oniguruma.OnigString;
import org.eclipse.tm4e.core.internal.theme.FontStyle;
import org.eclipse.tm4e.core.internal.theme.Theme;
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager;
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete;
import io.github.rosemoe.sora.lang.styling.CodeBlock;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.SpanFactory;
import io.github.rosemoe.sora.lang.styling.Spans;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.lang.styling.TextStyle;
import io.github.rosemoe.sora.langs.textmate.brackets.BracketsConfiguration;
import io.github.rosemoe.sora.langs.textmate.brackets.TextMateBracketPairProvider;
import io.github.rosemoe.sora.langs.textmate.brackets.tree.TreeContentSnapshot;
import io.github.rosemoe.sora.langs.textmate.brackets.tree.TreeContentSnapshotProvider;
import io.github.rosemoe.sora.langs.textmate.brackets.tree.tokenizer.BracketTokens;
import io.github.rosemoe.sora.langs.textmate.folding.FoldingHelper;
import io.github.rosemoe.sora.langs.textmate.folding.IndentRange;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.utils.StringUtils;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.text.ContentReference;
import io.github.rosemoe.sora.util.ArrayList;
import io.github.rosemoe.sora.util.MyCharacter;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

public class TextMateAnalyzer extends AsyncIncrementalAnalyzeManager<MyState, Span> implements FoldingHelper, ThemeRegistry.ThemeChangeListener, TreeContentSnapshotProvider {

    private final IGrammar grammar;

    private Theme theme;
    private final TextMateLanguage language;
    private final LanguageConfiguration configuration;

    //private final GrammarRegistry grammarRegistry;

    private final ThemeRegistry themeRegistry;

    private OnigRegExp cachedRegExp;
    private boolean foldingOffside;
    private TextMateBracketPairProvider bracketsProvider;
    final IdentifierAutoComplete.SyncIdentifiers syncIdentifiers = new IdentifierAutoComplete.SyncIdentifiers();

    @Nullable
    private Styles styles = null;

    public TextMateAnalyzer(TextMateLanguage language, IGrammar grammar, LanguageConfiguration languageConfiguration,/* GrammarRegistry grammarRegistry,*/ ThemeRegistry themeRegistry) {
        this.language = language;

        this.theme = themeRegistry.getCurrentThemeModel().getTheme();

        this.grammar = grammar;

        //this.grammarRegistry = grammarRegistry;

        this.themeRegistry = themeRegistry;

        if (!themeRegistry.hasListener(this)) {
            themeRegistry.addListener(this);
        }

        if (languageConfiguration != null) {
            configuration = languageConfiguration;
            var pairs = languageConfiguration.getColorizedBracketPairs();

            if (pairs == null || pairs.isEmpty()) {
                pairs = languageConfiguration.getBrackets();
            }
            if (pairs != null && !pairs.isEmpty()) {
                this.bracketsProvider = new TextMateBracketPairProvider(this, BracketTokens.createFromLanguage(
                        new BracketsConfiguration(
                                pairs,
                                languageConfiguration.getColorizedBracketPairs()
                        )
                ));
            }
        } else {
            configuration = null;
        }

        createFoldingExp();
    }

    private void createFoldingExp() {
        if (configuration == null) {
            return;
        }
        var markers = configuration.getFolding();
        if (markers == null) return;
        foldingOffside = markers.offSide;
        cachedRegExp = new OnigRegExp("(" + markers.markersStart + ")|(?:" + markers.markersEnd + ")");
    }

    @Override
    public MyState getInitialState() {
        return null;
    }

    @Override
    public boolean stateEquals(MyState state, MyState another) {
        if (state == null && another == null) {
            return true;
        }
        if (state != null && another != null) {
            return Objects.equals(state.tokenizeState, another.tokenizeState);
        }
        return false;
    }

    @Override
    public int getIndentFor(int line) {
        return getState(line).state.indent;
    }

    @Override
    public OnigResult getResultFor(int line) {
        return getState(line).state.foldingCache;
    }

    @Override
    public List<CodeBlock> computeBlocks(Content text, CodeBlockAnalyzeDelegate delegate) {
        var list = new ArrayList<CodeBlock>();
        analyzeCodeBlocks(text, list, delegate);
        if (delegate.isNotCancelled()) {
            withReceiver(r -> r.updateBracketProvider(this, bracketsProvider));
        }
        return list;
    }

    public void analyzeCodeBlocks(Content model, ArrayList<CodeBlock> blocks, CodeBlockAnalyzeDelegate delegate) {
        if (cachedRegExp == null) {
            return;
        }
        try {
            var foldingRegions = IndentRange.computeRanges(model, language.getTabSize(), foldingOffside, this, cachedRegExp, delegate);
            blocks.ensureCapacity(foldingRegions.length());
            for (int i = 0; i < foldingRegions.length() && delegate.isNotCancelled(); i++) {
                int startLine = foldingRegions.getStartLineNumber(i);
                int endLine = foldingRegions.getEndLineNumber(i);
                if (startLine != endLine) {
                    CodeBlock codeBlock = new CodeBlock();
                    codeBlock.toBottomOfEndLine = true;
                    codeBlock.startLine = startLine;
                    codeBlock.endLine = endLine;

                    // It's safe here to use raw data because the Content is only held by this thread
                    var length = model.getColumnCount(startLine);
                    var chars = model.getLine(startLine).getBackingCharArray();

                    codeBlock.startColumn = IndentRange.computeStartColumn(chars, length, language.getTabSize());
                    codeBlock.endColumn = codeBlock.startColumn;
                    blocks.add(codeBlock);
                }
            }
            Collections.sort(blocks, CodeBlock.COMPARATOR_END);
        } catch (Exception e) {
            e.printStackTrace();
        }
        getManagedStyles().setIndentCountMode(true);
    }

    @Override
    @SuppressLint("NewApi")
    public synchronized LineTokenizeResult<MyState, Span> tokenizeLine(CharSequence lineC, MyState state, int lineIndex) {
        String line = (lineC instanceof ContentLine) ? ((ContentLine) lineC).toStringWithNewline() : lineC.toString();
        var tokens = new ArrayList<Span>();
        var surrogate = StringUtils.checkSurrogate(line);
        var lineTokens = grammar.tokenizeLine2(line, state == null ? null : state.tokenizeState, Duration.ofSeconds(2));
        int tokensLength = lineTokens.getTokens().length / 2;
        var identifiers = language.createIdentifiers ? new ArrayList<String>() : null;
        for (int i = 0; i < tokensLength; i++) {
            int startIndex = StringUtils.convertUnicodeOffsetToUtf16(line, lineTokens.getTokens()[2 * i], surrogate);
            if (i == 0 && startIndex != 0) {
                tokens.add(SpanFactory.obtain(0, EditorColorScheme.TEXT_NORMAL));
            }
            int metadata = lineTokens.getTokens()[2 * i + 1];
            int foreground = EncodedTokenAttributes.getForeground(metadata);
            int backgroud = EncodedTokenAttributes.getBackground(metadata);
            int fontStyle = EncodedTokenAttributes.getFontStyle(metadata);
            var tokenType = EncodedTokenAttributes.getTokenType(metadata);
            if (language.createIdentifiers) {
                if (tokenType == StandardTokenType.Other) {
                    var end = i + 1 == tokensLength ? lineC.length() : StringUtils.convertUnicodeOffsetToUtf16(line, lineTokens.getTokens()[2 * (i + 1)], surrogate);
                    if (end > startIndex && MyCharacter.isJavaIdentifierStart(line.charAt(startIndex))) {
                        var flag = true;
                        for (int j = startIndex + 1; j < end; j++) {
                            if (!MyCharacter.isJavaIdentifierPart(line.charAt(j))) {
                                flag = false;
                                break;
                            }
                        }
                        if (flag) {
                            identifiers.add(line.substring(startIndex, end));
                        }
                    }
                }
            }

            Span span = SpanFactory.obtain(startIndex,
                    TextStyle.makeStyle(
                            foreground + 255, backgroud + 255,
                            (fontStyle & FontStyle.Bold) != 0, (fontStyle & FontStyle.Italic) != 0,
                            (fontStyle & FontStyle.Strikethrough) != 0, false
                    )
            );

            span.setExtra(tokenType);

            if ((fontStyle & FontStyle.Underline) != 0) {
                String color = theme.getColor(foreground);
                if (color != null) {
                    span.setUnderlineColor(Color.parseColor(color));
                }
            }

            tokens.add(span);
        }
        return new LineTokenizeResult<>(new MyState(lineTokens.getRuleStack(), cachedRegExp == null ? null : cachedRegExp.search(OnigString.of(line), 0), IndentRange.computeIndentLevel(((ContentLine) lineC).getBackingCharArray(), line.length() - 1, language.getTabSize()), identifiers), null, tokens);
    }

    @Override
    public @Nullable TreeContentSnapshot snapshot() {
        var ref = getManagedContent();
        if (ref == null) {
            return null;
        }
        Spans spans;

        if (styles == null) {
            return new TreeContentSnapshot(ref, null);
        }
        spans = styles.spans;
        return new TreeContentSnapshot(ref, spans);
    }

    @Override
    public void onAddState(MyState state) {
        super.onAddState(state);
        if (language.createIdentifiers) {
            for (String identifier : state.identifiers) {
                syncIdentifiers.identifierIncrease(identifier);
            }
        }
    }

    @Override
    public void onAbandonState(MyState state) {
        super.onAbandonState(state);
        if (language.createIdentifiers) {
            for (String identifier : state.identifiers) {
                syncIdentifiers.identifierDecrease(identifier);
            }
        }
    }

    @Override
    public void insert(@NonNull CharPosition start, @NonNull CharPosition end, @NonNull CharSequence insertedText) {
        styles = null;
        bracketsProvider.handleContentChanged(start, end, insertedText);
        super.insert(start, end, insertedText);
    }

    @Override
    public void delete(@NonNull CharPosition start, @NonNull CharPosition end, @NonNull CharSequence deletedText) {
        styles = null;
        bracketsProvider.handleContentChanged(start, end, deletedText);
        super.delete(start, end, deletedText);
    }

    @Override
    public void reset(@NonNull ContentReference content, @NonNull Bundle extraArguments) {
        super.reset(content, extraArguments);
        syncIdentifiers.clear();
        styles = null;
    }


    @Override
    protected void onStyleUpdated(Styles styles, int startLine, int endLine) {
        this.styles = styles;
        if (styles == null) {
            bracketsProvider.init();
            return;
        }
        updateBrackets(true);
    }

    @Override
    protected void onThreadMessageHandled() {

        updateBrackets(false);
    }

    private void updateBrackets(boolean isTokenChange) {
        var ref = getContentRef();
        if (ref == null) return;

        bracketsProvider.flush(isTokenChange);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (bracketsProvider != null) {
            bracketsProvider = null;
        }
        themeRegistry.removeListener(this);
    }

    @Override
    public List<Span> generateSpansForLine(LineTokenizeResult<MyState, Span> tokens) {
        return null;
    }

    @Override
    public void onChangeTheme(ThemeModel newTheme) {
        this.theme = newTheme.getTheme();
    }
}
