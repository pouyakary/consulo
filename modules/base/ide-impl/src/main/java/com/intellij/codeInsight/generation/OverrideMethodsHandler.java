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

package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.lang.CodeInsightActions;
import consulo.language.Language;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import consulo.codeEditor.Editor;
import consulo.document.FileDocumentManager;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import javax.annotation.Nonnull;
import consulo.ui.annotation.RequiredUIAccess;

public class OverrideMethodsHandler implements CodeInsightActionHandler{
  @RequiredUIAccess
  @Override
  public final void invoke(@Nonnull final Project project, @Nonnull final Editor editor, @Nonnull PsiFile file) {
    if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
    if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)){
      return;
    }

    Language language = PsiUtilCore.getLanguageAtOffset(file, editor.getCaretModel().getOffset());
    final LanguageCodeInsightActionHandler codeInsightActionHandler = CodeInsightActions.OVERRIDE_METHOD.forLanguage(language);
    if (codeInsightActionHandler != null) {
      codeInsightActionHandler.invoke(project, editor, file);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
