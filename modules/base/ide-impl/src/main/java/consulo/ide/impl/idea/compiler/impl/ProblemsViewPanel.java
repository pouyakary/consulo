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
package consulo.ide.impl.idea.compiler.impl;

import consulo.ide.impl.compiler.CompilerWorkspaceConfiguration;
import consulo.ide.impl.compiler.HelpID;
import consulo.ide.impl.idea.ide.errorTreeView.NewErrorTreeViewPanelImpl;
import consulo.project.Project;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.IdeActions;

public class ProblemsViewPanel extends NewErrorTreeViewPanelImpl {
  public ProblemsViewPanel(Project project) {
    super(project, HelpID.COMPILER, false, true, null);
    myTree.getEmptyText().setText("No compilation problems found");
  }

  @Override
  public void addActionsAfter(ActionGroup.Builder group) {
    group.add(new CompilerPropertiesAction());
  }

  @Override
  protected void addExtraPopupMenuActions(ActionGroup.Builder group) {
    group.add(new ExcludeFromCompileAction(myProject, this));

    ActionGroup popupGroup = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_COMPILER_ERROR_VIEW_POPUP);
    if (popupGroup != null) {
      for (AnAction action : popupGroup.getChildren(null)) {
        group.add(action);
      }
    }
  }

  @Override
  protected boolean shouldShowFirstErrorInEditor() {
    return CompilerWorkspaceConfiguration.getInstance(myProject).AUTO_SHOW_ERRORS_IN_EDITOR;
  }

  @Override
  protected boolean canHideWarningsOrInfos() {
    return true;
  }
}
