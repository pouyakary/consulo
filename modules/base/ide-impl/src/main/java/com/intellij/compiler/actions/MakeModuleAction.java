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
package com.intellij.compiler.actions;

import com.intellij.openapi.actionSystem.*;
import consulo.compiler.CompilerBundle;
import consulo.compiler.CompilerManager;
import consulo.dataContext.DataContext;
import consulo.module.Module;
import consulo.project.Project;
import javax.annotation.Nonnull;

import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.Presentation;

public class MakeModuleAction extends CompileActionBase {
  private static final Logger LOG = Logger.getInstance(MakeModuleAction.class);

  @RequiredUIAccess
  protected void doAction(DataContext dataContext, Project project) {
    Module[] modules = dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    Module module;
    if (modules == null) {
      module = dataContext.getData(LangDataKeys.MODULE);
      if (module == null) {
        return;
      }
      modules = new Module[]{module};
    }
    try {
      CompilerManager.getInstance(project).make(modules[0].getProject(), modules, null);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @RequiredUIAccess
  public void update(@Nonnull AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    if (!presentation.isEnabled()) {
      return;
    }
    final DataContext dataContext = event.getDataContext();
    final Module module = dataContext.getData(LangDataKeys.MODULE);
    Module[] modules = dataContext.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
    final boolean isEnabled = module != null || modules != null;
    presentation.setEnabled(isEnabled);
    final String actionName = getTemplatePresentation().getTextWithMnemonic();

    String presentationText;
    if (modules != null) {
      String text = actionName;
      for (int i = 0; i < modules.length; i++) {
        if (text.length() > 30) {
          text = CompilerBundle.message("action.make.selected.modules.text");
          break;
        }
        Module toMake = modules[i];
        if (i!=0) {
          text += ",";
        }
        text += " '" + toMake.getName() + "'";
      }
      presentationText = text;
    }
    else if (module != null) {
      presentationText = actionName + " '" + module.getName() + "'";
    }
    else {
      presentationText = actionName;
    }
    presentation.setText(presentationText);
    presentation.setVisible(isEnabled || !ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()));
  }
}
