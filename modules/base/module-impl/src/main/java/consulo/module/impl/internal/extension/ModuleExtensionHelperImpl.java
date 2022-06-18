/*
 * Copyright 2013-2016 consulo.io
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
package consulo.module.impl.internal.extension;

import consulo.annotation.component.ServiceImpl;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.module.impl.internal.layer.ModuleExtensionProviderEP;
import consulo.project.Project;
import consulo.module.content.ModuleRootManager;
import consulo.ui.image.Image;
import consulo.util.collection.MultiMap;
import consulo.annotation.access.RequiredReadAction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;

/**
 * @author VISTALL
 * @since 8:00/12.11.13
 */
@Singleton
@ServiceImpl
public class ModuleExtensionHelperImpl implements ModuleExtensionHelper {
  private final Project myProject;
  private MultiMap<Class<? extends ModuleExtension>, ModuleExtension> myExtensions;

  @Inject
  public ModuleExtensionHelperImpl(Project project) {
    myProject = project;
    project.getMessageBus().connect().subscribe(ModuleExtension.CHANGE_TOPIC, (oldExtension, newExtension) -> myExtensions = null);
  }

  @Override
  public boolean hasModuleExtension(@Nonnull Class<? extends ModuleExtension> clazz) {
    checkInit();

    assert myExtensions != null;

    return !getModuleExtensions(clazz).isEmpty();
  }

  @Override
  @Nonnull
  @SuppressWarnings("unchecked")
  public <T extends ModuleExtension<T>> Collection<T> getModuleExtensions(@Nonnull Class<T> clazz) {
    checkInit();

    assert myExtensions != null;

    Collection<ModuleExtension> moduleExtensions = myExtensions.get(clazz);
    if(moduleExtensions.isEmpty()) {
      for (Map.Entry<Class<? extends ModuleExtension>, Collection<ModuleExtension>> entry : myExtensions.entrySet()) {
        Class<? extends ModuleExtension> targetCheck = entry.getKey();

        if(clazz.isAssignableFrom(targetCheck)) {
          myExtensions.put(clazz, moduleExtensions = entry.getValue());
          break;
        }
      }
    }
    return (Collection)moduleExtensions;
  }

  @Nonnull
  @Override
  public String getModuleExtensionName(@Nonnull ModuleExtension<?> moduleExtension) {
    final ModuleExtensionProviderEP provider = ModuleExtensionProviders.findProvider(moduleExtension.getId());
    assert provider != null;
    return provider.getName();
  }

  @Nullable
  @Override
  public Image getModuleExtensionIcon(@Nonnull String extensionId) {
    ModuleExtensionProviderEP provider = ModuleExtensionProviders.findProvider(extensionId);
    return provider == null ? null : provider.getIcon();
  }

  @RequiredReadAction
  private void checkInit() {
    if(myExtensions == null) {
      myExtensions = new MultiMap<>();
      for (Module o : ModuleManager.getInstance(myProject).getModules()) {
        for (ModuleExtension moduleExtension : ModuleRootManager.getInstance(o).getExtensions()) {
          myExtensions.putValue(moduleExtension.getClass(), moduleExtension);
        }
      }
    }
  }
}
