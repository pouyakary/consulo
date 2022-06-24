/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Date: 01-Feb-2008
 */
package consulo.ide.impl.idea.codeInsight.hint;

import consulo.language.OldLanguageExtension;
import consulo.container.plugin.PluginIds;
import consulo.language.editor.ImplementationTextSelectioner;

public class LanguageImplementationTextSelectioner extends OldLanguageExtension<ImplementationTextSelectioner> {
  public static final LanguageImplementationTextSelectioner INSTANCE = new LanguageImplementationTextSelectioner();

  public LanguageImplementationTextSelectioner() {
    super(PluginIds.CONSULO_BASE + ".lang.implementationTextSelectioner", new DefaultImplementationTextSelectioner());
  }
}