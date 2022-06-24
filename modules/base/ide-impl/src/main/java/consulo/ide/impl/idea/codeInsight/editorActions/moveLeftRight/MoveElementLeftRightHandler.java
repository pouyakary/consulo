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
package consulo.ide.impl.idea.codeInsight.editorActions.moveLeftRight;

import consulo.language.OldLanguageExtension;
import consulo.language.psi.PsiElement;
import consulo.annotation.access.RequiredReadAction;
import consulo.container.plugin.PluginIds;

import javax.annotation.Nonnull;

/**
 * Instances of this class implement language-specific logic of 'move element left/right' actions
 */
public interface MoveElementLeftRightHandler {
  OldLanguageExtension<MoveElementLeftRightHandler> EP = new OldLanguageExtension<>(PluginIds.CONSULO_BASE + ".moveLeftRightHandler");

  /**
   * Returns a list of sub-elements (usually children) of given PSI element, which can be moved using 'move element left/right' actions.
   * Should return an empty array if there are no such elements.
   */
  @Nonnull
  @RequiredReadAction
  PsiElement[] getMovableSubElements(@Nonnull PsiElement element);
}
