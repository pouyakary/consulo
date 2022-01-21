package com.intellij.coverage;

import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.process.ProcessHandler;
import consulo.component.persist.InvalidDataException;
import consulo.component.persist.WriteExternalException;
import org.jdom.Element;
import javax.annotation.Nonnull;

/**
 * @author traff
 */
public class CoverageHelper {
  private CoverageHelper() {
  }

  public static void attachToProcess(@Nonnull RunConfigurationBase configuration,
                                     @Nonnull ProcessHandler handler,
                                     RunnerSettings runnerSettings) {
    resetCoverageSuit(configuration);

    // attach to process termination listener
    CoverageDataManager.getInstance(configuration.getProject()).attachToProcess(handler, configuration, runnerSettings);
  }

  public static void resetCoverageSuit(RunConfigurationBase configuration) {
    final CoverageEnabledConfiguration covConfig = CoverageEnabledConfiguration.getOrCreate(configuration);

    // reset coverage suite
    covConfig.setCurrentCoverageSuite(null);

    // register new coverage suite
    final CoverageDataManager coverageDataManager = CoverageDataManager.getInstance(configuration.getProject());

    covConfig.setCurrentCoverageSuite(coverageDataManager.addCoverageSuite(covConfig));
  }

  public static void doReadExternal(RunConfigurationBase runConfiguration, Element element) throws InvalidDataException {
    final CoverageEnabledConfiguration covConf = CoverageEnabledConfiguration.getOrCreate(runConfiguration);

    covConf.readExternal(element);
  }

  public static void doWriteExternal(RunConfigurationBase runConfiguration, Element element) throws WriteExternalException {
    final CoverageEnabledConfiguration covConf = CoverageEnabledConfiguration.getOrCreate(runConfiguration);

    covConf.writeExternal(element);
  }
}
