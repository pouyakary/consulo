// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.ide.actions.runAnything;

import consulo.ide.impl.idea.execution.actions.ChooseRunConfigurationPopup;
import consulo.execution.configuration.ConfigurationType;
import consulo.ide.impl.idea.ide.actions.runAnything.items.RunAnythingItemBase;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public class RunAnythingRunConfigurationItem extends RunAnythingItemBase {
  public static final String RUN_CONFIGURATION_AD_TEXT = RunAnythingUtil.AD_CONTEXT_TEXT + ", " + RunAnythingUtil.AD_DEBUG_TEXT;
  private final ChooseRunConfigurationPopup.ItemWrapper myWrapper;

  public RunAnythingRunConfigurationItem(@Nonnull ChooseRunConfigurationPopup.ItemWrapper wrapper, @Nullable Image icon) {
    super(wrapper.getText(), icon);
    myWrapper = wrapper;
  }

  @Nullable
  @Override
  public String getDescription() {
    ConfigurationType type = myWrapper.getType();
    return type == null ? null : type.getConfigurationTypeDescription().get();
  }
}