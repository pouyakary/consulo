// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import consulo.codeEditor.Editor;
import consulo.component.extension.ExtensionPointName;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.editor.inspection.QuickFix;
import consulo.language.psi.PsiFile;
import javax.annotation.Nonnull;

/**
 * Contributes actions to be shown in the Alt-Enter menu. Note that this is a low-level extensibility mechanism not designed to
 * be used by plugins. Plugin developers should implement {@link IntentionAction} or
 * {@link QuickFix} instead.
 */
//@ApiStatus.Internal
public interface IntentionMenuContributor {
  ExtensionPointName<IntentionMenuContributor> EP_NAME = ExtensionPointName.create("consulo.intentionMenuContributor");

  void collectActions(@Nonnull Editor hostEditor, @Nonnull PsiFile hostFile, @Nonnull final ShowIntentionsPass.IntentionsInfo intentions, int passIdToShowIntentionsFor, int offset);
}
