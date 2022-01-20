/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.safeDelete;

import consulo.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import javax.annotation.Nullable;

import java.util.Collection;

/**
 * User: anna
 * Date: 12/1/11
 */
public abstract class SafeDeleteProcessorDelegateBase implements SafeDeleteProcessorDelegate {
  @Nullable
  public abstract Collection<? extends PsiElement> getElementsToSearch(PsiElement element, @Nullable Module module, Collection<PsiElement> allElementsToDelete);
  @Override
  public Collection<? extends PsiElement> getElementsToSearch(PsiElement element, Collection<PsiElement> allElementsToDelete) {
    return getElementsToSearch(element, null, allElementsToDelete);
  }

  @Nullable
  public UsageView showUsages(UsageInfo[] usages, UsageViewPresentation presentation, UsageViewManager manager, PsiElement[] elements) {
    return null;
  }
}
