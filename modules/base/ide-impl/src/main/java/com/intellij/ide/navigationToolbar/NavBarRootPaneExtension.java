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

/*
 * User: anna
 * Date: 12-Nov-2007
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.ide.navigationToolbar.ui.NavBarUIManager;
import consulo.application.ui.UISettings;
import consulo.application.ui.event.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.ui.customization.CustomisedActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import consulo.project.Project;
import consulo.project.ui.wm.IdeFrame;
import consulo.project.ui.wm.IdeRootPaneNorthExtension;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBSwingUtilities;
import consulo.application.ui.awt.JBUI;
import consulo.application.ui.awt.UIUtil;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.awt.TargetAWT;
import consulo.disposer.Disposer;
import consulo.ui.ex.action.*;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class NavBarRootPaneExtension extends IdeRootPaneNorthExtension {
  private JComponent myWrapperPanel;
  @NonNls
  public static final String NAV_BAR = "NavBar";
  private Project myProject;
  private NavBarPanel myNavigationBar;
  private JPanel myRunPanel;
  private final boolean myNavToolbarGroupExist;
  private JScrollPane myScrollPane;

  @Inject
  public NavBarRootPaneExtension(Project project) {
    myProject = project;

    myProject.getMessageBus().connect().subscribe(UISettingsListener.TOPIC, uiSettings -> toggleRunPanel(!uiSettings.getShowMainToolbar() && uiSettings.getShowNavigationBar() && !uiSettings.getPresentationMode()));

    myNavToolbarGroupExist = runToolbarExists();

    Disposer.register(myProject, this);
  }

  @Override
  public void revalidate() {
    final UISettings settings = UISettings.getInstance();
    if (!settings.getShowMainToolbar() && settings.getShowNavigationBar() && !UISettings.getInstance().getPresentationMode()) {
      toggleRunPanel(false);
      toggleRunPanel(true);
    }
  }

  @Override
  public IdeRootPaneNorthExtension copy() {
    return new NavBarRootPaneExtension(myProject);
  }

  public boolean isMainToolbarVisible() {
    return !UISettings.getInstance().getPresentationMode() && (UISettings.getInstance().getShowMainToolbar() || !myNavToolbarGroupExist);
  }

  public static boolean runToolbarExists() {
    final AnAction correctedAction = CustomActionsSchema.getInstance().getCorrectedAction("NavBarToolBar");
    return correctedAction instanceof DefaultActionGroup && ((DefaultActionGroup)correctedAction).getChildrenCount() > 0 ||
           correctedAction instanceof CustomisedActionGroup && ((CustomisedActionGroup)correctedAction).getFirstAction() != null;
  }

  @Override
  public JComponent getComponent() {
    if (myWrapperPanel == null) {
      myWrapperPanel = new NavBarWrapperPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          NavBarUIManager.getUI().doPaintWrapperPanel((Graphics2D)g, getBounds(), isMainToolbarVisible());
        }

        @Override
        public Insets getInsets() {
          return NavBarUIManager.getUI().getWrapperPanelInsets(super.getInsets());
        }
      };
      myWrapperPanel.add(buildNavBarPanel(), BorderLayout.CENTER);
      toggleRunPanel(!UISettings.getInstance().getShowMainToolbar() && !UISettings.getInstance().getPresentationMode());
    }

    return myWrapperPanel;
  }

  public static class NavBarWrapperPanel extends JPanel {
    public NavBarWrapperPanel(LayoutManager layout) {
      super(layout);
      setName("navbar");
    }

    @Override
    protected Graphics getComponentGraphics(Graphics graphics) {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(graphics));
    }
  }

  private static void alignVertically(Container container) {
    if (container.getComponentCount() == 1) {
      Component c = container.getComponent(0);
      Insets insets = container.getInsets();
      Dimension d = c.getPreferredSize();
      Rectangle r = container.getBounds();
      c.setBounds(insets.left, (r.height - d.height - insets.top - insets.bottom) / 2 + insets.top, r.width - insets.left - insets.right, d.height);
    }
  }

  private void toggleRunPanel(final boolean show) {
    if (show && myRunPanel == null && runToolbarExists()) {
      final ActionManager manager = ActionManager.getInstance();
      AnAction toolbarRunGroup = CustomActionsSchema.getInstance().getCorrectedAction("NavBarToolBar");
      if (toolbarRunGroup instanceof ActionGroup) {
        final boolean needGap = isNeedGap(toolbarRunGroup);
        final ActionToolbar actionToolbar = manager.createActionToolbar(ActionPlaces.NAVIGATION_BAR_TOOLBAR, (ActionGroup)toolbarRunGroup, true);
        actionToolbar.setTargetComponent(null);
        final JComponent component = actionToolbar.getComponent();
        myRunPanel = new JPanel(new BorderLayout()) {
          @Override
          public void doLayout() {
            alignVertically(this);
          }
        };
        myRunPanel.setOpaque(false);
        myRunPanel.add(component, BorderLayout.CENTER);
        myRunPanel.setBorder(JBUI.Borders.empty(0, needGap ? 5 : 1, 0, 0));
        myWrapperPanel.add(myRunPanel, BorderLayout.EAST);
      }
    }
    else if (!show && myRunPanel != null) {
      myWrapperPanel.remove(myRunPanel);
      myRunPanel = null;
    }
  }

  private boolean isUndocked() {
    consulo.ui.Window uiWindow = TargetAWT.from(SwingUtilities.getWindowAncestor(myWrapperPanel));
    return (uiWindow != null && !(uiWindow.getUserData(IdeFrame.KEY) instanceof IdeFrameEx)) || !UISettings.getInstance().getShowMainToolbar() || !UISettings.getInstance().getPresentationMode();
  }

  private static boolean isNeedGap(final AnAction group) {
    final AnAction firstAction = getFirstAction(group);
    return firstAction instanceof ComboBoxAction;
  }

  @Nullable
  private static AnAction getFirstAction(final AnAction group) {
    if (group instanceof DefaultActionGroup) {
      AnAction firstAction = null;
      for (final AnAction action : ((DefaultActionGroup)group).getChildActionsOrStubs()) {
        if (action instanceof DefaultActionGroup) {
          firstAction = getFirstAction(action);
        }
        else if (action instanceof AnSeparator || action instanceof ActionGroup) {
          continue;
        }
        else {
          firstAction = action;
          break;
        }

        if (firstAction != null) break;
      }

      return firstAction;
    }
    if (group instanceof CustomisedActionGroup) {
      return ((CustomisedActionGroup)group).getFirstAction();
    }
    return null;
  }

  private JComponent buildNavBarPanel() {
    myNavigationBar = new NavBarPanel(myProject, true);
    myWrapperPanel.putClientProperty("NavBarPanel", myNavigationBar);
    myNavigationBar.getModel().setFixedComponent(true);
    myScrollPane = ScrollPaneFactory.createScrollPane(myNavigationBar);

    JPanel panel = new JPanel(new BorderLayout()) {

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        final Component navBar = myScrollPane;
        Insets insets = getInsets();
        Rectangle r = navBar.getBounds();

        Graphics2D g2d = (Graphics2D)g.create();
        g2d.translate(r.x, r.y);

        Rectangle rectangle = new Rectangle(0, 0, r.width + insets.left + insets.right, r.height + insets.top + insets.bottom);
        NavBarUIManager.getUI().doPaintNavBarPanel(g2d, rectangle, isMainToolbarVisible(), isUndocked());
        if (UIUtil.isUnderAquaLookAndFeel() && isUndocked()) {
          Rectangle bounds = getParent().getBounds();
          NavBarUIManager.getUI().doPaintWrapperPanel(g2d, bounds, false);
        }

        g2d.dispose();
      }

      @Override
      public void doLayout() {
        // align vertically
        final Rectangle r = getBounds();
        final Insets insets = getInsets();
        int x = insets.left;
        if (myScrollPane == null) return;
        final Component navBar = myScrollPane;

        final Dimension preferredSize = navBar.getPreferredSize();

        navBar.setBounds(x, (r.height - preferredSize.height) / 2, r.width - insets.left - insets.right, preferredSize.height);
      }

      @Override
      public void updateUI() {
        super.updateUI();
        setOpaque(true);
        if (myScrollPane == null || myNavigationBar == null) return;

        myScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        myScrollPane.setHorizontalScrollBar(null);
        myScrollPane.setBorder(new NavBarBorder());
        myScrollPane.setOpaque(false);
        myScrollPane.getViewport().setOpaque(false);
        myScrollPane.setViewportBorder(null);
        myNavigationBar.setBorder(null);
      }
    };

    panel.add(myScrollPane, BorderLayout.CENTER);
    panel.updateUI();
    return panel;
  }

  @Override
  public void uiSettingsChanged(final UISettings settings) {
    if (myNavigationBar != null) {
      myNavigationBar.updateState(settings.getShowNavigationBar());
      myWrapperPanel.setVisible(settings.getShowNavigationBar() && !UISettings.getInstance().getPresentationMode());

      myWrapperPanel.revalidate();
      myNavigationBar.revalidate();
      myWrapperPanel.repaint();

      if (myWrapperPanel.getComponentCount() > 0) {
        final Component c = myWrapperPanel.getComponent(0);
        if (c instanceof JComponent) ((JComponent)c).setOpaque(false);
      }
    }
  }

  @Override
  @NonNls
  public String getKey() {
    return NAV_BAR;
  }

  @Override
  public void dispose() {
    myWrapperPanel.setVisible(false);
    myWrapperPanel = null;
    myRunPanel = null;
    myNavigationBar = null;
    myScrollPane = null;
    myProject = null;
  }
}
