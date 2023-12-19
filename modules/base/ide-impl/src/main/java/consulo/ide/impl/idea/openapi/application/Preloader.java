/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package consulo.ide.impl.idea.openapi.application;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.HeavyProcessLatch;
import consulo.application.PreloadingActivity;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.application.impl.internal.progress.AbstractProgressIndicatorBase;
import consulo.application.impl.internal.progress.ProgressIndicatorBase;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.component.ProcessCanceledException;
import consulo.container.util.StatCollector;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.util.lang.TimeoutUtil;
import jakarta.annotation.Nonnull;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author peter
 */
@Singleton
@ServiceAPI(value = ComponentScope.APPLICATION, lazy = false)
@ServiceImpl
public class Preloader implements Disposable {
  private static final Logger LOG = Logger.getInstance(Preloader.class);
  private final ExecutorService myExecutor;
  private final ProgressIndicator myIndicator = new ProgressIndicatorBase();
  private final ProgressIndicator myWrappingIndicator = new AbstractProgressIndicatorBase() {
    @Override
    public void checkCanceled() {
      checkHeavyProcessRunning();
      myIndicator.checkCanceled();
    }

    @Override
    public boolean isCanceled() {
      return myIndicator.isCanceled();
    }
  };

  private static void checkHeavyProcessRunning() {
    if (HeavyProcessLatch.INSTANCE.isRunning()) {
      TimeoutUtil.sleep(1);
    }
  }

  @Inject
  public Preloader(@Nonnull Application application, @Nonnull ApplicationConcurrency applicationConcurrency, @Nonnull ProgressManager progressManager) {
    myExecutor = applicationConcurrency.createSequentialApplicationPoolExecutor("Preloader pool");

    StatCollector collector = new StatCollector();

    List<CompletableFuture<?>> result = new ArrayList<>();

    application.getExtensionPoint(PreloadingActivity.class).forEachExtensionSafe(activity -> {
      result.add(CompletableFuture.runAsync(() -> {
        if (myIndicator.isCanceled()) return;

        checkHeavyProcessRunning();
        if (myIndicator.isCanceled()) return;

        progressManager.runProcess(() -> {
          Runnable mark = collector.mark(activity.getClass().getName());
          try {
            activity.preload(myWrappingIndicator);
          }
          catch (ProcessCanceledException ignore) {
          }
          catch (Throwable e) {
            LOG.error(e);
          }
          finally {
            mark.run();
          }
          LOG.info("Finished preloading " + activity);
        }, myIndicator);
      }, myExecutor));
    });

    CompletableFuture.allOf(result.toArray(CompletableFuture[]::new)).handleAsync((aVoid, throwable) -> {
      collector.dump("Preload statistics", LOG::info);
      return true;
    }, myExecutor);
  }

  @Override
  public void dispose() {
    myExecutor.shutdown();
    myIndicator.cancel();
  }
}
