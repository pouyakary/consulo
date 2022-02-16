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
package com.intellij.openapi.editor.ex;

import consulo.codeEditor.FoldRegion;
import consulo.codeEditor.FoldingGroup;
import consulo.codeEditor.FoldingModel;
import consulo.codeEditor.markup.TextAttributes;
import consulo.disposer.Disposable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author max
 */
public interface FoldingModelEx extends FoldingModel {
  void setFoldingEnabled(boolean isEnabled);

  boolean isFoldingEnabled();

  FoldRegion getFoldingPlaceholderAt(@Nonnull Point p);

  boolean intersectsRegion(int startOffset, int endOffset);

  /**
   * Returns an index in an array returned by {@link #fetchTopLevel()} method, for the last folding region lying entirely before given
   * offset (region can touch given offset at its right edge).
   */
  int getLastCollapsedRegionBefore(int offset);

  TextAttributes getPlaceholderAttributes();

  FoldRegion[] fetchTopLevel();

  @Nullable
  FoldRegion createFoldRegion(int startOffset, int endOffset, @Nonnull String placeholder, @Nullable FoldingGroup group, boolean neverExpands);

  void addListener(@Nonnull FoldingListener listener, @Nonnull Disposable parentDisposable);

  void clearFoldRegions();

  void rebuild();

  @Nonnull
  List<FoldRegion> getGroupedRegions(FoldingGroup group);

  void clearDocumentRangesModificationStatus();

  boolean hasDocumentRegionChangedFor(@Nonnull FoldRegion region);
}
