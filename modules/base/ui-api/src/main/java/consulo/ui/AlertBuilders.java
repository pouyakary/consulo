/*
 * Copyright 2013-2018 consulo.io
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
package consulo.ui;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 6/9/18
 */
public class AlertBuilders {
  @Nonnull
  public static AlertBuilder<Void> notify(int button) {
    return AlertBuilder.<Void>create().button(button, () -> null);
  }

  @Nonnull
  public static AlertBuilder<Boolean> okCancel() {
    return AlertBuilder.<Boolean>create().button(AlertBuilder.OK, () -> Boolean.TRUE).markDefault().button(AlertBuilder.CANCEL, () -> Boolean.FALSE);
  }

  @Nonnull
  public static AlertBuilder<Boolean> yesNo() {
    return AlertBuilder.<Boolean>create().button(AlertBuilder.YES, () -> Boolean.TRUE).markDefault().button(AlertBuilder.NO, () -> Boolean.FALSE);
  }
}
