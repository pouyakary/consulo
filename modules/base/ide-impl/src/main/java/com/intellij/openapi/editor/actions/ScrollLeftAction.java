/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;

/**
 * @author Denis Zhdanov
 * @since 5/25/12 9:26 AM
 */
public class ScrollLeftAction extends InactiveEditorAction {
  public ScrollLeftAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void execute(Editor editor, DataContext dataContext) {
      EditorActionUtil.scrollRelatively(editor, 0, -1, false);
    }
  }
}
