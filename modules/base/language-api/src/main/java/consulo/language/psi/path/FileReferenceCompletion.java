/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package consulo.language.psi.path;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.Service;
import consulo.application.Application;

/**
 * @author yole
 */
@Service(ComponentScope.APPLICATION)
public abstract class FileReferenceCompletion {
  public static FileReferenceCompletion getInstance() {
    return Application.get().getInstance(FileReferenceCompletion.class);
  }

  public abstract Object[] getFileReferenceCompletionVariants(FileReference reference);
}
