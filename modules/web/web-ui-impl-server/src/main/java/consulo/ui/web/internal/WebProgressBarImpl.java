/*
 * Copyright 2013-2020 consulo.io
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
package consulo.ui.web.internal;

import consulo.ui.ProgressBar;
import consulo.ui.web.internal.base.VaadinComponentDelegate;
import consulo.ui.web.internal.base.VaadinComponent;
import consulo.web.gwt.shared.ui.state.ProgressBarState;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2020-05-11
 */
public class WebProgressBarImpl extends VaadinComponentDelegate<WebProgressBarImpl.Vaadin> implements ProgressBar {
  public static class Vaadin extends VaadinComponent {
    @Override
    public ProgressBarState getState() {
      return (ProgressBarState)super.getState();
    }
  }

  @Nonnull
  @Override
  public Vaadin createVaadinComponent() {
    return new Vaadin();
  }

  @Override
  public void setIndeterminate(boolean value) {
    getVaadinComponent().getState().indeterminate = value;
  }

  @Override
  public boolean isIndeterminate() {
    return getVaadinComponent().getState().indeterminate;
  }

  @Override
  public void setMinimum(int value) {
    getVaadinComponent().getState().minimum = value;
  }

  @Override
  public void setMaximum(int value) {
    getVaadinComponent().getState().maximum = value;
  }

  @Override
  public void setValue(int value) {
    getVaadinComponent().getState().value = value;
  }
}
