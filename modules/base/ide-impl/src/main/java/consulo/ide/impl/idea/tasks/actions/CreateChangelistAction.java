/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package consulo.ide.impl.idea.tasks.actions;

import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.awt.Messages;
import consulo.task.LocalTask;
import consulo.task.TaskManager;
import consulo.ide.impl.idea.tasks.impl.TaskManagerImpl;
import consulo.task.util.TaskUtil;

/**
 * @author Dmitry Avdeev
 */
public class CreateChangelistAction extends BaseTaskAction {

  @Override
  public void update(AnActionEvent event) {
    super.update(event);
    if (event.getPresentation().isEnabled()) {
      TaskManager manager = getTaskManager(event);
      Presentation presentation = event.getPresentation();

      if (manager == null || !manager.isVcsEnabled()) {
        presentation.setText(getTemplatePresentation().getText());
        presentation.setEnabled(false);
      }
      else {
        presentation.setEnabled(true);
        if (manager.getActiveTask().getChangeLists().size() == 0) {
          presentation.setText("Create changelist for '" + TaskUtil.getTrimmedSummary(manager.getActiveTask()) + "'");
        }
        else {
          presentation.setText("Add changelist for '" + TaskUtil.getTrimmedSummary(manager.getActiveTask()) + "'");
        }
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    TaskManagerImpl manager = (TaskManagerImpl)getTaskManager(e);
    assert manager != null;
    LocalTask activeTask = manager.getActiveTask();
    String name =
      Messages.showInputDialog(getProject(e), "Changelist name:", "Create Changelist", null, manager.getChangelistName(activeTask), null);
    if (name != null) {
      manager.createChangeList(activeTask, name);
    }
  }
}
