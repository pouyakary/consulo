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

package com.intellij.openapi.module.impl;

import com.intellij.openapi.components.ServiceDescriptor;
import com.intellij.openapi.components.impl.ModulePathMacroManager;
import com.intellij.openapi.extensions.impl.ExtensionAreaId;
import consulo.module.Module;
import consulo.project.Project;
import consulo.module.content.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModuleRootManagerImpl;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.pointer.VirtualFilePointer;
import consulo.virtualFileSystem.pointer.VirtualFilePointerManager;
import consulo.component.extension.ExtensionPointId;
import consulo.components.impl.PlatformComponentManagerImpl;
import consulo.container.plugin.PluginDescriptor;
import consulo.container.plugin.PluginIds;
import consulo.container.plugin.PluginListenerDescriptor;
import consulo.injecting.InjectingContainerBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author max
 */
public class ModuleImpl extends PlatformComponentManagerImpl implements ModuleEx {
  private static final ExtensionPointId<ServiceDescriptor> MODULE_SERVICES = ExtensionPointId.of(PluginIds.CONSULO_BASE + ".moduleService");

  @Nonnull
  private String myName;

  @Nullable
  private final VirtualFilePointer myDirVirtualFilePointer;

  public ModuleImpl(@Nonnull String name, @Nullable String dirUrl, @Nonnull Project project) {
    super(project, "Module " + name, ExtensionAreaId.MODULE);
    myName = name;
    myDirVirtualFilePointer = dirUrl == null ? null : VirtualFilePointerManager.getInstance().create(dirUrl, this, null);
  }

  @Override
  protected void bootstrapInjectingContainer(@Nonnull InjectingContainerBuilder builder) {
    super.bootstrapInjectingContainer(builder);

    builder.bind(Module.class).to(this);
    builder.bind(ModulePathMacroManager.class).to(ModulePathMacroManager.class).forceSingleton();
  }

  @Nullable
  @Override
  protected ExtensionPointId<ServiceDescriptor> getServiceExtensionPointName() {
    return MODULE_SERVICES;
  }

  @Nonnull
  @Override
  protected List<PluginListenerDescriptor> getPluginListenerDescriptors(PluginDescriptor pluginDescriptor) {
    return pluginDescriptor.getModuleListeners();
  }

  @Override
  public void rename(String newName) {
    myName = newName;
  }

  @Nullable
  @Override
  public VirtualFile getModuleDir() {
    return myDirVirtualFilePointer == null ? null : myDirVirtualFilePointer.getFile();
  }

  @Nullable
  @Override
  public String getModuleDirPath() {
    return myDirVirtualFilePointer == null ? null : VirtualFileManager.extractPath(myDirVirtualFilePointer.getUrl());
  }

  @Nullable
  @Override
  public String getModuleDirUrl() {
    return myDirVirtualFilePointer == null ? null : myDirVirtualFilePointer.getUrl();
  }

  @Override
  @Nonnull
  public Project getProject() {
    return (Project)myParent;
  }

  @Override
  @Nonnull
  public String getName() {
    return myName;
  }

  @Override
  public void moduleAdded() {
    ModuleRootManagerImpl manager = (ModuleRootManagerImpl)ModuleRootManager.getInstance(this);

    manager.moduleAdded();
  }

  @Override
  public String toString() {
    return "Module: '" + myName + "'";
  }
}
