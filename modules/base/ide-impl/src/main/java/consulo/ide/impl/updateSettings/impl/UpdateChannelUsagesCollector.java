package consulo.ide.impl.updateSettings.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.ide.impl.idea.internal.statistic.CollectUsagesException;
import consulo.ide.impl.idea.internal.statistic.UsagesCollector;
import consulo.ide.impl.idea.internal.statistic.beans.UsageDescriptor;
import consulo.project.Project;
import consulo.ide.impl.updateSettings.UpdateChannel;
import consulo.ide.impl.updateSettings.UpdateSettings;
import jakarta.inject.Inject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * @author VISTALL
 * @since 2020-05-31
 */
@ExtensionImpl
public class UpdateChannelUsagesCollector extends UsagesCollector {
  private final UpdateSettings myUpdateSettings;

  @Inject
  public UpdateChannelUsagesCollector(UpdateSettings updateSettings) {
    myUpdateSettings = updateSettings;
  }

  @Nonnull
  @Override
  public Set<UsageDescriptor> getUsages(@Nullable Project project) throws CollectUsagesException {
    UpdateChannel channel = myUpdateSettings.getChannel();
    return Collections.singleton(new UsageDescriptor(channel.name(), 1));
  }

  @Nonnull
  @Override
  public String getGroupId() {
    return "consulo.platform.base:update.channel";
  }
}
