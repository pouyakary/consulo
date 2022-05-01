package consulo.web.application.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.application.ApplicationManager;
import consulo.application.TransactionGuard;
import consulo.ide.impl.application.UnifiedTransactionGuardImpl;
import consulo.application.impl.internal.BaseApplication;
import consulo.application.impl.internal.IdeaModalityState;
import consulo.application.impl.internal.ReadMostlyRWLock;
import consulo.application.impl.internal.start.StartupProgress;
import consulo.component.ComponentManager;
import consulo.injecting.InjectingContainerBuilder;
import consulo.logging.Logger;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.ref.SimpleReference;
import consulo.web.application.WebApplication;
import consulo.web.application.WebSession;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

/**
 * @author VISTALL
 * @since 16-Sep-17
 */
public class WebApplicationImpl extends BaseApplication implements WebApplication {
  private static final Logger LOG = Logger.getInstance(WebApplicationImpl.class);

  private WebSession myCurrentSession;
  private static final IdeaModalityState ANY = new IdeaModalityState() {
    @Override
    public boolean dominates(@Nonnull IdeaModalityState anotherState) {
      return false;
    }

    @Override
    public String toString() {
      return "ANY";
    }
  };

  public WebApplicationImpl(@Nonnull SimpleReference<? extends StartupProgress> splash) {
    super(splash);

    ApplicationManager.setApplication(this);

    myLock = new ReadMostlyRWLock(null);
  }

  @Override
  protected void bootstrapInjectingContainer(@Nonnull InjectingContainerBuilder builder) {
    super.bootstrapInjectingContainer(builder);

    builder.bind(TransactionGuard.class).to(new UnifiedTransactionGuardImpl());
  }

  @Override
  public boolean isInternal() {
    return true;
  }

  @Nullable
  public WebStartupProgressImpl getSplash() {
    return (WebStartupProgressImpl)mySplashRef.get();
  }

  @Override
  @Nonnull
  public IdeaModalityState getAnyModalityState() {
    return ANY;
  }

  @RequiredReadAction
  @Override
  public void assertReadAccessAllowed() {
    if (!isReadAccessAllowed()) {
      throw new IllegalArgumentException();
    }
  }

  @RequiredUIAccess
  @Override
  public void assertIsDispatchThread() {
    if (!isDispatchThread()) {
      throw new IllegalArgumentException(Thread.currentThread().getName() + " is not ui thread");
    }
  }

  @RequiredUIAccess
  @Override
  public void assertIsWriteThread() {
    if (!isWriteThread()) {
      throw new IllegalArgumentException(Thread.currentThread().getName() + " is not write thread");
    }
  }

  @Override
  public boolean isWriteThread() {
    return super.isWriteThread() || isDispatchThread();
  }

  @Override
  public void exit() {

  }

  @Override
  public void invokeLater(@Nonnull Runnable runnable) {
    WebSession currentSession = getCurrentSession();
    if (currentSession != null) currentSession.getAccess().giveIfNeed(runnable);
  }

  @Override
  public void invokeLater(@Nonnull Runnable runnable, @Nonnull BooleanSupplier expired) {
    WebSession currentSession = getCurrentSession();
    if (currentSession != null) currentSession.getAccess().giveIfNeed(runnable);
  }

  @Override
  public void invokeLater(@Nonnull Runnable runnable, @Nonnull consulo.ui.ModalityState state) {
    WebSession currentSession = getCurrentSession();
    if (currentSession != null) currentSession.getAccess().giveIfNeed(runnable);
  }

  @Override
  public void invokeLater(@Nonnull Runnable runnable, @Nonnull consulo.ui.ModalityState state, @Nonnull BooleanSupplier expired) {
    WebSession currentSession = getCurrentSession();
    if (currentSession != null) currentSession.getAccess().giveIfNeed(runnable);
  }

  @Override
  public void invokeAndWait(@Nonnull Runnable runnable, @Nonnull consulo.ui.ModalityState modalityState) {
    WebSession currentSession = getCurrentSession();
    if (currentSession != null) currentSession.getAccess().giveAndWaitIfNeed(runnable);
  }

  @Nonnull
  @Override
  public IdeaModalityState getCurrentModalityState() {
    return getNoneModalityState();
  }

  @Nonnull
  @Override
  public IdeaModalityState getModalityStateForComponent(@Nonnull Component c) {
    return getNoneModalityState();
  }

  @Nonnull
  @Override
  public IdeaModalityState getDefaultModalityState() {
    return getNoneModalityState();
  }

  @Nonnull
  @Override
  public IdeaModalityState getNoneModalityState() {
    return IdeaModalityState.NON_MODAL;
  }

  @RequiredUIAccess
  @Override
  public long getIdleTime() {
    return 0;
  }

  @Override
  public boolean isHeadlessEnvironment() {
    return false;
  }

  @Override
  public boolean isDisposeInProgress() {
    return false;
  }

  @Override
  public boolean isRestartCapable() {
    return true;
  }

  @Override
  public boolean isActive() {
    return true;
  }

  @Override
  public void exit(boolean force, boolean exitConfirmed) {

  }

  @Override
  public void restart(boolean exitConfirmed) {

  }

  @Override
  public boolean runProcessWithProgressSynchronously(@Nonnull Runnable process,
                                                     @Nonnull String progressTitle,
                                                     boolean canBeCanceled,
                                                     boolean shouldShowModalWindow,
                                                     @Nullable ComponentManager project,
                                                     @Nullable JComponent parentComponent,
                                                     @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText) {
    return true;
  }

  @Override
  public void assertTimeConsuming() {

  }

  @Nonnull
  @Override
  public UIAccess getLastUIAccess() {
    WebSession currentSession = getCurrentSession();
    if (currentSession == null) {
      throw new IllegalArgumentException("No session");
    }
    return currentSession.getAccess();
  }

  @Override
  public void setCurrentSession(@Nullable WebSession session) {
    myCurrentSession = session;
  }

  @Override
  @Nullable
  public WebSession getCurrentSession() {
    return myCurrentSession;
  }

  @Override
  public boolean isUnifiedApplication() {
    return true;
  }
}
