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

package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;

import javax.swing.*;

/**
 * User: anna
 * Date: 17-May-2006
 */
public class HeadlessLafManagerImpl extends LafManager {
  @Override
  public UIManager.LookAndFeelInfo[] getInstalledLookAndFeels() {
    return new UIManager.LookAndFeelInfo[0];
  }

  @Override
  public UIManager.LookAndFeelInfo getCurrentLookAndFeel() {
    return null;
  }

  @Override
  public boolean checkLookAndFeel(UIManager.LookAndFeelInfo lookAndFeelInfo) {
    return true;
  }

  @Override
  public void setCurrentLookAndFeel(UIManager.LookAndFeelInfo lookAndFeelInfo) {
  }

  @Override
  public void updateUI() {
  }

  @Override
  public void repaintUI() {
  }

  @Override
  public void addLafManagerListener(LafManagerListener l) {
  }

  @Override
  public void removeLafManagerListener(LafManagerListener l) {
  }
}
