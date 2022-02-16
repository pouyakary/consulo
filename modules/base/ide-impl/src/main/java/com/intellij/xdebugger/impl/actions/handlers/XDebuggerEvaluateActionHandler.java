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
package com.intellij.xdebugger.impl.actions.handlers;

import consulo.language.Language;
import consulo.language.util.LanguageUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.ui.AppUIUtil;
import consulo.debugger.XDebugSession;
import consulo.debugger.breakpoint.XExpression;
import consulo.debugger.XSourcePosition;
import consulo.debugger.evaluation.EvaluationMode;
import consulo.debugger.evaluation.ExpressionInfo;
import consulo.debugger.evaluation.XDebuggerEditorsProvider;
import consulo.debugger.evaluation.XDebuggerEvaluator;
import consulo.debugger.frame.XStackFrame;
import consulo.debugger.frame.XValue;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * @author nik
 */
public class XDebuggerEvaluateActionHandler extends XDebuggerActionHandler {
  @Override
  protected void perform(@Nonnull final XDebugSession session, final DataContext dataContext) {
    final XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    final XStackFrame stackFrame = session.getCurrentStackFrame();
    final XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
    if (evaluator == null) {
      return;
    }

    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);

    EvaluationMode mode = EvaluationMode.EXPRESSION;
    String selectedText = editor != null ? editor.getSelectionModel().getSelectedText() : null;
    if (selectedText != null) {
      selectedText = evaluator.formatTextForEvaluation(selectedText);
      mode = evaluator.getEvaluationMode(selectedText, editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd(), dataContext.getData(CommonDataKeys.PSI_FILE));
    }
    String text = selectedText;

    if (text == null && editor != null) {
      text = getExpressionText(evaluator, dataContext.getData(CommonDataKeys.PROJECT), editor);
    }

    final VirtualFile file = dataContext.getData(CommonDataKeys.VIRTUAL_FILE);

    if (text == null) {
      XValue value = XDebuggerTreeActionBase.getSelectedValue(dataContext);
      if (value != null) {
        value.calculateEvaluationExpression().doWhenDone(new Consumer<XExpression>() {
          @Override
          public void accept(final XExpression expression) {
            if (expression != null) {
              AppUIUtil.invokeOnEdt(new Runnable() {
                @Override
                public void run() {
                  showDialog(session, file, editorsProvider, stackFrame, evaluator, expression);
                }
              });
            }
          }
        });
        return;
      }
    }

    XExpression expression = XExpressionImpl.fromText(StringUtil.notNullize(text), mode);
    showDialog(session, file, editorsProvider, stackFrame, evaluator, expression);
  }

  private static void showDialog(@Nonnull XDebugSession session,
                                 VirtualFile file,
                                 XDebuggerEditorsProvider editorsProvider,
                                 XStackFrame stackFrame,
                                 XDebuggerEvaluator evaluator,
                                 @Nonnull XExpression expression) {
    if (expression.getLanguage() == null) {
      Language language = null;
      if (stackFrame != null) {
        XSourcePosition position = stackFrame.getSourcePosition();
        if (position != null) {
          language = LanguageUtil.getFileLanguage(position.getFile());
        }
      }
      if (language == null && file != null) {
        language = LanguageUtil.getFileTypeLanguage(file.getFileType());
      }
      expression = new XExpressionImpl(expression.getExpression(), language, expression.getCustomInfo(), expression.getMode());
    }
    new XDebuggerEvaluationDialog(session, editorsProvider, evaluator, expression, stackFrame == null ? null : stackFrame.getSourcePosition()).show();
  }

  @Nullable
  public static String getExpressionText(@Nullable XDebuggerEvaluator evaluator, @Nullable Project project, @Nonnull Editor editor) {
    if (project == null || evaluator == null) {
      return null;
    }

    Document document = editor.getDocument();
    return getExpressionText(evaluator.getExpressionInfoAtOffset(project, document, editor.getCaretModel().getOffset(), true), document);
  }

  @Nullable
  public static String getExpressionText(@Nullable ExpressionInfo expressionInfo, @Nonnull Document document) {
    if (expressionInfo == null) {
      return null;
    }
    String text = expressionInfo.getExpressionText();
    return text == null ? document.getText(expressionInfo.getTextRange()) : text;
  }

  @Nullable
  public static String getDisplayText(@Nullable ExpressionInfo expressionInfo, @Nonnull Document document) {
    if (expressionInfo == null) {
      return null;
    }
    String text = expressionInfo.getDisplayText();
    return text == null ? document.getText(expressionInfo.getTextRange()) : text;
  }

  @Override
  protected boolean isEnabled(final @Nonnull XDebugSession session, final DataContext dataContext) {
    return session.getDebugProcess().getEvaluator() != null;
  }
}
