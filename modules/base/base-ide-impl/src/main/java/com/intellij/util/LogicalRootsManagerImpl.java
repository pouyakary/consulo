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

package com.intellij.util;

import consulo.module.ProjectTopics;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import consulo.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import consulo.module.layer.event.ModuleRootEvent;
import consulo.module.ModuleRootManager;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VfsUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import consulo.component.messagebus.MessageBusConnection;
import consulo.roots.ContentFolderScopes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * @author spleaner
 */
@Singleton
public class LogicalRootsManagerImpl extends LogicalRootsManager {
  private Map<Module, MultiValuesMap<LogicalRootType, LogicalRoot>> myRoots = null;
  private final MultiValuesMap<LogicalRootType, NotNullFunction> myProviders = new MultiValuesMap<LogicalRootType, NotNullFunction>();
  private final MultiValuesMap<FileType, LogicalRootType> myFileTypes2RootTypes = new MultiValuesMap<FileType, LogicalRootType>();
  private final ModuleManager myModuleManager;
  private final Project myProject;

  @Inject
  public LogicalRootsManagerImpl(final ModuleManager moduleManager, final Project project) {
    myModuleManager = moduleManager;
    myProject = project;

    final MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(LOGICAL_ROOTS, new LogicalRootListener() {
      @Override
      public void logicalRootsChanged() {
        clear();
        //updateCache(moduleManager);
      }
    });
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        project.getMessageBus().syncPublisher(LOGICAL_ROOTS).logicalRootsChanged();
      }
    });
    registerLogicalRootProvider(LogicalRootType.SOURCE_ROOT, new NotNullFunction<Module, List<VirtualFileLogicalRoot>>() {
      @Override
      @Nonnull
      public List<VirtualFileLogicalRoot> fun(final Module module) {
        return ContainerUtil.map2List(ModuleRootManager.getInstance(module).getContentFolderFiles(ContentFolderScopes.productionAndTest()), new Function<VirtualFile, VirtualFileLogicalRoot>() {
          @Override
          public VirtualFileLogicalRoot fun(final VirtualFile s) {
            return new VirtualFileLogicalRoot(s);
          }
        });
      }
    });
  }

  private synchronized void clear() {
    myRoots = null;
  }

  private synchronized  Map<Module, MultiValuesMap<LogicalRootType, LogicalRoot>> getRoots(final ModuleManager moduleManager) {
    if (myRoots == null) {
      myRoots = new HashMap<Module, MultiValuesMap<LogicalRootType, LogicalRoot>>();

      final Module[] modules = moduleManager.getModules();
      for (Module module : modules) {
        final MultiValuesMap<LogicalRootType, LogicalRoot> map = new MultiValuesMap<LogicalRootType, LogicalRoot>();
        for (Map.Entry<LogicalRootType, Collection<NotNullFunction>> entry : myProviders.entrySet()) {
          final Collection<NotNullFunction> functions = entry.getValue();
          for (NotNullFunction function : functions) {
            map.putAll(entry.getKey(), (List<LogicalRoot>) function.fun(module));
          }
        }
        myRoots.put(module, map);
      }
    }

    return myRoots;
  }

  @Override
  @Nullable
  public LogicalRoot findLogicalRoot(@Nonnull final VirtualFile file) {
    final Module module = ModuleUtil.findModuleForFile(file, myProject);
    if (module == null) return null;

    LogicalRoot result = null;
    final List<LogicalRoot> list = getLogicalRoots(module);
    for (final LogicalRoot root : list) {
      final VirtualFile rootFile = root.getVirtualFile();
      if (rootFile != null && VfsUtil.isAncestor(rootFile, file, false)) {
        result = root;
        break;
      }
    }

    return result;
  }

  @Override
  public List<LogicalRoot> getLogicalRoots() {
    return ContainerUtil.concat(myModuleManager.getModules(), new Function<Module, Collection<? extends LogicalRoot>>() {
      @Override
      public Collection<? extends LogicalRoot> fun(final Module module) {
        return getLogicalRoots(module);
      }
    });
  }

  @Override
  public List<LogicalRoot> getLogicalRoots(@Nonnull final Module module) {
    final Map<Module, MultiValuesMap<LogicalRootType, LogicalRoot>> roots = getRoots(myModuleManager);
    final MultiValuesMap<LogicalRootType, LogicalRoot> valuesMap = roots.get(module);
    if (valuesMap == null) {
      return Collections.emptyList();
    }
    return new ArrayList<LogicalRoot>(valuesMap.values());
  }

  @Override
  public List<LogicalRoot> getLogicalRootsOfType(@Nonnull final Module module, @Nonnull final LogicalRootType... types) {
    return ContainerUtil.concat(types, new Function<LogicalRootType, Collection<? extends LogicalRoot>>() {
      @Override
      public Collection<? extends LogicalRoot> fun(final LogicalRootType s) {
        return getLogicalRootsOfType(module, s);
      }
    });
  }

  @Override
  public <T extends LogicalRoot> List<T> getLogicalRootsOfType(@Nonnull final Module module, @Nonnull final LogicalRootType<T> type) {
    final Map<Module, MultiValuesMap<LogicalRootType, LogicalRoot>> roots = getRoots(myModuleManager);
    final MultiValuesMap<LogicalRootType, LogicalRoot> map = roots.get(module);
    if (map == null) {
      return Collections.emptyList();
    }

    Collection<LogicalRoot> collection = map.get(type);
    if (collection == null) return Collections.emptyList();
    return new ArrayList<T>((Collection<T>)collection);
  }

  @Override
  @Nonnull
  public LogicalRootType[] getRootTypes(@Nonnull final FileType type) {
    final Collection<LogicalRootType> rootTypes = myFileTypes2RootTypes.get(type);
    if (rootTypes == null) {
      return new LogicalRootType[0];
    }

    return rootTypes.toArray(new LogicalRootType[rootTypes.size()]);
  }

  @Override
  public void registerRootType(@Nonnull final FileType fileType, @Nonnull final LogicalRootType... rootTypes) {
    myFileTypes2RootTypes.putAll(fileType, rootTypes);
  }

  @Override
  public <T extends LogicalRoot> void registerLogicalRootProvider(@Nonnull final LogicalRootType<T> rootType, @Nonnull NotNullFunction<Module, List<T>> provider) {
    myProviders.put(rootType, provider);
    clear();
  }
}
