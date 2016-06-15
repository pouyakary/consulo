/*
 * Copyright 2013-2016 must-be.org
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
package com.intellij.idea.starter;

import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.ui.Splash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ApplicationStarter {
  public static final String IDEA_APPLICATION = "idea";

  @Nullable
  protected Splash createSplash(@NotNull String[] args) {
    return null;
  }

  public void createApplication(boolean internal,
                                       boolean isUnitTestMode,
                                       boolean isHeadlessMode,
                                       boolean isCommandline,
                                       String[] args) {
    Splash splash = createSplash(args);

    new ApplicationImpl(internal, isUnitTestMode, isHeadlessMode, isCommandline, IDEA_APPLICATION, splash);
  }

  public void premain(String[] args) {
  }

  public void main(String[] args) {
  }
}
