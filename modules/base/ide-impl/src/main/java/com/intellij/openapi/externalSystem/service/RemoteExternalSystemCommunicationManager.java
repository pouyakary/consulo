/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service;

import consulo.application.CommonBundle;
import consulo.execution.DefaultExecutionResult;
import consulo.process.ExecutionException;
import consulo.execution.ExecutionResult;
import consulo.execution.executor.Executor;
import consulo.execution.configuration.CommandLineState;
import consulo.process.cmd.GeneralCommandLine;
import consulo.execution.configuration.RunProfileState;
import com.intellij.execution.configurations.SimpleJavaParameters;
import consulo.process.local.OSProcessHandler;
import consulo.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.rmi.RemoteProcessSupport;
import consulo.execution.runner.ProgramRunner;
import com.intellij.ide.actions.OpenProjectFileChooserDescriptor;
import consulo.ui.ex.action.AnAction;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.remote.wrapper.ExternalSystemFacadeWrapper;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import consulo.util.lang.ShutDownTracker;
import consulo.ui.ex.util.Alarm;
import com.intellij.util.PathUtil;
import consulo.util.lang.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import consulo.component.extension.ExtensionPointName;
import consulo.container.boot.ContainerPathManager;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiBundle;
import consulo.logging.Logger;
import consulo.module.content.layer.orderEntry.DependencyScope;
import consulo.project.ProjectBundle;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.charset.Charset;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Denis Zhdanov
 * @since 8/9/13 3:37 PM
 */
@Singleton
public class RemoteExternalSystemCommunicationManager implements ExternalSystemCommunicationManager {

  private static final Logger LOG = Logger.getInstance(RemoteExternalSystemCommunicationManager.class);

  private static final String MAIN_CLASS_NAME = RemoteExternalSystemFacadeImpl.class.getName();

  private final AtomicReference<RemoteExternalSystemProgressNotificationManager> myExportedNotificationManager
    = new AtomicReference<RemoteExternalSystemProgressNotificationManager>();

  @Nonnull
  private final ThreadLocal<ProjectSystemId> myTargetExternalSystemId = new ThreadLocal<ProjectSystemId>();

  @Nonnull
  private final ExternalSystemProgressNotificationManagerImpl                    myProgressManager;
  @Nonnull
  private final RemoteProcessSupport<Object, RemoteExternalSystemFacade, String> mySupport;

  @Inject
  public RemoteExternalSystemCommunicationManager(@Nonnull ExternalSystemProgressNotificationManager notificationManager) {
    myProgressManager = (ExternalSystemProgressNotificationManagerImpl)notificationManager;
    mySupport = new RemoteProcessSupport<Object, RemoteExternalSystemFacade, String>(RemoteExternalSystemFacade.class) {
      @Override
      protected void fireModificationCountChanged() {
      }

      @Override
      protected String getName(Object o) {
        return RemoteExternalSystemFacade.class.getName();
      }

      @Override
      protected RunProfileState getRunProfileState(Object o, String configuration, Executor executor) throws ExecutionException {
        return createRunProfileState();
      }
    };

    ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
      public void run() {
        shutdown(false);
      }
    });
  }

  public synchronized void shutdown(boolean wait) {
    mySupport.stopAll(wait);
  }

  private RunProfileState createRunProfileState() {
    return new CommandLineState(null) {
      private SimpleJavaParameters createJavaParameters() throws ExecutionException {

        final SimpleJavaParameters params = new SimpleJavaParameters();
        params.setJdk(new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()));

        params.setWorkingDirectory(ContainerPathManager.get().getBinPath());
        final List<String> classPath = new ArrayList<>();

        // IDE jars.
        classPath.addAll(PathManager.getUtilClassPath());
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(ProjectBundle.class), classPath);
        ExternalSystemApiUtil.addBundle(params.getClassPath(), "messages.ProjectBundle", ProjectBundle.class);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(PsiBundle.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(Alarm.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(DependencyScope.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(ExtensionPointName.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(OpenProjectFileChooserDescriptor.class), classPath);
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(ExternalSystemTaskNotificationListener.class), classPath);

        // External system module jars
        ContainerUtil.addIfNotNull(PathUtil.getJarPathForClass(getClass()), classPath);
        ExternalSystemApiUtil.addBundle(params.getClassPath(), "messages.CommonBundle", CommonBundle.class);
        params.getClassPath().addAll(classPath);

        params.setMainClass(MAIN_CLASS_NAME);
        params.getVMParametersList().addParametersString("-Djava.awt.headless=true");

        // It may take a while for gradle api to resolve external dependencies. Default RMI timeout
        // is 15 seconds (http://download.oracle.com/javase/6/docs/technotes/guides/rmi/sunrmiproperties.html#connectionTimeout),
        // we don't want to get EOFException because of that.
        params.getVMParametersList().addParametersString(
          "-Dsun.rmi.transport.connectionTimeout=" + String.valueOf(TimeUnit.HOURS.toMillis(1))
        );
//        params.getVMParametersList().addParametersString("-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5009");

        ProjectSystemId externalSystemId = myTargetExternalSystemId.get();
        if (externalSystemId != null) {
          ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
          if (manager != null) {
            params.getClassPath().add(PathUtil.getJarPathForClass(manager.getProjectResolverClass().getClass()));
            params.getProgramParametersList().add(manager.getProjectResolverClass().getName());
            params.getProgramParametersList().add(manager.getTaskManagerClass().getName());
            manager.enhanceRemoteProcessing(params);
          }
        }

        return params;
      }

      @Override
      @Nonnull
      public ExecutionResult execute(@Nonnull Executor executor, @Nonnull ProgramRunner runner) throws ExecutionException {
        ProcessHandler processHandler = startProcess();
        return new DefaultExecutionResult(null, processHandler, AnAction.EMPTY_ARRAY);
      }

      @Nonnull
      protected OSProcessHandler startProcess() throws ExecutionException {
        SimpleJavaParameters params = createJavaParameters();
        Sdk sdk = params.getJdk();
        if (sdk == null) {
          throw new ExecutionException("No sdk is defined. Params: " + params);
        }

        final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(sdk, params, false);
        final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
          @Override
          public Charset getCharset() {
            return commandLine.getCharset();
          }
        };
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
      }
    };
  }

  @Nullable
  @Override
  public RemoteExternalSystemFacade acquire(@Nonnull String id, @Nonnull ProjectSystemId externalSystemId)
    throws Exception
  {
    myTargetExternalSystemId.set(externalSystemId);
    final RemoteExternalSystemFacade facade;
    try {
      facade = mySupport.acquire(this, id);
    }
    finally {
      myTargetExternalSystemId.set(null);
    }
    if (facade == null) {
      return null;
    }

    RemoteExternalSystemProgressNotificationManager exported = myExportedNotificationManager.get();
    if (exported == null) {
      try {
        exported = (RemoteExternalSystemProgressNotificationManager)UnicastRemoteObject.exportObject(myProgressManager, 0);
        myExportedNotificationManager.set(exported);
      }
      catch (RemoteException e) {
        exported = myExportedNotificationManager.get();
      }
    }
    if (exported == null) {
      LOG.warn("Can't export progress manager");
    }
    else {
      facade.applyProgressManager(exported);
    }
    return facade;
  }

  @Override
  public void release(@Nonnull String id, @Nonnull ProjectSystemId externalSystemId) throws Exception {
    mySupport.release(this, id);
  }

  @Override
  public boolean isAlive(@Nonnull RemoteExternalSystemFacade facade) {
    RemoteExternalSystemFacade toCheck = facade;
    if (facade instanceof ExternalSystemFacadeWrapper) {
      toCheck = ((ExternalSystemFacadeWrapper)facade).getDelegate();

    }
    if (toCheck instanceof InProcessExternalSystemFacadeImpl) {
      return false;
    }
    try {
      facade.getResolver();
      return true;
    }
    catch (RemoteException e) {
      return false;
    }
  }

  @Override
  public void clear() {
    mySupport.stopAll(true); 
  }
}
