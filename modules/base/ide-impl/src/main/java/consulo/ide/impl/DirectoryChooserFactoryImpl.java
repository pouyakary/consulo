/*
 * Copyright 2013-2022 consulo.io
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
package consulo.ide.impl;

import consulo.annotation.component.ServiceImpl;
import consulo.ide.impl.idea.ide.util.DirectoryChooser;
import consulo.ide.internal.DirectoryChooserDialog;
import consulo.ide.internal.DirectoryChooserFactory;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import jakarta.inject.Singleton;

/**
 * @author VISTALL
 * @since 10-Jul-22
 */
@Singleton
@ServiceImpl
public class DirectoryChooserFactoryImpl implements DirectoryChooserFactory {
  @Nonnull
  @Override
  public DirectoryChooserDialog create(@Nonnull Project project) {
    return new DirectoryChooser(project);
  }
}
