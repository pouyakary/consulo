/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import consulo.module.Module;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;

/**
 * @author Konstantin Bulenkov
 */
public class OpenModuleSettingsAction extends EditSourceAction {
  @RequiredUIAccess
  @Override
  public void update(@Nonnull AnActionEvent event) {
    super.update(event);

    if (!isModuleInProjectViewPopup(event)) {
      event.getPresentation().setEnabledAndVisible(false);
    }
  }

  protected static boolean isModuleInProjectViewPopup(@Nonnull AnActionEvent e) {
    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(e.getPlace())) {
      return isModuleInContext(e);
    }
    return false;
  }

  public static boolean isModuleInContext(@Nonnull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Module module = e.getData(LangDataKeys.MODULE);
    if (project != null && module != null) {
      final VirtualFile moduleFolder = e.getData(CommonDataKeys.VIRTUAL_FILE);
      if (moduleFolder == null) {
        return false;
      }
      if (ProjectRootsUtil.isModuleContentRoot(moduleFolder, project) || ProjectRootsUtil.isModuleSourceRoot(moduleFolder, project)) {
        return true;
      }
    }
    return false;
  }
}
