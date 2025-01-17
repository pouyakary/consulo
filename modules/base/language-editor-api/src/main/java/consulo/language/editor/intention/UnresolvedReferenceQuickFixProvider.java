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
package consulo.language.editor.intention;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiReference;
import consulo.project.DumbService;
import consulo.util.lang.reflect.ReflectionUtil;

import jakarta.annotation.Nonnull;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class UnresolvedReferenceQuickFixProvider<T extends PsiReference> {
  private static final ExtensionPointName<UnresolvedReferenceQuickFixProvider> EXTENSION_NAME = ExtensionPointName.create(UnresolvedReferenceQuickFixProvider.class);

  @RequiredReadAction
  public static <T extends PsiReference> void registerReferenceFixes(T ref, QuickFixActionRegistrar registrar) {

    final boolean dumb = DumbService.getInstance(ref.getElement().getProject()).isDumb();
    Class<? extends PsiReference> referenceClass = ref.getClass();
    for (UnresolvedReferenceQuickFixProvider each : EXTENSION_NAME.getExtensionList()) {
      if (dumb && !DumbService.isDumbAware(each)) {
        continue;
      }
      if (ReflectionUtil.isAssignable(each.getReferenceClass(), referenceClass)) {
        each.registerFixes(ref, registrar);
      }
    }
  }

  public abstract void registerFixes(T ref, QuickFixActionRegistrar registrar);

  @Nonnull
  public abstract Class<T> getReferenceClass();
}
