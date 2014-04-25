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
package com.intellij.core;

import com.intellij.openapi.projectRoots.SdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class CoreSdkTable extends SdkTable {
  private final List<Sdk> mySdks = new ArrayList<Sdk>();

  @Override
  public Sdk findSdk(String name) {
    synchronized (mySdks) {
      for (Sdk jdk : mySdks) {
        if (Comparing.strEqual(name, jdk.getName())) {
          return jdk;
        }
      }
    }
    return null;
  }

  @Override
  public Sdk[] getAllSdks() {
    synchronized (mySdks) {
      return mySdks.toArray(new Sdk[mySdks.size()]);
    }
  }

  @Override
  public List<Sdk> getSdksOfType(SdkTypeId type) {
    List<Sdk> result = new ArrayList<Sdk>();
    synchronized (mySdks) {
      for (Sdk sdk : mySdks) {
        if (sdk.getSdkType() == type) {
          result.add(sdk);
        }
      }
    }
    return result;
  }

  @Override
  public void addSdk(@NotNull Sdk jdk) {
    synchronized (mySdks) {
      mySdks.add(jdk);
    }
  }

  @Override
  public void removeSdk(@NotNull Sdk jdk) {
    synchronized (mySdks) {
      mySdks.remove(jdk);
    }
  }

  @Override
  public void updateSdk(Sdk originalJdk, Sdk modifiedJdk) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SdkTypeId getDefaultSdkType() {
    return CoreSdkType.INSTANCE;
  }

  @Override
  public SdkTypeId getSdkTypeByName(String name) {
    return CoreSdkType.INSTANCE;
  }

  @Nullable
  @Override
  public Sdk findBundleSdkByType(@NotNull Class<? extends SdkType> sdkTypeClass) {
    for (Sdk sdk : mySdks) {
      if(sdk.isBundled()) {
        return sdk;
      }
    }
    return null;
  }

  @Override
  public Sdk createSdk(String name, SdkTypeId sdkType) {
    throw new UnsupportedOperationException();
  }
}
