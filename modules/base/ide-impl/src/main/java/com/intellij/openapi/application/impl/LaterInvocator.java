// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.idea.ApplicationStarterCore;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import consulo.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ExceptionUtil;
import consulo.util.lang.SystemProperties;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.application.util.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import consulo.util.collection.Stack;
import consulo.application.ui.awt.internal.EDT;
import consulo.application.ui.awt.UIUtil;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.TransactionGuard;
import consulo.application.internal.TransactionGuardEx;
import consulo.application.internal.ApplicationWithIntentWriteLock;
import consulo.disposer.Disposable;
import consulo.util.collection.Maps;
import consulo.util.concurrent.ActionCallback;
import consulo.util.concurrent.AsyncResult;
import consulo.util.lang.ref.Ref;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

public final class LaterInvocator {
  private static final Logger LOG = Logger.getInstance(LaterInvocator.class);

  private LaterInvocator() {
  }

  // Application modal entities
  private static final List<Object> ourModalEntities = ContainerUtil.createLockFreeCopyOnWriteList();

  // Per-project modal entities
  private static final Map<Project, List<Dialog>> projectToModalEntities = ContainerUtil.createWeakMap();
  private static final Map<Project, Stack<ModalityState>> projectToModalEntitiesStack = ContainerUtil.createWeakMap();
  private static final Stack<ModalityStateEx> ourModalityStack = new Stack<>((ModalityStateEx)ModalityState.NON_MODAL);
  private static final EventDispatcher<ModalityStateListener> ourModalityStateMulticaster = EventDispatcher.create(ModalityStateListener.class);

  private static final Executor ourWriteThreadExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Write Thread", 1);
  private static final FlushQueue ourEdtQueue = new FlushQueue(SwingUtilities::invokeLater);
  private static final FlushQueue ourWtQueue = new FlushQueue(r -> ourWriteThreadExecutor.execute(() -> ((ApplicationWithIntentWriteLock)Application.get()).runIntendedWriteActionOnCurrentThread(r)));

  public static void addModalityStateListener(@Nonnull ModalityStateListener listener, @Nonnull Disposable parentDisposable) {
    if (!ourModalityStateMulticaster.getListeners().contains(listener)) {
      ourModalityStateMulticaster.addListener(listener, parentDisposable);
    }
  }

  private static final ConcurrentMap<Window, ModalityStateEx> ourWindowModalities = Maps.newConcurrentWeakHashMap();

  @Nonnull
  public static ModalityStateEx modalityStateForWindow(@Nonnull Window window) {
    return ourWindowModalities.computeIfAbsent(window, __ -> {
      synchronized (ourModalityStack) {
        for (ModalityStateEx state : ourModalityStack) {
          if (state.getModalEntities().contains(window)) {
            return state;
          }
        }
      }

      Window owner = window.getOwner();
      ModalityStateEx ownerState = owner == null ? (ModalityStateEx)ModalityState.NON_MODAL : modalityStateForWindow(owner);
      return isModalDialog(window) ? ownerState.appendEntity(window) : ownerState;
    });
  }

  private static boolean isModalDialog(@Nonnull Object window) {
    return window instanceof Dialog && ((Dialog)window).isModal();
  }

  @Nonnull
  public static AsyncResult<Void> invokeLater(@Nonnull Runnable runnable, @Nonnull BooleanSupplier expired) {
    ModalityState modalityState = ModalityState.defaultModalityState();
    return invokeLater(runnable, modalityState, expired);
  }

  @Nonnull
  public static AsyncResult<Void> invokeLater(@Nonnull Runnable runnable, @Nonnull ModalityState modalityState) {
    return invokeLater(runnable, modalityState, () -> false);
  }

  @Nonnull
  public static AsyncResult<Void> invokeLater(@Nonnull Runnable runnable, @Nonnull ModalityState modalityState, @Nonnull BooleanSupplier expired) {
    AsyncResult<Void> callback = AsyncResult.undefined();
    invokeLaterWithCallback(runnable, modalityState, expired, callback, true);
    return callback;
  }

  public static void invokeLaterWithCallback(@Nonnull Runnable runnable, @Nonnull ModalityState modalityState, @Nonnull BooleanSupplier expired, @Nullable ActionCallback callback, boolean onEdt) {
    if (expired.getAsBoolean()) {
      if (callback != null) {
        callback.setRejected();
      }
      return;
    }
    FlushQueue.RunnableInfo runnableInfo = new FlushQueue.RunnableInfo(runnable, modalityState, expired, callback);
    getRunnableQueue(onEdt).push(runnableInfo);
    requestFlush();
  }

  public static void invokeAndWait(@Nonnull final Runnable runnable, @Nonnull ModalityState modalityState) {
    LOG.assertTrue(!isDispatchThread());

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final Ref<Throwable> exception = Ref.create();
    Runnable runnable1 = new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        }
        catch (Throwable e) {
          exception.set(e);
        }
        finally {
          semaphore.up();
        }
      }

      @Override
      @NonNls
      public String toString() {
        return "InvokeAndWait[" + runnable + "]";
      }
    };
    invokeLaterWithCallback(runnable1, modalityState, () -> false, null, true);
    semaphore.waitFor();
    if (!exception.isNull()) {
      Throwable cause = exception.get();
      if (SystemProperties.getBooleanProperty("invoke.later.wrap.error", true)) {
        // wrap everything to keep the current thread stacktrace
        // also TC ComparisonFailure feature depends on this
        throw new RuntimeException(cause);
      }
      else {
        ExceptionUtil.rethrow(cause);
      }
    }
  }

  public static void enterModal(@Nonnull Object modalEntity) {
    ModalityStateEx state = getCurrentModalityState().appendEntity(modalEntity);
    if (isModalDialog(modalEntity)) {
      List<Object> currentEntities = state.getModalEntities();
      state = modalityStateForWindow((Window)modalEntity);
      state.forceModalEntities(currentEntities);
    }
    enterModal(modalEntity, state);
  }

  public static void enterModal(@Nonnull Object modalEntity, @Nonnull ModalityStateEx appendedState) {
    LOG.assertTrue(isWriteThread(), "enterModal() should be invoked in write thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + modalEntity);
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(true, modalEntity);

    ourModalEntities.add(modalEntity);
    synchronized (ourModalityStack) {
      ourModalityStack.push(appendedState);
    }

    TransactionGuardEx guard = ApplicationStarterCore.isLoaded() ? (TransactionGuardEx)TransactionGuard.getInstance() : null;
    if (guard != null) {
      guard.enteredModality(appendedState);
    }

    reincludeSkippedItemsAndRequestFlush();
  }

  public static void enterModal(Project project, @Nonnull Dialog dialog) {
    LOG.assertTrue(isDispatchThread(), "enterModal() should be invoked in event-dispatch thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("enterModal:" + dialog.getName() + " ; for project: " + project.getName());
    }

    if (project == null) {
      enterModal(dialog);
      return;
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(true, dialog);

    List<Dialog> modalEntitiesList = projectToModalEntities.getOrDefault(project, ContainerUtil.createLockFreeCopyOnWriteList());
    projectToModalEntities.put(project, modalEntitiesList);
    modalEntitiesList.add(dialog);

    Stack<ModalityState> modalEntitiesStack = projectToModalEntitiesStack.getOrDefault(project, new Stack<>(ModalityState.NON_MODAL));
    projectToModalEntitiesStack.put(project, modalEntitiesStack);
    modalEntitiesStack.push(new ModalityStateEx(ourModalEntities));
  }

  /**
   * Marks the given modality state (not {@code any()}} as transparent, i.e. {@code invokeLater} calls with its "parent" modality state
   * will also be executed within it. NB: this will cause all VFS/PSI/etc events be executed inside your modal dialog, so you'll need
   * to handle them appropriately, so please consider making the dialog non-modal instead of using this API.
   */
  public static void markTransparent(@Nonnull ModalityState state) {
    ((ModalityStateEx)state).markTransparent();
    reincludeSkippedItemsAndRequestFlush();
  }

  public static void leaveModal(Project project, @Nonnull Dialog dialog) {
    LOG.assertTrue(isWriteThread(), "leaveModal() should be invoked in write thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + dialog.getName() + " ; for project: " + project.getName());
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(false, dialog);

    int index = ourModalEntities.indexOf(dialog);

    if (index != -1) {
      removeModality(dialog, index);
    }
    else if (project != null) {
      List<Dialog> dialogs = projectToModalEntities.get(project);
      int perProjectIndex = dialogs.indexOf(dialog);
      LOG.assertTrue(perProjectIndex >= 0);
      dialogs.remove(perProjectIndex);
      Stack<ModalityState> states = projectToModalEntitiesStack.get(project);
      states.remove(perProjectIndex + 1);
      for (int i = 1; i < states.size(); i++) {
        ((ModalityStateEx)states.get(i)).removeModality(dialog);
      }
    }

    reincludeSkippedItemsAndRequestFlush();
  }

  private static void removeModality(@Nonnull Object modalEntity, int index) {
    ourModalEntities.remove(index);
    synchronized (ourModalityStack) {
      ourModalityStack.remove(index + 1);
      for (int i = 1; i < ourModalityStack.size(); i++) {
        ourModalityStack.get(i).removeModality(modalEntity);
      }
    }
    ModalityStateEx.unmarkTransparent(modalEntity);
  }


  public static void leaveModal(@Nonnull Object modalEntity) {
    LOG.assertTrue(isWriteThread(), "leaveModal() should be invoked in write thread");

    if (LOG.isDebugEnabled()) {
      LOG.debug("leaveModal:" + modalEntity);
    }

    ourModalityStateMulticaster.getMulticaster().beforeModalityStateChanged(false, modalEntity);

    int index = ourModalEntities.indexOf(modalEntity);
    LOG.assertTrue(index >= 0);
    removeModality(modalEntity, index);

    reincludeSkippedItemsAndRequestFlush();
  }

  @TestOnly
  public static void leaveAllModals() {
    while (!ourModalEntities.isEmpty()) {
      leaveModal(ourModalEntities.get(ourModalEntities.size() - 1));
    }
    LOG.assertTrue(getCurrentModalityState() == ModalityState.NON_MODAL, getCurrentModalityState());
    reincludeSkippedItemsAndRequestFlush();
  }

  @Nonnull
  public static Object [] getCurrentModalEntities() {
    ApplicationManager.getApplication().assertIsWriteThread();
    return ArrayUtil.toObjectArray(ourModalEntities);
  }

  @Nonnull
  public static ModalityStateEx getCurrentModalityState() {
    if (!EDT.isCurrentThreadEdt()) {
      ApplicationManager.getApplication().assertIsWriteThread();
    }
    synchronized (ourModalityStack) {
      return ourModalityStack.peek();
    }
  }

  public static boolean isInModalContextForProject(final Project project) {
    LOG.assertTrue(isWriteThread());

    if (ourModalEntities.isEmpty()) return false;

    List<Dialog> modalEntitiesForProject = projectToModalEntities.get(project);

    return modalEntitiesForProject == null || modalEntitiesForProject.isEmpty();
  }

  public static boolean isInModalContext() {
    return isInModalContextForProject(null);
  }

  private static boolean isDispatchThread() {
    return ApplicationManager.getApplication().isDispatchThread();
  }

  private static boolean isWriteThread() {
    return ApplicationManager.getApplication().isWriteThread();
  }

  @Nonnull
  private static FlushQueue getRunnableQueue(boolean onEdt) {
    return onEdt ? ourEdtQueue : ourWtQueue;
  }

  static void requestFlush() {
    SUBMITTED_COUNT.incrementAndGet();
    while (FLUSHER_SCHEDULED.compareAndSet(false, true)) {
      int whichThread = THREAD_TO_FLUSH.getAndUpdate(operand -> operand ^ 1);

      long submittedCount = SUBMITTED_COUNT.get();

      FlushQueue firstQueue = getRunnableQueue(whichThread == 0);
      if (firstQueue.mayHaveItems()) {
        firstQueue.scheduleFlush();
        return;
      }

      FlushQueue secondQueue = getRunnableQueue(whichThread != 0);
      if (secondQueue.mayHaveItems()) {
        secondQueue.scheduleFlush();
        return;
      }

      FLUSHER_SCHEDULED.set(false);

      // If a requestFlush was called by somebody else (because queues were modified) but we have not really scheduled anything
      // then we've missed `mayHaveItems` `true` value because of race.
      // Another run of `requestFlush` will get the correct `mayHaveItems` because
      // `mayHaveItems` is mutated strictly before SUBMITTED_COUNT which we've observe below
      if (submittedCount == SUBMITTED_COUNT.get()) {
        break;
      }
    }
  }

  public static void pollWriteThreadEventsOnce() {
    LOG.assertTrue(!SwingUtilities.isEventDispatchThread());
    LOG.assertTrue(ApplicationManager.getApplication().isWriteThread());

    ourWtQueue.flushNow();
  }

  /**
   * There might be some requests in the queue, but ourFlushQueueRunnable might not be scheduled yet. In these circumstances
   * {@link EventQueue#peekEvent()} default implementation would return null, and {@link UIUtil#dispatchAllInvocationEvents()} would
   * stop processing events too early and lead to spurious test failures.
   *
   * @see com.intellij.ide.IdeEventQueue#peekEvent()
   */
  public static boolean ensureFlushRequested() {
    if (ourEdtQueue.getNextEvent(false) != null) {
      ourEdtQueue.scheduleFlush();
      return true;
    }
    else if (ourWtQueue.getNextEvent(false) != null) {
      ourWtQueue.scheduleFlush();
      return true;
    }
    return false;
  }

  static final AtomicBoolean FLUSHER_SCHEDULED = new AtomicBoolean(false);

  private static final AtomicLong SUBMITTED_COUNT = new AtomicLong(0);

  private static final AtomicInteger THREAD_TO_FLUSH = new AtomicInteger(0);

  @TestOnly
  @Nonnull
  public static Collection<FlushQueue.RunnableInfo> getLaterInvocatorEdtQueue() {
    return ourEdtQueue.getQueue();
  }

  @TestOnly
  @Nonnull
  public static Collection<FlushQueue.RunnableInfo> getLaterInvocatorWtQueue() {
    return ourWtQueue.getQueue();
  }

  private static void reincludeSkippedItemsAndRequestFlush() {
    ourEdtQueue.reincludeSkippedItems();
    ourWtQueue.reincludeSkippedItems();
    requestFlush();
  }

  public static void purgeExpiredItems() {
    ourEdtQueue.purgeExpiredItems();
    ourWtQueue.purgeExpiredItems();
    requestFlush();
  }

  @TestOnly
  public static void dispatchPendingFlushes() {
    if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Must call from EDT");

    Semaphore semaphore = new Semaphore();
    semaphore.down();
    invokeLater(semaphore::up, ModalityState.any());
    while (!semaphore.isUp()) {
      UIUtil.dispatchAllInvocationEvents();
    }
  }
}
