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
package consulo.ide.impl.idea.application.options.codeStyle.arrangement.match.tokens;

import consulo.language.codeStyle.CodeStyleBundle;
import consulo.language.codeStyle.arrangement.ArrangementColorsProvider;
import consulo.configurable.ConfigurationException;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.language.codeStyle.arrangement.std.StdArrangementRuleAliasToken;
import consulo.ide.impl.language.codeStyle.arrangement.std.ArrangementStandardSettingsManagerImpl;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import javax.annotation.Nonnull;

import javax.annotation.Nullable;
import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class ArrangementRuleAliasDialog extends DialogWrapper {
  private final ArrangementRuleAliasesListEditor myEditor;
  private boolean myModified;

  public ArrangementRuleAliasDialog(@Nullable Project project,
                                    @Nonnull ArrangementStandardSettingsManagerImpl settingsManager,
                                    @Nonnull ArrangementColorsProvider colorsProvider,
                                    @Nonnull Collection<StdArrangementRuleAliasToken> tokens,
                                    @Nonnull Set<String> tokensInUse) {
    super(project, false);

    final List<StdArrangementRuleAliasToken> tokenList = ContainerUtil.newArrayList(tokens);
    myEditor = new ArrangementRuleAliasesListEditor(settingsManager, colorsProvider, tokenList, tokensInUse);
    if (!tokenList.isEmpty()) {
      myEditor.selectItem(tokenList.get(0));
    }

    setTitle(CodeStyleBundle.message("arrangement.settings.section.rule.custom.token.title"));
    init();
  }

  public boolean isModified() {
    return myModified;
  }

  public Collection<StdArrangementRuleAliasToken> getRuleAliases() {
    return myEditor.getItems();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myEditor.createComponent();
  }

  @Override
  protected void doOKAction() {
    try {
      myModified = myEditor.isModified();
      if (myModified) {
        myEditor.apply();
      }
      super.doOKAction();
    }
    catch (ConfigurationException e) {
      // show error
    }
  }
}
