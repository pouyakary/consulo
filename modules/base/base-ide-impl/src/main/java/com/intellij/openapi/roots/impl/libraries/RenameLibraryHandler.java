/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.impl.libraries;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import consulo.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import consulo.logging.Logger;
import com.intellij.openapi.editor.Editor;
import consulo.project.Project;
import consulo.content.library.Library;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class RenameLibraryHandler implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance(RenameLibraryHandler.class);

  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    Library library = dataContext.getData(LangDataKeys.LIBRARY);
    return library != null;
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.assertTrue(false);
  }

  @Override
  public void invoke(@Nonnull final Project project, @Nonnull PsiElement[] elements, @Nonnull DataContext dataContext) {
    final Library library = dataContext.getData(LangDataKeys.LIBRARY);
    LOG.assertTrue(library != null);
    Messages.showInputDialog(project,
                             IdeBundle.message("prompt.enter.new.library.name"),
                             IdeBundle.message("title.rename.library"),
                             Messages.getQuestionIcon(),
                             library.getName(),
                             new MyInputValidator(project, library));
  }

  @Override
  public String getActionTitle() {
    return IdeBundle.message("title.rename.library");
  }

  private static class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final Library myLibrary;
    public MyInputValidator(Project project, Library library) {
      myProject = project;
      myLibrary = library;
    }

    @Override
    public boolean checkInput(String inputString) {
      return inputString != null && !inputString.isEmpty() && myLibrary.getTable().getLibraryByName(inputString) == null;
    }

    @Override
    public boolean canClose(final String inputString) {
      final String oldName = myLibrary.getName();
      final Library.ModifiableModel modifiableModel = renameLibrary(inputString);
      if (modifiableModel == null) return false;
      final Ref<Boolean> success = Ref.create(Boolean.TRUE);
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        @Override
        public void run() {
          UndoableAction action = new BasicUndoableAction() {
            @Override
            public void undo() throws UnexpectedUndoException {
              final Library.ModifiableModel modifiableModel = renameLibrary(oldName);
              if (modifiableModel != null) {
                modifiableModel.commit();
              }
            }

            @Override
            public void redo() throws UnexpectedUndoException {
              final Library.ModifiableModel modifiableModel = renameLibrary(inputString);
              if (modifiableModel != null) {
                modifiableModel.commit();
              }
            }
          };
          UndoManager.getInstance(myProject).undoableActionPerformed(action);
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              modifiableModel.commit();
            }
          });
        }
      }, IdeBundle.message("command.renaming.module", oldName), null);
      return success.get().booleanValue();
    }

    @Nullable
    private Library.ModifiableModel renameLibrary(String inputString) {
      final Library.ModifiableModel modifiableModel = myLibrary.getModifiableModel();
      modifiableModel.setName(inputString);
      return modifiableModel;
    }
  }

}
