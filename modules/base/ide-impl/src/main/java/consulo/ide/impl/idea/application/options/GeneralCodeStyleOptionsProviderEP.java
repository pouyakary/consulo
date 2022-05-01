/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package consulo.ide.impl.idea.application.options;

import consulo.application.Application;
import consulo.component.extension.ExtensionPointName;
import consulo.ide.impl.options.ApplicationConfigurableEP;

import jakarta.inject.Inject;

/**
 * @author Dennis.Ushakov
 */
public class GeneralCodeStyleOptionsProviderEP extends ApplicationConfigurableEP<GeneralCodeStyleOptionsProvider> {
  public static final ExtensionPointName<GeneralCodeStyleOptionsProviderEP> EP_NAME = ExtensionPointName.create("consulo.generalCodeStyleOptionsProvider");

  @Inject
  public GeneralCodeStyleOptionsProviderEP(Application application) {
    super(application);
  }
}
