// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import consulo.application.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationsManagerImpl;
import consulo.ui.ex.action.AnActionEvent;
import consulo.application.ApplicationManager;
import consulo.project.Project;
import consulo.ui.ex.popup.Balloon;
import consulo.util.lang.ref.Ref;
import consulo.project.ui.wm.IconLikeCustomStatusBarWidget;
import consulo.project.ui.wm.IdeFrame;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.BalloonLayout;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.ClickListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.concurrency.EdtExecutorService;
import consulo.application.ui.awt.JBUI;
import consulo.application.ui.awt.UIUtil;
import consulo.disposer.Disposer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;

public final class IdeMessagePanel extends NonOpaquePanel implements MessagePoolListener, IconLikeCustomStatusBarWidget {
  public static final String FATAL_ERROR = "FatalError";

  private final IdeErrorsIcon myIcon;
  private final IdeFrame myFrame;
  private final MessagePool myMessagePool;

  private Balloon myBalloon;
  private IdeErrorsDialog myDialog;
  private boolean myOpeningInProgress;
  private boolean myNotificationPopupAlreadyShown;

  public IdeMessagePanel(@Nullable IdeFrame frame, @Nonnull MessagePool messagePool) {
    super(new BorderLayout());

    myIcon = new IdeErrorsIcon(frame != null);
    myIcon.setVerticalAlignment(SwingConstants.CENTER);
    add(myIcon, BorderLayout.CENTER);
    new ClickListener() {
      @Override
      public boolean onClick(@Nonnull MouseEvent event, int clickCount) {
        openErrorsDialog(null);
        return true;
      }
    }.installOn(myIcon);

    myFrame = frame;

    myMessagePool = messagePool;
    messagePool.addListener(this);

    updateIconAndNotify();
  }

  @Override
  @Nonnull
  public String ID() {
    return FATAL_ERROR;
  }

  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public void dispose() {
    UIUtil.dispose(myIcon);
    myMessagePool.removeListener(this);
  }

  @Override
  public void install(@Nonnull StatusBar statusBar) {
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  /**
   * @deprecated use {@link #openErrorsDialog(LogMessage)}
   */
  @Deprecated
  @SuppressWarnings("SpellCheckingInspection")
  public void openFatals(@Nullable LogMessage message) {
    openErrorsDialog(message);
  }

  public void openErrorsDialog(@Nullable LogMessage message) {
    if (myDialog != null) return;
    if (myOpeningInProgress) return;
    myOpeningInProgress = true;

    new Runnable() {
      @Override
      public void run() {
        if (!isOtherModalWindowActive()) {
          try {
            doOpenErrorsDialog(message);
          }
          finally {
            myOpeningInProgress = false;
          }
        }
        else if (myDialog == null) {
          EdtExecutorService.getScheduledExecutorInstance().schedule(this, 300L, TimeUnit.MILLISECONDS);
        }
      }
    }.run();
  }

  private void doOpenErrorsDialog(@Nullable LogMessage message) {
    Project project = myFrame != null ? myFrame.getProject() : null;
    myDialog = new IdeErrorsDialog(myMessagePool, project, message) {
      @Override
      protected void dispose() {
        super.dispose();
        myDialog = null;
        updateIconAndNotify();
      }

      @Override
      protected void updateOnSubmit() {
        super.updateOnSubmit();
        updateIcon(myMessagePool.getState());
      }
    };
    myDialog.show();
  }

  private void updateIcon(MessagePool.State state) {
    UIUtil.invokeLaterIfNeeded(() -> {
      myIcon.setState(state);
      setVisible(state != MessagePool.State.NoErrors);
    });
  }

  @Override
  public void newEntryAdded() {
    updateIconAndNotify();
  }

  @Override
  public void poolCleared() {
    updateIconAndNotify();
  }

  @Override
  public void entryWasRead() {
    updateIconAndNotify();
  }

  private boolean isOtherModalWindowActive() {
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    return activeWindow instanceof JDialog && ((JDialog)activeWindow).isModal() && (myDialog == null || myDialog.getWindow() != activeWindow);
  }

  private void updateIconAndNotify() {
    MessagePool.State state = myMessagePool.getState();
    updateIcon(state);

    if (state == MessagePool.State.NoErrors) {
      myNotificationPopupAlreadyShown = false;
      if (myBalloon != null) {
        Disposer.dispose(myBalloon);
      }
    }
    else if (state == MessagePool.State.UnreadErrors && !myNotificationPopupAlreadyShown && isActive(myFrame)) {
      Project project = myFrame.getProject();
      if (project != null) {
        ApplicationManager.getApplication().invokeLater(() -> showErrorNotification(project), project.getDisposed());
        myNotificationPopupAlreadyShown = true;
      }
    }
  }

  private static boolean isActive(IdeFrame frame) {
    return frame.isActive();
  }

  private void showErrorNotification(@Nonnull Project project) {
    String title = DiagnosticBundle.message("error.new.notification.title");
    String linkText = DiagnosticBundle.message("error.new.notification.link");
    Notification notification = new Notification("", AllIcons.Ide.FatalError, title, null, null, NotificationType.ERROR, null);
    notification.addAction(new NotificationAction(linkText) {
      @Override
      public void actionPerformed(@Nonnull AnActionEvent e, @Nonnull Notification notification) {
        notification.expire();
        openErrorsDialog(null);
      }
    });

    BalloonLayout layout = myFrame.getBalloonLayout();
    assert layout != null : myFrame;

    BalloonLayoutData layoutData = BalloonLayoutData.createEmpty();
    layoutData.fadeoutTime = 5000;
    layoutData.textColor = JBUI.CurrentTheme.Notification.Error.FOREGROUND;
    layoutData.fillColor = JBUI.CurrentTheme.Notification.Error.BACKGROUND;
    layoutData.borderColor = JBUI.CurrentTheme.Notification.Error.BORDER_COLOR;

    assert myBalloon == null;
    myBalloon = NotificationsManagerImpl.createBalloon(myFrame, notification, false, false, new Ref<>(layoutData), project);
    Disposer.register(myBalloon, () -> myBalloon = null);
    layout.add(myBalloon);
  }
}