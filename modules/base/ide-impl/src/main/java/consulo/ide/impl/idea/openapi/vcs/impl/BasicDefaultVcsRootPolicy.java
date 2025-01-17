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
package consulo.ide.impl.idea.openapi.vcs.impl;

import consulo.project.Project;
import consulo.ide.impl.idea.openapi.vcs.impl.projectlevelman.NewMappings;
import consulo.ide.impl.idea.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class BasicDefaultVcsRootPolicy extends DefaultVcsRootPolicy {
  private final Project myProject;
  private final VirtualFile myBaseDir;

  public BasicDefaultVcsRootPolicy(Project project) {
    myProject = project;
    myBaseDir = project.getBaseDir();
  }

  @Nonnull
  public Collection<VirtualFile> getDefaultVcsRoots(@Nonnull NewMappings mappingList, @Nonnull String vcsName) {
    List<VirtualFile> result = ContainerUtil.newArrayList();
    final VirtualFile baseDir = myBaseDir;
    if (baseDir != null && vcsName.equals(mappingList.getVcsFor(baseDir))) {
      result.add(baseDir);
    }
    return result;
  }

  public boolean matchesDefaultMapping(@Nonnull final VirtualFile file, final Object matchContext) {
    return VfsUtil.isAncestor(myBaseDir, file, false);
  }

  @jakarta.annotation.Nullable
  public Object getMatchContext(final VirtualFile file) {
    return null;
  }

  @Nullable
  public VirtualFile getVcsRootFor(@Nonnull final VirtualFile file) {
    return myBaseDir;
  }

  @Nonnull
  public Collection<VirtualFile> getDirtyRoots() {
    return Collections.singletonList(myBaseDir);
  }

}
