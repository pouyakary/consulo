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
package consulo.ide.impl.packaging.impl.elements;

import consulo.annotation.component.ExtensionImpl;
import consulo.annotation.component.Orderable;
import consulo.application.AllIcons;
import consulo.compiler.CompilerBundle;
import consulo.ui.ex.awt.Messages;
import consulo.ide.impl.idea.openapi.util.io.FileUtil;
import consulo.compiler.artifact.element.CompositePackagingElement;
import consulo.compiler.artifact.element.CompositePackagingElementType;
import consulo.compiler.artifact.element.PackagingElement;
import consulo.compiler.artifact.element.PackagingElementFactory;
import consulo.ide.impl.idea.packaging.impl.elements.FilePathValidator;
import consulo.ide.impl.idea.packaging.impl.elements.PackagingElementFactoryImpl;
import consulo.compiler.artifact.ui.ArtifactEditorContext;
import consulo.ide.impl.idea.util.PathUtil;
import consulo.project.Project;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 16:04/18.06.13
 */
@ExtensionImpl
@Orderable(id = "zip-archive-element")
public class ZipArchiveElementType extends CompositePackagingElementType<ZipArchivePackagingElement> {
  public static ZipArchiveElementType getInstance() {
    return getInstance(ZipArchiveElementType.class);
  }

  public ZipArchiveElementType() {
    super("zip-archive", CompilerBundle.message("element.type.name.zip.archive"));
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return AllIcons.Nodes.PpJar;
  }

  @Nonnull
  @Override
  public ZipArchivePackagingElement createEmpty(@Nonnull Project project) {
    return new ZipArchivePackagingElement();
  }

  @Override
  public CompositePackagingElement<?> createComposite(CompositePackagingElement<?> parent,
                                                      @Nullable String baseName,
                                                      @Nonnull ArtifactEditorContext context) {
    final String initialValue = PackagingElementFactoryImpl.suggestFileName(parent, baseName != null ? baseName : "archive", ".zip");
    String path =
      Messages.showInputDialog(context.getProject(), "Enter archive name: ", "New Archive", null, initialValue, new FilePathValidator());
    if (path == null) {
      return null;
    }
    path = FileUtil.toSystemIndependentName(path);
    final String parentPath = PathUtil.getParentPath(path);
    final String fileName = PathUtil.getFileName(path);
    final PackagingElement<?> element = new ZipArchivePackagingElement(fileName);
    return (CompositePackagingElement<?>)PackagingElementFactory.getInstance(context.getProject()).createParentDirectories(parentPath, element);
  }
}
