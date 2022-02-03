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
package com.intellij.openapi.vfs.ex.http;

import consulo.disposer.Disposable;
import consulo.virtualFileSystem.BaseVirtualFileSystem;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;

public abstract class HttpFileSystem extends BaseVirtualFileSystem {
  @NonNls public static final String PROTOCOL = StandardFileSystems.HTTP_PROTOCOL;

  public static HttpFileSystem getInstance() {
    return (HttpFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  public abstract boolean isFileDownloaded(@Nonnull VirtualFile file);

  public abstract void addFileListener(@Nonnull HttpVirtualFileListener listener);

  public abstract void addFileListener(@Nonnull HttpVirtualFileListener listener, @Nonnull Disposable parentDisposable);

  public abstract void removeFileListener(@Nonnull HttpVirtualFileListener listener);

}