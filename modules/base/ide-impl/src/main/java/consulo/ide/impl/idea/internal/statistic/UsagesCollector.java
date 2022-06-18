/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.ide.impl.idea.internal.statistic;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Extension;
import consulo.ide.impl.idea.internal.statistic.beans.UsageDescriptor;
import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

@Extension(ComponentScope.APPLICATION)
public abstract class UsagesCollector {
  public static ExtensionPointName<UsagesCollector> EP_NAME = ExtensionPointName.create(UsagesCollector.class);

  @Nonnull
  public abstract Set<UsageDescriptor> getUsages(@Nullable Project project) throws CollectUsagesException;

  @Nonnull
  public abstract String getGroupId();
}
