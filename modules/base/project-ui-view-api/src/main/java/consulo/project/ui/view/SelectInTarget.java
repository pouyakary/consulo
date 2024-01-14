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
package consulo.project.ui.view;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionList;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.concurrent.CompletableFuture;

@ExtensionAPI(ComponentScope.PROJECT)
public interface SelectInTarget {
  ExtensionList<SelectInTarget, Project> EP_NAME = ExtensionList.of(SelectInTarget.class);

  String toString();

  /**
   * This should be called in an read action
   */
  boolean canSelect(SelectInContext context);

  void selectIn(SelectInContext context, final boolean requestFocus);

  @Nonnull
  default CompletableFuture<?> selectInAsync(SelectInContext context, final boolean requestFocus) {
    selectIn(context, requestFocus);
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Tool window this target is supposed to select in
   */
  @Nullable
  String getToolWindowId();

  /**
   * aux view id specific for tool window, e.g. Project/Packages/J2EE tab inside project View
   */
  @Nullable
  String getMinorViewId();

  /**
   * Weight is used to provide an order in SelectIn popup. Lesser weights come first.
   *
   * @return weight of this particular target.
   */
  float getWeight();
}
