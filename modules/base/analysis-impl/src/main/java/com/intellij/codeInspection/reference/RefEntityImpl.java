/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.util.BitUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class RefEntityImpl implements RefEntity, WritableRefEntity {
  private volatile WritableRefEntity myOwner;
  protected List<RefEntity> myChildren;  // guarded by this
  private final String myName;
  private Map<Key, Object> myUserMap;    // guarded by this
  protected long myFlags; // guarded by this
  protected final RefManagerImpl myManager;

  protected RefEntityImpl(@Nonnull String name, @Nonnull RefManager manager) {
    myManager = (RefManagerImpl)manager;
    myName = myManager.internName(name);
  }

  @Nonnull
  @Override
  public String getName() {
    return myName;
  }

  @Nonnull
  @Override
  public String getQualifiedName() {
    return myName;
  }

  @Nonnull
  @Override
  public synchronized List<RefEntity> getChildren() {
    return ObjectUtils.notNull(myChildren, ContainerUtil.emptyList());
  }

  @Override
  public WritableRefEntity getOwner() {
    return myOwner;
  }

  @Override
  public void setOwner(@Nullable final WritableRefEntity owner) {
    myOwner = owner;
  }

  @Override
  public synchronized void add(@Nonnull final RefEntity child) {
    List<RefEntity> children = myChildren;
    if (children == null) {
      myChildren = children = new ArrayList<>(1);
    }
    children.add(child);
    ((RefEntityImpl)child).setOwner(this);
  }

  @Override
  public synchronized void removeChild(@Nonnull final RefEntity child) {
    if (myChildren != null) {
      myChildren.remove(child);
      ((WritableRefEntity)child).setOwner(null);
    }
  }

  public String toString() {
    return getName();
  }

  @Override
  @Nullable
  public <T> T getUserData(@Nonnull Key<T> key) {
    synchronized (this) {
      if (myUserMap == null) return null;
      //noinspection unchecked
      return (T)myUserMap.get(key);
    }
  }

  @Override
  public void accept(@Nonnull final RefVisitor refVisitor) {
    ApplicationManager.getApplication().runReadAction(() -> refVisitor.visitElement(this));
  }

  @Override
  public <T> void putUserData(@Nonnull Key<T> key, T value) {
    synchronized (this) {
      Map<Key, Object> userMap = myUserMap;
      if (userMap == null) {
        if (value == null) return;
        myUserMap = userMap = new THashMap<>();
      }
      if (value != null) {
        userMap.put(key, value);
      }
      else {
        userMap.remove(key);
        if (userMap.isEmpty()) {
          myUserMap = null;
        }
      }
    }
  }

  public synchronized boolean checkFlag(long mask) {
    return BitUtil.isSet(myFlags, mask);
  }

  public synchronized void setFlag(final boolean value, final long mask) {
    myFlags = BitUtil.set(myFlags, mask, value);
  }

  @Override
  public String getExternalName() {
    return myName;
  }

  @Nonnull
  @Override
  public RefManagerImpl getRefManager() {
    return myManager;
  }
}
