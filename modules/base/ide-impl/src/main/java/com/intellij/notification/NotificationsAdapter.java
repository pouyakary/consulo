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
package com.intellij.notification;

import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationDisplayType;
import consulo.project.ui.notification.Notifications;

import javax.annotation.Nonnull;

public class NotificationsAdapter implements Notifications {
  @Override
  public void notify(@Nonnull Notification notification) {
  }

  @Override
  public void register(@Nonnull String groupDisplayName, @Nonnull NotificationDisplayType defaultDisplayType) {
  }

  @Override
  public void register(@Nonnull String groupDisplayName,
                       @Nonnull NotificationDisplayType defaultDisplayType,
                       boolean shouldLog) {
  }

  @Override
  public void register(@Nonnull String groupDisplayName,
                       @Nonnull NotificationDisplayType defaultDisplayType,
                       boolean shouldLog,
                       boolean shouldReadAloud) {
  }
}
