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

package com.intellij.ide.hierarchy.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.LanguageTypeHierarchy;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.mustbe.consulo.RequiredDispatchThread;

public final class BrowseTypeHierarchyAction extends BrowseHierarchyActionBase {
  public BrowseTypeHierarchyAction() {
    super(LanguageTypeHierarchy.INSTANCE);
  }

  @RequiredDispatchThread
  @Override
  public final void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
      presentation.setText(IdeBundle.message("action.browse.type.hierarchy"));
    }
    super.update(event);
  }
}