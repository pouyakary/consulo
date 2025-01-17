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

package consulo.versionControlSystem;

import consulo.util.io.FileUtil;
import consulo.versionControlSystem.root.VcsRootSettings;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class VcsDirectoryMapping {
  public static final String PROJECT_CONSTANT = "<Project>";
  public static final VcsDirectoryMapping[] EMPTY_ARRAY = new VcsDirectoryMapping[0];
  private String myDirectory;
  // for reliable comparison
  private String mySystemIdependentPath;
  private String myVcs;
  private VcsRootSettings myRootSettings;

  @Nonnull
  public static VcsDirectoryMapping createDefault(@Nonnull String vcs) {
    return new VcsDirectoryMapping("", vcs);
  }

  public VcsDirectoryMapping() {
  }

  public VcsDirectoryMapping(@Nonnull final String directory, final String vcs) {
    setDirectory(directory);
    myVcs = vcs;
  }

  public VcsDirectoryMapping(final String directory, final String vcs, final VcsRootSettings rootSettings) {
    setDirectory(directory);
    myVcs = vcs;
    myRootSettings = rootSettings;
  }

  @Nonnull
  public String getDirectory() {
    return myDirectory;
  }

  /**
   * @return if this mapping denotes "no vcs" aka "&lt;none&gt;".
   */
  public boolean isNoneMapping() {
    return myVcs.isEmpty();
  }

  private void initSystemIndependentPath() {
    mySystemIdependentPath = FileUtil.toSystemIndependentName(myDirectory);
  }

  public String systemIndependentPath() {
    return mySystemIdependentPath;
  }

  public String getVcs() {
    return myVcs;
  }

  public void setVcs(final String vcs) {
    myVcs = vcs;
  }

  public void setDirectory(@Nonnull final String directory) {
    myDirectory = directory;
    initSystemIndependentPath();
  }

  /**
   * Returns the VCS-specific settings for the given mapping.
   *
   * @return VCS-specific settings, or null if none have been defined.
   * @see AbstractVcs#getRootConfigurable(VcsDirectoryMapping)
   */
  @jakarta.annotation.Nullable
  public VcsRootSettings getRootSettings() {
    return myRootSettings;
  }

  /**
   * Sets the VCS-specific settings for the given mapping.
   *
   * @param rootSettings the VCS-specific settings.
   */
  public void setRootSettings(final VcsRootSettings rootSettings) {
    myRootSettings = rootSettings;
  }

  public boolean isDefaultMapping() {
    return myDirectory.length() == 0;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VcsDirectoryMapping mapping = (VcsDirectoryMapping)o;

    if (myDirectory != null ? !myDirectory.equals(mapping.myDirectory) : mapping.myDirectory != null) return false;
    if (myVcs != null ? !myVcs.equals(mapping.myVcs) : mapping.myVcs != null) return false;
    if (myRootSettings != null ? !myRootSettings.equals(mapping.myRootSettings) : mapping.myRootSettings != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myDirectory != null ? myDirectory.hashCode() : 0);
    result = 31 * result + (myVcs != null ? myVcs.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return isDefaultMapping() ? PROJECT_CONSTANT : myDirectory;
  }
}