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

package consulo.ide.impl.idea.ide.actions;

import consulo.application.dumb.DumbAware;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.TextEditor;
import consulo.find.FindManager;
import consulo.ide.IdeBundle;
import consulo.ide.impl.idea.find.FindUtil;
import consulo.ide.impl.idea.openapi.fileEditor.ex.IdeDocumentHistory;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.psi.PsiDocumentManager;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.undoRedo.CommandProcessor;

public class SearchAgainAction extends AnAction implements DumbAware {
  public SearchAgainAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final FileEditor editor = e.getData(PlatformDataKeys.FILE_EDITOR);
    if (editor == null || project == null) return;
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(project, () -> {
                                      PsiDocumentManager.getInstance(project).commitAllDocuments();
                                      IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
                                      if (FindManager.getInstance(project).findNextUsageInEditor(editor)) {
                                        return;
                                      }

                                      FindUtil.searchAgain(project, editor, e.getDataContext());
                                    },
                                    IdeBundle.message("command.find.next"),
                                    null
    );
  }

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    FileEditor editor = event.getData(PlatformDataKeys.FILE_EDITOR);
    presentation.setEnabled(editor instanceof TextEditor);
  }
}
