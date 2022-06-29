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
package consulo.language.psi;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.component.extension.ExtensionPointCacheKey;
import consulo.language.Language;
import consulo.language.extension.ByLanguageValue;
import consulo.language.extension.LanguageExtension;
import consulo.language.extension.LanguageOneToMany;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author peter
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class LanguageSubstitutor implements LanguageExtension {
  private static final ExtensionPointCacheKey<LanguageSubstitutor, ByLanguageValue<List<LanguageSubstitutor>>> KEY =
          ExtensionPointCacheKey.create("LanguageSubstitutor", LanguageOneToMany.build(false));

  @Nonnull
  public static List<LanguageSubstitutor> forLanguage(@Nonnull Language language) {
    return Application.get().getExtensionPoint(LanguageSubstitutor.class).getOrBuildCache(KEY).requiredGet(language);
  }

  @Nullable
  public abstract Language getLanguage(@Nonnull VirtualFile file, @Nonnull Project project);
}
