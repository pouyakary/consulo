// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.HighPriorityAction;
import consulo.language.editor.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.config.IntentionSettingsConfigurable;
import consulo.codeEditor.Editor;
import com.intellij.openapi.options.ShowSettingsUtil;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;

public class EditIntentionSettingsAction extends AbstractEditIntentionSettingsAction implements HighPriorityAction {
  public EditIntentionSettingsAction(IntentionAction action) {
    super(action);
  }

  @Nonnull
  @Override
  public String getText() {
    return "Edit intention settings";
  }

  @Override
  @RequiredUIAccess
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    ShowSettingsUtil.getInstance().showAndSelect(project, IntentionSettingsConfigurable.class, configurable -> {
      configurable.selectIntention(myFamilyName);
    });
  }
}
