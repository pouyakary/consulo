/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowLayout;
import consulo.ui.RequiredUIAccess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class ToolWindowManagerEx extends ToolWindowManager {
  @RequiredUIAccess
  public abstract void initToolWindow(@NotNull ToolWindowEP bean);

  public static ToolWindowManagerEx getInstanceEx(final Project project) {
    return (ToolWindowManagerEx)getInstance(project);
  }

  public abstract void addToolWindowManagerListener(@NotNull ToolWindowManagerListener l);

  public abstract void addToolWindowManagerListener(@NotNull ToolWindowManagerListener l, @NotNull Disposable parentDisposable);

  public abstract void removeToolWindowManagerListener(@NotNull ToolWindowManagerListener l);

  /**
   * @return <code>ID</code> of tool window that was activated last time.
   */
  @Nullable
  @RequiredUIAccess
  public String getLastActiveToolWindowId() {
    return getLastActiveToolWindowId(null);
  }

  /**
   * @return <code>ID</code> of tool window which was last activated among tool windows satisfying the current condition
   */
  @Nullable
  @RequiredUIAccess
  //TODO [VISTALL] remove JComponent dependency
  public String getLastActiveToolWindowId(@Nullable Condition<JComponent> condition) {
    return null;
  }

  /**
   * @return layout of tool windows.
   */
  @RequiredUIAccess
  @NotNull
  public abstract ToolWindowLayout getLayout();

  public abstract void setLayoutToRestoreLater(ToolWindowLayout layout);

  public abstract ToolWindowLayout getLayoutToRestoreLater();

  /**
   * Copied <code>layout</code> into internal layout and rearranges tool windows.
   */
  @RequiredUIAccess
  public abstract void setLayout(@NotNull ToolWindowLayout layout);

  public abstract void clearSideStack();

  @RequiredUIAccess
  public abstract void hideToolWindow(@NotNull String id, boolean hideSide);

  @RequiredUIAccess
  public void hideToolWindow(final String id, final boolean hideSide, final boolean moveFocus) {
    hideToolWindow(id, hideSide);
  }

  public abstract List<String> getIdsOn(@NotNull ToolWindowAnchor anchor);
}
