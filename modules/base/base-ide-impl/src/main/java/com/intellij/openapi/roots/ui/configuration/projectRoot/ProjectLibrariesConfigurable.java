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
package com.intellij.openapi.roots.ui.configuration.projectRoot;

import consulo.project.Project;
import consulo.project.ProjectBundle;
import consulo.content.library.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import consulo.configurable.internal.ConfigurableWeight;
import consulo.roots.ui.configuration.ProjectConfigurableWeights;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;

@Singleton
public class ProjectLibrariesConfigurable extends BaseLibrariesConfigurable implements ConfigurableWeight {
  public static final String ID = "project.libraries";

  @Inject
  public ProjectLibrariesConfigurable(final Project project) {
    super(project);
    myLevel = LibraryTablesRegistrar.PROJECT_LEVEL;
  }

  @Override
  protected String getComponentStateKey() {
    return "ProjectLibrariesConfigurable.UI";
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Libraries";
  }

  @Override
  @Nonnull
  public String getId() {
    return ID;
  }

  @Override
  public LibraryTableModifiableModelProvider getModelProvider() {
    return getLibrariesConfigurator().getProjectLibrariesProvider();
  }

  @Override
  public LibraryTablePresentation getLibraryTablePresentation() {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getPresentation();
  }

  @Override
  protected String getAddText() {
    return ProjectBundle.message("add.new.project.library.text");
  }

  @Override
  public int getConfigurableWeight() {
    return ProjectConfigurableWeights.LIBRARIES;
  }
}
