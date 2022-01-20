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
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import consulo.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import consulo.project.Project;
import consulo.module.ModuleRootManager;
import consulo.virtualFileSystem.VirtualFile;
import javax.annotation.Nonnull;
import consulo.roots.ContentFolderScopes;

import java.util.Arrays;
import java.util.Collection;

public class PackageViewModuleNode extends AbstractModuleNode{
  public PackageViewModuleNode(Project project, Module value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public PackageViewModuleNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (Module)value, viewSettings);
  }

  @Override
  @Nonnull
  public Collection<AbstractTreeNode> getChildren() {
    final Collection<AbstractTreeNode> result = PackageNodeUtil
      .createPackageViewChildrenOnFiles(Arrays.asList(ModuleRootManager.getInstance(getValue()).getContentFolderFiles(ContentFolderScopes.productionAndTest())), myProject, getSettings(),
                                        getValue(), false);
    if (getSettings().isShowLibraryContents()) {
      result.add(new PackageViewLibrariesNode(getProject(), getValue(),getSettings()));
    }
    return result;

  }

  @Override
  public boolean contains(@Nonnull VirtualFile file) {
    Module module = getValue();
    return module != null && !module.isDisposed() &&
           (ModuleUtilCore.moduleContainsFile(module, file, false) || ModuleUtilCore.moduleContainsFile(module, file, true));
  }

  @Override
  public boolean someChildContainsFile(VirtualFile file) {
    return true;
  }
}
