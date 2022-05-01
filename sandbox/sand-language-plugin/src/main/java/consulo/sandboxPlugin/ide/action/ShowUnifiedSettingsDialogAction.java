/*
 * Copyright 2013-2021 consulo.io
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
package consulo.sandboxPlugin.ide.action;

import consulo.ui.ex.action.AnActionEvent;
import consulo.language.editor.CommonDataKeys;
import consulo.project.internal.DefaultProjectFactory;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ide.impl.options.impl.UnifiedShowSettingsUtil;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 15/07/2021
 */
public class ShowUnifiedSettingsDialogAction extends DumbAwareAction {
  private final DefaultProjectFactory myDefaultProjectFactory;

  public ShowUnifiedSettingsDialogAction(DefaultProjectFactory defaultProjectFactory) {
    myDefaultProjectFactory = defaultProjectFactory;
  }

  @RequiredUIAccess
  @Override
  public void actionPerformed(@Nonnull AnActionEvent e) {
    UnifiedShowSettingsUtil unifiedShowSettingsUtil = new UnifiedShowSettingsUtil(myDefaultProjectFactory);

    unifiedShowSettingsUtil.showSettingsDialog(e.getData(CommonDataKeys.PROJECT));
  }
}
