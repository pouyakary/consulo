// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.desktop.awt.wm;

import consulo.annotation.component.ComponentProfiles;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.internal.ApplicationManagerEx;
import consulo.application.ui.wm.*;
import consulo.application.util.registry.Registry;
import consulo.component.ComponentManager;
import consulo.dataContext.DataContext;
import consulo.desktop.awt.ui.IdeEventQueue;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ide.impl.idea.openapi.util.TimedOutCallback;
import consulo.ide.impl.idea.ui.popup.AbstractPopup;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ui.internal.ProjectIdeFocusManager;
import consulo.project.ui.wm.IdeFrame;
import consulo.project.ui.wm.event.ApplicationActivationListener;
import consulo.ui.ModalityState;
import consulo.ui.UIAccess;
import consulo.ui.ex.UiActivityMonitor;
import consulo.ui.ex.awt.IdeFocusTraversalPolicy;
import consulo.ui.ex.awt.UIExAWTDataKey;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.ui.ex.popup.JBPopup;
import consulo.util.concurrent.ActionCallback;
import consulo.util.concurrent.AsyncResult;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
@ServiceImpl(profiles = ComponentProfiles.AWT)
public final class FocusManagerImpl implements ApplicationIdeFocusManager, Disposable {
  private static final Logger LOG = Logger.getInstance(FocusManagerImpl.class);

  private final List<FocusRequestInfo> myRequests = new LinkedList<>();

  private final IdeEventQueue myQueue;

  private final Set<FurtherRequestor> myValidFurtherRequestors = new HashSet<>();

  private final Set<ActionCallback> myTypeAheadRequestors = new HashSet<>();
  private boolean myTypeaheadEnabled = true;

  private final Map<IdeFrame, Component> myLastFocused = ContainerUtil.createWeakValueMap();
  private final Map<IdeFrame, Component> myLastFocusedAtDeactivation = ContainerUtil.createWeakValueMap();

  private DataContext myRunContext;

  private IdeFrame myLastFocusedFrame;

  @Inject
  public FocusManagerImpl(Application application) {
    UiActivityMonitor.getInstance();

    myQueue = IdeEventQueue.getInstance();

    final AppListener listener = new AppListener();
    application.getMessageBus().connect().subscribe(ApplicationActivationListener.class, listener);

    IdeEventQueue.getInstance().addDispatcher(e -> {
      if (e instanceof FocusEvent) {
        final FocusEvent fe = (FocusEvent)e;
        final Component c = fe.getComponent();
        if (c instanceof Window || c == null) return false;

        Component parent = UIUtil.findUltimateParent(c);

        if (parent instanceof Window) {
          consulo.ui.Window uiWindow = TargetAWT.from((Window)parent);

          IdeFrame ideFrame = uiWindow.getUserData(IdeFrame.KEY);
          if (ideFrame != null) {
            myLastFocused.put(ideFrame, c);
          }
        }
      }
      else if (e instanceof WindowEvent) {
        Window wnd = ((WindowEvent)e).getWindow();
        if (e.getID() == WindowEvent.WINDOW_CLOSED) {
          consulo.ui.Window uiWindow = TargetAWT.from(wnd);

          IdeFrame ideFrame = uiWindow == null ? null : uiWindow.getUserData(IdeFrame.KEY);
          if (ideFrame != null) {
            myLastFocused.remove(ideFrame);
            myLastFocusedAtDeactivation.remove(ideFrame);
          }
        }
      }

      return false;
    }, this);

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusedWindow", evt -> {
      consulo.ui.Window uiWindow = TargetAWT.from((Window)evt.getNewValue());
      if (uiWindow == null) {
        return;
      }

      IdeFrame ideFrame = uiWindow.getUserData(IdeFrame.KEY);
      if (ideFrame != null) {
        myLastFocusedFrame = ideFrame;
      }
    });
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return myLastFocusedFrame;
  }

  @Override
  public AsyncResult<Void> requestFocusInProject(@Nonnull Component c, @Nullable ComponentManager project) {
    if (ApplicationManagerEx.getApplicationEx().isActive() || !Registry.is("suppress.focus.stealing")) {
      c.requestFocus();
    }
    else {
      c.requestFocusInWindow();
    }
    return AsyncResult.resolved();
  }

  @Override
  @Nonnull
  public AsyncResult<Void> requestFocus(@Nonnull final Component c, final boolean forced) {
    c.requestFocus();
    return AsyncResult.resolved();
  }

  @Nonnull
  @Override
  public AsyncResult<Void> requestFocus(@Nonnull consulo.ui.Component c, boolean forced) {
    requestFocus(TargetAWT.to(c), forced);
    return AsyncResult.resolved();
  }

  @Nonnull
  public List<FocusRequestInfo> getRequests() {
    return myRequests;
  }

  public void recordFocusRequest(Component c, boolean forced) {
    myRequests.add(new FocusRequestInfo(c, new Throwable(), forced));
    if (myRequests.size() > 200) {
      myRequests.remove(0);
    }
  }

  @Override
  public void dispose() {
    for (FurtherRequestor requestor : myValidFurtherRequestors) {
      Disposer.dispose(requestor);
    }
    myValidFurtherRequestors.clear();
  }

  @Override
  public void doWhenFocusSettlesDown(@Nonnull ExpirableRunnable runnable) {
    doWhenFocusSettlesDown((Runnable)runnable);
  }

  @Override
  public void doWhenFocusSettlesDown(@Nonnull final Runnable runnable) {
    myQueue.executeWhenAllFocusEventsLeftTheQueue(runnable);
  }

  @Override
  public void doWhenFocusSettlesDown(@Nonnull Runnable runnable, @Nonnull ModalityState modality) {
    AtomicBoolean immediate = new AtomicBoolean(true);
    doWhenFocusSettlesDown(() -> {
      if (immediate.get()) {
        if (!(runnable instanceof ExpirableRunnable) || !((ExpirableRunnable)runnable).isExpired()) {
          runnable.run();
        }
        return;
      }

      ApplicationManager.getApplication().invokeLater(() -> doWhenFocusSettlesDown(runnable, modality), modality);
    });
    immediate.set(false);
  }


  @Override
  public void setTypeaheadEnabled(boolean enabled) {
    myTypeaheadEnabled = enabled;
  }

  private boolean isTypeaheadEnabled() {
    return Registry.is("actionSystem.fixLostTyping") && myTypeaheadEnabled;
  }

  @Override
  public void typeAheadUntil(@Nonnull ActionCallback callback, @Nonnull String cause) {
    if (!isTypeaheadEnabled()) return;

    final long currentTime = System.currentTimeMillis();
    final ActionCallback done;
    if (!Registry.is("type.ahead.logging.enabled")) {
      done = callback;
    }
    else {
      final String id = new Exception().getStackTrace()[2].getClassName();
      //LOG.setLevel(Level.ALL);
      final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy HH:ss:SSS", Locale.US);
      LOG.info(dateFormat.format(System.currentTimeMillis()) + "\tStarted:  " + id);
      done = new ActionCallback();
      callback.doWhenDone(() -> {
        done.setDone();
        LOG.info(dateFormat.format(System.currentTimeMillis()) + "\tDone:     " + id);
      });
      callback.doWhenRejected(() -> {
        done.setRejected();
        LOG.info(dateFormat.format(System.currentTimeMillis()) + "\tRejected: " + id);
      });
    }
    assertDispatchThread();

    myTypeAheadRequestors.add(done);
    done.notify(new TimedOutCallback(Registry.intValue("actionSystem.commandProcessingTimeout"), "Typeahead request blocked", new Exception() {
      @Override
      public String getMessage() {
        return "Time: " +
               (System.currentTimeMillis() - currentTime) +
               "; cause: " +
               cause +
               "; runnable waiting for the focus change: " +
               IdeEventQueue.getInstance().runnablesWaitingForFocusChangeState();
      }
    }, true).doWhenProcessed(() -> myTypeAheadRequestors.remove(done)));
  }

  @Override
  public Component getFocusOwner() {
    UIAccess.assertIsUIThread();

    Component result = null;
    if (!ApplicationManager.getApplication().isActive()) {
      result = myLastFocusedAtDeactivation.get(getLastFocusedFrame());
    }
    else if (myRunContext != null) {
      result = myRunContext.getData(UIExAWTDataKey.CONTEXT_COMPONENT);
    }

    if (result == null) {
      result = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    if (result == null) {
      final Component permOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
      if (permOwner != null) {
        result = permOwner;
      }

      if (UIUtil.isMeaninglessFocusOwner(result)) {
        result = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      }
    }

    return result;
  }

  @Override
  public void runOnOwnContext(@Nonnull DataContext context, @Nonnull Runnable runnable) {
    assertDispatchThread();

    myRunContext = context;
    try {
      runnable.run();
    }
    finally {
      myRunContext = null;
    }
  }

  @Override
  public Component getLastFocusedFor(FocusableFrame frame) {
    assertDispatchThread();

    return myLastFocused.get(frame);
  }

  public void setLastFocusedAtDeactivation(@Nonnull IdeFrame frame, @Nonnull Component c) {
    myLastFocusedAtDeactivation.put(frame, c);
  }

  @Override
  public void toFront(JComponent c) {
    assertDispatchThread();

    if (c == null) return;

    final Window window = UIUtil.getParentOfType(Window.class, c);
    if (window != null && window.isShowing()) {
      doWhenFocusSettlesDown(() -> {
        if (ApplicationManager.getApplication().isActive()) {
          if (window instanceof JFrame && ((JFrame)window).getState() == Frame.ICONIFIED) {
            ((JFrame)window).setState(Frame.NORMAL);
          }
          else {
            window.toFront();
          }
        }
      });
    }
  }

  private static class FurtherRequestor implements FocusRequestor {
    private final IdeFocusManager myManager;
    private final Expirable myExpirable;
    private Throwable myAllocation;
    private boolean myDisposed;

    private FurtherRequestor(@Nonnull IdeFocusManager manager, @Nonnull Expirable expirable) {
      myManager = manager;
      myExpirable = expirable;
      if (Registry.is("ide.debugMode")) {
        myAllocation = new Exception();
      }
    }

    @Nonnull
    @Override
    public AsyncResult<Void> requestFocus(@Nonnull Component c, boolean forced) {
      final AsyncResult<Void> result = isExpired() ? AsyncResult.rejected() : myManager.requestFocus(c, forced);
      result.doWhenProcessed(() -> Disposer.dispose(this));
      return result;
    }

    @Nonnull
    @Override
    public AsyncResult<Void> requestFocus(@Nonnull consulo.ui.Component c, boolean forced) {
      final AsyncResult<Void> result = isExpired() ? AsyncResult.rejected() : myManager.requestFocus(c, forced);
      result.doWhenProcessed(() -> Disposer.dispose(this));
      return result;
    }

    private boolean isExpired() {
      return myExpirable.isExpired() || myDisposed;
    }

    @Override
    public void dispose() {
      myDisposed = true;
    }
  }

  private class AppListener implements ApplicationActivationListener {

    @Override
    public void delayedApplicationDeactivated(@Nonnull IdeFrame ideFrame) {
      final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      Component parent = UIUtil.findUltimateParent(owner);

      if (parent == ideFrame) {
        myLastFocusedAtDeactivation.put(ideFrame, owner);
      }
    }
  }

  @Override
  public JComponent getFocusTargetFor(@Nonnull JComponent comp) {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(comp);
  }

  @Override
  public Component getFocusedDescendantFor(Component comp) {
    final Component focused = getFocusOwner();
    if (focused == null) return null;

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) return focused;

    List<JBPopup> popups = AbstractPopup.getChildPopups(comp);
    for (JBPopup each : popups) {
      if (each.isFocused()) return focused;
    }

    return null;
  }

  @Nonnull
  @Override
  public AsyncResult<Void> requestDefaultFocus(boolean forced) {
    Component toFocus = null;
    if (myLastFocusedFrame != null) {
      toFocus = myLastFocused.get(myLastFocusedFrame);
      if (toFocus == null || !toFocus.isShowing()) {
        toFocus = getFocusTargetFor(myLastFocusedFrame.getComponent());
      }
    }
    else {
      Optional<Component> toFocusOptional = Arrays.stream(Window.getWindows()).
              filter(window -> window instanceof RootPaneContainer).
              filter(window -> ((RootPaneContainer)window).getRootPane() != null).
              filter(window -> window.isActive()).
              findFirst().
              map(w -> getFocusTargetFor(((RootPaneContainer)w).getRootPane()));

      if (toFocusOptional.isPresent()) {
        toFocus = toFocusOptional.get();
      }
    }

    if (toFocus != null) {
      if (ApplicationManagerEx.getApplicationEx().isActive() || !Registry.is("suppress.focus.stealing")) {
        toFocus.requestFocus();
      }
      else {
        toFocus.requestFocusInWindow();
      }
      return AsyncResult.resolved();
    }


    return AsyncResult.resolved();
  }

  @Override
  public boolean isFocusTransferEnabled() {
    if (Registry.is("focus.fix.lost.cursor")) {
      return true;
    }
    return ApplicationManager.getApplication().isActive() || !Registry.is("actionSystem.suspendFocusTransferIfApplicationInactive");
  }

  private static void assertDispatchThread() {
    if (Registry.is("actionSystem.assertFocusAccessFromEdt")) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }
  }

  @Override
  @Nonnull
  public IdeFocusManager findInstanceByComponent(@Nonnull Component c) {
    final IdeFocusManager instance = findByComponent(c);
    return instance != null ? instance : findInstanceByContext(null);
  }

  @Nullable
  private IdeFocusManager findByComponent(Component c) {
    final Component parent = UIUtil.findUltimateParent(c);
    if (parent instanceof Window) {
      consulo.ui.Window uiWindow = TargetAWT.from((Window)parent);

      IdeFrame ideFrame = uiWindow.getUserData(IdeFrame.KEY);
      if (ideFrame == null) {
        return null;
      }
      return getInstanceSafe(ideFrame.getProject());
    }
    return null;
  }

  @Nonnull
  public IdeFocusManager findInstanceByContext(@Nullable DataContext context) {
    IdeFocusManager instance = null;
    if (context != null) {
      instance = getInstanceSafe(context.getData(Project.KEY));
    }

    if (instance == null) {
      instance = findByComponent(KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow());
    }

    if (instance == null) {
      instance = IdeFocusManager.getGlobalInstance();
    }

    return instance;
  }

  @Nonnull
  @Override
  public IdeFocusManager getInstanceForProject(@Nullable ComponentManager componentManager) {
    if (!(componentManager instanceof Project)) {
      return this;
    }
    IdeFocusManager ideFocusManager = getInstanceSafe((Project)componentManager);
    if (ideFocusManager != null) {
      return ideFocusManager;
    }
    return this;
  }

  @Nullable
  static IdeFocusManager getInstanceSafe(@Nullable Project project) {
    if (project != null && !project.isDisposed() && project.isInitialized()) {
      return ProjectIdeFocusManager.getInstance(project);
    }
    return null;
  }
}
