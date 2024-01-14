// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.ide.util;

import consulo.annotation.component.ServiceImpl;
import consulo.ide.impl.idea.ide.impl.ProjectViewSelectInTarget;
import consulo.ide.impl.idea.ide.projectView.impl.ProjectViewPaneImpl;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNavigationSupport;
import consulo.language.psi.util.EditSourceUtil;
import consulo.navigation.Navigatable;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

/**
 * @author yole
 */
@Singleton
@ServiceImpl
public class PsiNavigationSupportImpl extends PsiNavigationSupport {
  @Nullable
  @Override
  public Navigatable getDescriptor(@Nonnull PsiElement element) {
    return EditSourceUtil.getDescriptor(element);
  }

  @Nonnull
  @Override
  public Navigatable createNavigatable(@Nonnull Project project, @Nonnull VirtualFile vFile, int offset) {
    OpenFileDescriptorFactory factory = OpenFileDescriptorFactory.getInstance(project);
    return factory.newBuilder(vFile).offset(offset).build();
  }

  @Override
  public boolean canNavigate(@Nonnull PsiElement element) {
    return EditSourceUtil.canNavigate(element);
  }

  @Override
  public void navigateToDirectory(@Nonnull PsiDirectory psiDirectory, boolean requestFocus) {
    ProjectViewSelectInTarget.select(psiDirectory.getProject(),
                                     this,
                                     ProjectViewPaneImpl.ID,
                                     null,
                                     psiDirectory.getVirtualFile(),
                                     requestFocus);
  }
}
