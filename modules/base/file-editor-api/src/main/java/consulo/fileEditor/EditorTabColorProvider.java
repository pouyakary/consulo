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
package consulo.fileEditor;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;

/**
 * @author spleaner
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface EditorTabColorProvider {
  ExtensionPointName<EditorTabColorProvider> EP_NAME = ExtensionPointName.create(EditorTabColorProvider.class);

  @Nullable
  @RequiredReadAction
  Color getEditorTabColor(Project project, VirtualFile file);

  @Nullable
  @RequiredReadAction
  default Color getEditorTabColor(@Nonnull Project project, @Nonnull VirtualFile file, @Nullable FileEditorWindow editorWindow) {
    return getEditorTabColor(project, file);
  }

  @Nullable
  @RequiredReadAction
  default Color getProjectViewColor(@Nonnull Project project, @Nonnull VirtualFile file) {
    return null;
  }
}
