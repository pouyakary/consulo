/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.UnknownRunConfiguration;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.TypeSafeDataProviderAdapter;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;
import consulo.disposer.Disposer;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * User: anna
 * Date: 27-Mar-2006
 */
public class ConfigurationSettingsEditorWrapper extends SettingsEditor<RunnerAndConfigurationSettings> implements BeforeRunStepsPanel.StepsBeforeRunListener {
  public static Key<ConfigurationSettingsEditorWrapper> CONFIGURATION_EDITOR_KEY = Key.create("ConfigurationSettingsEditor");

  private static final String EXPAND_PROPERTY_KEY = "ExpandBeforeRunStepsPanel";

  private final JPanel myBeforeLaunchContainer;
  private BeforeRunStepsPanel myBeforeRunStepsPanel;

  private final ConfigurationSettingsEditor myEditor;
  private final HideableDecorator myDecorator;

  public ConfigurationSettingsEditorWrapper(final RunnerAndConfigurationSettings settings) {
    myEditor = new ConfigurationSettingsEditor(settings);
    Disposer.register(this, myEditor);
    myBeforeRunStepsPanel = new BeforeRunStepsPanel(this);

    myBeforeLaunchContainer = new JPanel(new BorderLayout());
    myDecorator = new HideableDecorator(myBeforeLaunchContainer, "", false) {
      @Override
      protected void on() {
        super.on();
        storeState();
      }

      @Override
      protected void off() {
        super.off();
        storeState();
      }

      private void storeState() {
        PropertiesComponent.getInstance().setValue(EXPAND_PROPERTY_KEY, String.valueOf(isExpanded()));
      }
    };
    myDecorator.setOn(PropertiesComponent.getInstance().getBoolean(EXPAND_PROPERTY_KEY, true));
    myDecorator.setContentComponent(myBeforeRunStepsPanel.getPanel());
    doReset(settings);
  }

  private void doReset(RunnerAndConfigurationSettings settings) {
    final RunConfiguration runConfiguration = settings.getConfiguration();
    myBeforeRunStepsPanel.doReset(settings);
    myBeforeLaunchContainer.setVisible(!(runConfiguration instanceof UnknownRunConfiguration));
  }

  @Override
  @Nonnull
  protected JComponent createEditor() {
    JPanel wholePanel = new JPanel(new VerticalLayout(JBUI.scale(5)));

    wholePanel.add(myEditor.getComponent());
    wholePanel.add(myBeforeLaunchContainer);

    DataManager.registerDataProvider(wholePanel, new TypeSafeDataProviderAdapter(new MyDataProvider()));
    return ScrollPaneFactory.createScrollPane(wholePanel, true);
  }

  @Override
  protected void disposeEditor() {
  }

  @Override
  public void resetEditorFrom(final RunnerAndConfigurationSettings settings) {
    myEditor.resetEditorFrom(settings);
    doReset(settings);
  }

  @Override
  public void applyEditorTo(final RunnerAndConfigurationSettings settings) throws ConfigurationException {
    myEditor.applyEditorTo(settings);
    doApply(settings);
  }

  @Override
  public RunnerAndConfigurationSettings getSnapshot() throws ConfigurationException {
    RunnerAndConfigurationSettings result = myEditor.getSnapshot();
    doApply(result);
    return result;
  }

  private void doApply(final RunnerAndConfigurationSettings settings) {
    final RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());
    runManager.setBeforeRunTasks(runConfiguration, myBeforeRunStepsPanel.getTasks(true), false);
    RunnerAndConfigurationSettings runManagerSettings = runManager.getSettings(runConfiguration);
    if (runManagerSettings != null) {
      runManagerSettings.setEditBeforeRun(myBeforeRunStepsPanel.needEditBeforeRun());
    }
    else {
      settings.setEditBeforeRun(myBeforeRunStepsPanel.needEditBeforeRun());
    }
  }

  public void addBeforeLaunchStep(BeforeRunTask<?> task) {
    myBeforeRunStepsPanel.addTask(task);
  }

  public List<BeforeRunTask> getStepsBeforeLaunch() {
    return Collections.unmodifiableList(myBeforeRunStepsPanel.getTasks(true));
  }

  @Override
  public void fireStepsBeforeRunChanged() {
    fireEditorStateChanged();
  }

  @Override
  public void titleChanged(String title) {
    myDecorator.setTitle(title);
  }

  private class MyDataProvider implements TypeSafeDataProvider {
    @Override
    public void calcData(Key key, DataSink sink) {
      if (CONFIGURATION_EDITOR_KEY == key) {
        sink.put(CONFIGURATION_EDITOR_KEY, ConfigurationSettingsEditorWrapper.this);
      }
    }
  }
}
