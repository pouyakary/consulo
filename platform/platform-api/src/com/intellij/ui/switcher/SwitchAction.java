/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.switcher;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public abstract class SwitchAction extends AnAction implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    SwitchingSession session = getSession(e);
    e.getPresentation().setEnabled((session != null && !session.isFinished()) || getProvider(e) != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    SwitchingSession session = getSession(e);
    if (session == null || session.isFinished()) {
      SwitchProvider provider = getProvider(e);
      session = new SwitchingSession(provider, e);
      initSession(e, session);
    }

    move(session);
  }

  private SwitchProvider getProvider(AnActionEvent e) {
    return e.getData(SwitchProvider.KEY);
  }

  private SwitchingSession getSession(AnActionEvent e) {
    return getManager(e).getSession();
  }

  private SwitchManager getManager(AnActionEvent e) {
    Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    return SwitchManager.getInstance(project);
  }

  private void initSession(AnActionEvent e, SwitchingSession session) {
    getManager(e).initSession(session);    
  }

  protected abstract void move(SwitchingSession session);

  public static class Up extends SwitchAction {
    @Override
    protected void move(SwitchingSession session) {
      session.up();
    }
  }

  public static class Down extends SwitchAction {
    @Override
    protected void move(SwitchingSession session) {
      session.down();
    }
  }

  public static class Left extends SwitchAction {
    @Override
    protected void move(SwitchingSession session) {
      session.left();
    }
  }

  public static class Right extends SwitchAction {
    @Override
    protected void move(SwitchingSession session) {
      session.right();
    }
  }

}
