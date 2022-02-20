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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkPropertyListener;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.ToolbarPanel;
import consulo.colorScheme.EditorColorsManager;
import consulo.ide.ui.OnePixelSplitter;
import consulo.ui.ex.JBSplitter;
import consulo.ui.ex.Splitter;
import consulo.application.ui.awt.SideBorder;
import consulo.application.util.registry.Registry;
import consulo.project.ui.wm.ToolWindow;
import consulo.project.ui.wm.ToolWindowAnchor;
import consulo.project.ui.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.*;
import com.intellij.util.Producer;
import consulo.application.ui.awt.UIUtil;
import consulo.ui.ex.action.*;
import consulo.ui.ex.awt.TargetAWT;
import consulo.dataContext.DataProvider;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;

/**
 * @author yole
 */
public abstract class TestResultsPanel extends JPanel implements Disposable, DataProvider {
  private JScrollPane myLeftPane;
  private JComponent myStatisticsComponent;
  private Splitter myStatisticsSplitter;
  protected final JComponent myConsole;
  protected ToolbarPanel myToolbarPanel;
  private final String mySplitterProportionProperty;
  private final String myStatisticsSplitterProportionProperty;
  private final float mySplitterDefaultProportion;
  protected final AnAction[] myConsoleActions;
  protected final TestConsoleProperties myProperties;
  protected TestStatusLine myStatusLine;
  private JBSplitter mySplitter;

  protected TestResultsPanel(@Nonnull JComponent console, AnAction[] consoleActions, TestConsoleProperties properties,
                             @Nonnull String splitterProportionProperty, float splitterDefaultProportion) {
    super(new BorderLayout(0,1));
    myConsole = console;
    myConsoleActions = consoleActions;
    myProperties = properties;
    mySplitterProportionProperty = splitterProportionProperty;
    mySplitterDefaultProportion = splitterDefaultProportion;
    myStatisticsSplitterProportionProperty = mySplitterProportionProperty + "_Statistics";
    final ToolWindowManagerListener listener = new ToolWindowManagerListener() {
      @Override
      public void stateChanged(ToolWindowManager toolWindowManager) {
        final boolean splitVertically = splitVertically();
        myStatusLine.setPreferredSize(splitVertically);
        mySplitter.setOrientation(splitVertically);
        revalidate();
        repaint();
      }
    };
    properties.getProject().getMessageBus().connect(this).subscribe(ToolWindowManagerListener.TOPIC, listener);
  }

  public void initUI() {
    myLeftPane = ScrollPaneFactory.createScrollPane();
    myLeftPane.putClientProperty(UIUtil.KEEP_BORDER_SIDES, SideBorder.TOP);
    myStatisticsComponent = createStatisticsPanel();
    myStatusLine = createStatusLine();
    JComponent testTreeView = createTestTreeView();
    myToolbarPanel = createToolbarPanel();
    Disposer.register(this, myToolbarPanel);
    boolean splitVertically = splitVertically();
    myStatusLine.setPreferredSize(splitVertically);

    mySplitter = createSplitter(mySplitterProportionProperty,
                                mySplitterDefaultProportion,
                                splitVertically);
    if (mySplitter instanceof OnePixelSplitter) {
      ((OnePixelSplitter)mySplitter).setBlindZone(new Producer<Insets>() {
        @javax.annotation.Nullable
        @Override
        public Insets produce() {
          return new Insets(myToolbarPanel.getHeight(), 0, 0, 0);
        }
      });
    }
    Disposer.register(this, new Disposable(){
      @Override
      public void dispose() {
        remove(mySplitter);
        mySplitter.dispose();
      }
    });
    add(mySplitter, BorderLayout.CENTER);
    final JPanel leftPanel = new JPanel(new BorderLayout());
    leftPanel.add(myLeftPane, BorderLayout.CENTER);
    leftPanel.add(myToolbarPanel, BorderLayout.NORTH);
    mySplitter.setFirstComponent(leftPanel);
    myStatusLine.setMinimumSize(new Dimension(0, myStatusLine.getMinimumSize().height));
    myStatusLine.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
    final JPanel rightPanel = new JPanel(new BorderLayout());
    rightPanel.add(SameHeightPanel.wrap(myStatusLine, myToolbarPanel), BorderLayout.NORTH);
    myStatisticsSplitter = createSplitter(myStatisticsSplitterProportionProperty, 0.5f, false);
    myStatisticsSplitter.setFirstComponent(createOutputTab(myConsole, myConsoleActions));
    if (Registry.is("tests.view.old.statistics.panel")) {
      if (TestConsoleProperties.SHOW_STATISTICS.value(myProperties)) {
        showStatistics();
      }
      myProperties.addListener(TestConsoleProperties.SHOW_STATISTICS, new TestFrameworkPropertyListener<Boolean>() {
        @Override
        public void onChanged(Boolean value) {
          if (value.booleanValue()) {
            showStatistics();
          }
          else {
            myStatisticsSplitter.setSecondComponent(null);
          }
        }
      });
    }
    rightPanel.add(myStatisticsSplitter, BorderLayout.CENTER);
    mySplitter.setSecondComponent(rightPanel);
    testTreeView.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
    setLeftComponent(testTreeView);
  }

  private boolean splitVertically() {
    final String windowId = myProperties.getExecutor().getToolWindowId();
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProperties.getProject()).getToolWindow(windowId);
    boolean splitVertically = false;
    if (toolWindow != null) {
      final ToolWindowAnchor anchor = toolWindow.getAnchor();
      splitVertically = anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT;
    }
    return splitVertically;
  }

  private void showStatistics() {
    myStatisticsSplitter.setSecondComponent(myStatisticsComponent);
  }

  protected abstract JComponent createStatisticsPanel();

  protected ToolbarPanel createToolbarPanel() {
    return new ToolbarPanel(myProperties, this);
  }

  protected TestStatusLine createStatusLine() {
    return new TestStatusLine();
  }

  protected abstract JComponent createTestTreeView();

  @javax.annotation.Nullable
  protected TestTreeView getTreeView() {
    return null;
  }

  @Nullable
  @Override
  public Object getData(@Nonnull Key<?> dataId) {
    final TestTreeView view = getTreeView();
    if (view != null) {
      return view.getData(dataId);
    }
    return null;
  }

  private JComponent createOutputTab(JComponent console,
                                     AnAction[] consoleActions) {
    JPanel outputTab = new JPanel(new BorderLayout());
    console.setFocusable(true);
    final Color editorBackground = TargetAWT.to(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground());
    console.setBorder(new CompoundBorder(IdeBorderFactory.createBorder(SideBorder.RIGHT | SideBorder.TOP),
                                         new SideBorder(editorBackground, SideBorder.LEFT)));
    outputTab.add(console, BorderLayout.CENTER);
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, new DefaultActionGroup(consoleActions), false);
    outputTab.add(toolbar.getComponent(), BorderLayout.EAST);
    return outputTab;
  }

  @Override
  public void dispose() {
  }

  @Nonnull
  protected static JBSplitter createSplitter(@Nonnull String proportionProperty, float defaultProportion, boolean splitVertically) {
    JBSplitter splitter = new OnePixelSplitter(splitVertically, proportionProperty, defaultProportion);
    splitter.setHonorComponentsMinimumSize(true);
    return splitter;
  }

  protected void setLeftComponent(final JComponent component) {
    if (component != myLeftPane.getViewport().getView()) myLeftPane.setViewportView(component);
  }
}
