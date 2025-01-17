/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package consulo.language.editor.intention;

import consulo.document.util.TextRange;
import consulo.language.editor.internal.QuickFixActionRegistrarImpl;
import consulo.language.editor.rawHighlight.HighlightDisplayKey;
import consulo.language.editor.rawHighlight.HighlightInfo;

import jakarta.annotation.Nonnull;
import java.util.function.Predicate;

public interface QuickFixActionRegistrar {
  @Nonnull
  static QuickFixActionRegistrar create(@Nonnull HighlightInfo highlightInfo) {
    return new QuickFixActionRegistrarImpl(highlightInfo);
  }

  void register(IntentionAction action);

  void register(TextRange fixRange, IntentionAction action, HighlightDisplayKey key);

  /**
   * Allows to replace some of the built-in quickfixes.
   *
   * @param condition condition for quickfixes to remove
   */
  void unregister(Predicate<IntentionAction> condition);
}
