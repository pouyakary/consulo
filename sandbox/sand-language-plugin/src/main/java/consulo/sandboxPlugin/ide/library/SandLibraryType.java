/*
 * Copyright 2013-2016 consulo.io
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
package consulo.sandboxPlugin.ide.library;

import consulo.application.AllIcons;
import consulo.project.Project;
import consulo.module.content.layer.ModuleRootModel;
import com.intellij.openapi.roots.libraries.DummyLibraryProperties;
import consulo.content.library.LibraryType;
import consulo.content.library.NewLibraryConfiguration;
import consulo.content.library.PersistentLibraryKind;
import consulo.content.library.ui.LibraryEditorComponent;
import consulo.content.library.ui.LibraryPropertiesEditor;
import consulo.virtualFileSystem.VirtualFile;
import consulo.sandboxPlugin.ide.module.extension.SandModuleExtension;
import consulo.ui.image.Image;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;

/**
 * @author VISTALL
 * @since 20.03.14
 */
public class SandLibraryType extends LibraryType<DummyLibraryProperties> {
  @Inject
  protected SandLibraryType() {
    super(new PersistentLibraryKind<DummyLibraryProperties>("sand") {
      @Nonnull
      @Override
      public DummyLibraryProperties createDefaultProperties() {
        return new DummyLibraryProperties();
      }
    });
  }

  @Override
  public boolean isAvailable(@Nonnull ModuleRootModel moduleRootModel) {
    return moduleRootModel.getExtension(SandModuleExtension.class) != null;
  }

  @Nullable
  @Override
  public String getCreateActionName() {
    return "test";
  }

  @Nullable
  @Override
  public NewLibraryConfiguration createNewLibrary(@Nonnull JComponent parentComponent, @Nullable VirtualFile contextDirectory, @Nonnull Project project) {
    return null;
  }

  @Nullable
  @Override
  public LibraryPropertiesEditor createPropertiesEditor(@Nonnull LibraryEditorComponent<DummyLibraryProperties> editorComponent) {
    return null;
  }

  @Nullable
  @Override
  public Image getIcon() {
    return AllIcons.Nodes.Static;
  }
}
