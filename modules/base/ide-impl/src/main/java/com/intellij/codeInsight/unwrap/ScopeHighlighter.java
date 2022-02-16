/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.unwrap;

import consulo.codeEditor.Editor;
import consulo.codeEditor.colorScheme.EditorColors;
import consulo.codeEditor.colorScheme.EditorColorsManager;
import consulo.codeEditor.markup.HighlighterTargetArea;
import consulo.codeEditor.markup.MarkupModel;
import consulo.codeEditor.markup.RangeHighlighter;
import consulo.codeEditor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import com.intellij.util.NotNullFunction;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public class ScopeHighlighter {
  public static final NotNullFunction<PsiElement, TextRange> NATURAL_RANGER = PsiElement::getTextRange;

  @Nonnull
  private final Editor myEditor;
  @Nonnull
  private final List<RangeHighlighter> myActiveHighliters = new ArrayList<>();
  @Nonnull
  private final NotNullFunction<? super PsiElement, ? extends TextRange> myRanger;

  public ScopeHighlighter(@Nonnull Editor editor) {
    this(editor, NATURAL_RANGER);
  }

  public ScopeHighlighter(@Nonnull Editor editor, @Nonnull NotNullFunction<? super PsiElement, ? extends TextRange> ranger) {
    myEditor = editor;
    myRanger = ranger;
  }

  public void highlight(@Nonnull PsiElement wholeAffected, @Nonnull List<? extends PsiElement> toExtract) {
    Pair<TextRange, List<TextRange>> ranges = collectTextRanges(wholeAffected, toExtract);

    highlight(ranges);
  }

  public void highlight(@Nonnull Pair<TextRange, List<TextRange>> ranges) {
    dropHighlight();

    TextRange wholeRange = ranges.first;

    List<TextRange> rangesToExtract = ranges.second;
    List<TextRange> rangesToRemove = RangeSplitter.split(wholeRange, rangesToExtract);

    for (TextRange r : rangesToRemove) {
      addHighlighter(r, UnwrapHandler.HIGHLIGHTER_LEVEL, getTestAttributesForRemoval());
    }
    for (TextRange r : rangesToExtract) {
      addHighlighter(r, UnwrapHandler.HIGHLIGHTER_LEVEL, UnwrapHandler.getTestAttributesForExtract());
    }
  }

  private Pair<TextRange, List<TextRange>> collectTextRanges(PsiElement wholeElement, List<? extends PsiElement> elementsToExtract) {
    TextRange affectedRange = getRange(wholeElement);
    List<TextRange> rangesToExtract = new ArrayList<>();

    for (PsiElement e : elementsToExtract) {
      rangesToExtract.add(getRange(e));
    }

    return Pair.create(affectedRange, rangesToExtract);
  }

  private TextRange getRange(PsiElement e) {
    return myRanger.fun(e);
  }

  private void addHighlighter(TextRange r, int level, TextAttributes attr) {
    MarkupModel markupModel = myEditor.getMarkupModel();
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(r.getStartOffset(), r.getEndOffset(), level, attr, HighlighterTargetArea.EXACT_RANGE);
    myActiveHighliters.add(highlighter);
  }

  public void dropHighlight() {
    for (RangeHighlighter h : myActiveHighliters) {
      h.dispose();
    }
    myActiveHighliters.clear();
  }

  private static TextAttributes getTestAttributesForRemoval() {
    EditorColorsManager manager = EditorColorsManager.getInstance();
    return manager.getGlobalScheme().getAttributes(EditorColors.DELETED_TEXT_ATTRIBUTES);
  }
}
