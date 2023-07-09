/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package consulo.ide.impl.idea.execution.dashboard.tree;

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.ExecutionBundle;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.configuration.ConfigurationType;
import consulo.ide.impl.idea.execution.dashboard.DashboardGroup;
import consulo.ide.impl.idea.execution.dashboard.DashboardGroupingRule;
import consulo.ide.impl.idea.execution.dashboard.DashboardRunConfigurationNode;
import consulo.application.AllIcons;
import consulo.project.ui.view.tree.AbstractTreeNode;
import consulo.fileEditor.structureView.tree.ActionPresentation;
import consulo.fileEditor.structureView.tree.ActionPresentationData;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author konstantin.aleev
 */
@ExtensionImpl
public class ConfigurationTypeDashboardGroupingRule implements DashboardGroupingRule {
  @NonNls private static final String NAME = "ConfigurationTypeDashboardGroupingRule";

  @Override
  @Nonnull
  public String getName() {
    return NAME;
  }

  @Nonnull
  @Override
  public ActionPresentation getPresentation() {
    return new ActionPresentationData(ExecutionBundle.message("run.dashboard.group.by.type.action.name"),
                                      ExecutionBundle.message("run.dashboard.group.by.type.action.name"),
                                      AllIcons.Actions.GroupByFile);
  }

  @Override
  public int getPriority() {
    return Priorities.BY_TYPE;
  }

  @Override
  public boolean isAlwaysEnabled() {
    return false;
  }

  @Override
  public boolean shouldGroupSingleNodes() {
    return true;
  }

  @Nullable
  @Override
  public DashboardGroup getGroup(AbstractTreeNode<?> node) {
    if (node instanceof DashboardRunConfigurationNode) {
      RunnerAndConfigurationSettings configurationSettings = ((DashboardRunConfigurationNode)node).getConfigurationSettings();
      ConfigurationType type = configurationSettings.getType();
      if (type != null) {
        return new DashboardGroupImpl<>(type, type.getDisplayName().get(), type.getIcon());
      }
    }
    return null;
  }
}
