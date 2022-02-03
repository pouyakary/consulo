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
package com.intellij.openapi.editor.impl;

import consulo.ui.ex.action.ActionManager;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import consulo.document.Document;
import com.intellij.openapi.editor.actionSystem.*;
import com.intellij.openapi.ui.Messages;
import consulo.language.file.inject.DocumentWindow;
import javax.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class EditorActionManagerImpl extends EditorActionManager {
  private ReadonlyFragmentModificationHandler myReadonlyFragmentsHandler = new DefaultReadOnlyFragmentModificationHandler();
  private final ActionManager myActionManager;

  @Inject
  public EditorActionManagerImpl(ActionManager actionManager) {
    myActionManager = actionManager;
  }

  @Override
  public EditorActionHandler getActionHandler(@Nonnull String actionId) {
    return ((EditorAction) myActionManager.getAction(actionId)).getHandler();
  }

  @Override
  public EditorActionHandler setActionHandler(@Nonnull String actionId, @Nonnull EditorActionHandler handler) {
    EditorAction action = (EditorAction)myActionManager.getAction(actionId);
    return action.setupHandler(handler);
  }

  @Override
  @Nonnull
  public TypedAction getTypedAction() {
    return TypedAction.getInstance();
  }

  @Override
  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler() {
    return myReadonlyFragmentsHandler;
  }

  @Override
  public ReadonlyFragmentModificationHandler getReadonlyFragmentModificationHandler(@Nonnull final Document document) {
    final Document doc = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
    final ReadonlyFragmentModificationHandler docHandler =
            doc instanceof DocumentImpl ? ((DocumentImpl)doc).getReadonlyFragmentModificationHandler() : null;
    return docHandler == null ? myReadonlyFragmentsHandler : docHandler;
  }

  @Override
  public void setReadonlyFragmentModificationHandler(@Nonnull final Document document, final ReadonlyFragmentModificationHandler handler) {
    final Document doc = document instanceof DocumentWindow ? ((DocumentWindow)document).getDelegate() : document;
    if (doc instanceof DocumentImpl) {
      ((DocumentImpl)document).setReadonlyFragmentModificationHandler(handler);
    }
  }

  @Override
  public ReadonlyFragmentModificationHandler setReadonlyFragmentModificationHandler(@Nonnull ReadonlyFragmentModificationHandler handler) {
    ReadonlyFragmentModificationHandler oldHandler = myReadonlyFragmentsHandler;
    myReadonlyFragmentsHandler = handler;
    return oldHandler;
  }


  private static class DefaultReadOnlyFragmentModificationHandler implements ReadonlyFragmentModificationHandler {
    @Override
    public void handle(ReadOnlyFragmentModificationException e) {
      Messages.showErrorDialog(EditorBundle.message("guarded.block.modification.attempt.error.message"),
                               EditorBundle.message("guarded.block.modification.attempt.error.title"));
    }
  }
}

