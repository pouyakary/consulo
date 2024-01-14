// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package consulo.ide.impl.idea.openapi.editor.impl;

import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.*;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.document.DocumentRunnable;
import consulo.document.ReadOnlyFragmentModificationException;
import consulo.undoRedo.internal.CommandProcessorEx;
import consulo.undoRedo.internal.CommandToken;
import consulo.ide.impl.idea.openapi.editor.EditorModificationUtil;
import consulo.project.Project;
import consulo.undoRedo.CommandProcessor;
import consulo.undoRedo.UndoConfirmationPolicy;

import jakarta.annotation.Nonnull;

public class DefaultRawTypedHandler implements TypedActionHandlerEx {
  private final CommandProcessorEx myCommandProcessor;

  private final TypedAction myAction;
  private CommandToken myCurrentCommandToken;
  private boolean myInOuterCommand;

  DefaultRawTypedHandler(TypedAction action, CommandProcessor commandProcessor) {
    myAction = action;
    myCommandProcessor = (CommandProcessorEx)commandProcessor;
  }

  @Override
  public void beforeExecute(@Nonnull Editor editor, char c, @Nonnull DataContext context, @Nonnull ActionPlan plan) {
    if (editor.isViewer() || !editor.getDocument().isWritable()) return;

    TypedActionHandler handler = myAction.getHandler();

    if (handler instanceof TypedActionHandlerEx) {
      ((TypedActionHandlerEx)handler).beforeExecute(editor, c, context, plan);
    }
  }

  @Override
  public void execute(@Nonnull final Editor editor, final char charTyped, @Nonnull final DataContext dataContext) {
    Project project = dataContext.getData(Project.KEY);
    if (myCurrentCommandToken != null) {
      throw new IllegalStateException("Unexpected reentrancy of DefaultRawTypedHandler");
    }
    myCurrentCommandToken = myCommandProcessor.startCommand(project, "", editor.getDocument(), UndoConfirmationPolicy.DEFAULT);
    myInOuterCommand = myCurrentCommandToken == null;
    try {
      if (!EditorModificationUtil.requestWriting(editor)) {
        return;
      }
      ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(editor.getDocument(), editor.getProject()) {
        @Override
        public void run() {
          Document doc = editor.getDocument();
          doc.startGuardedBlockChecking();
          try {
            myAction.getHandler().execute(editor, charTyped, dataContext);
          }
          catch (ReadOnlyFragmentModificationException e) {
            EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
          }
          finally {
            doc.stopGuardedBlockChecking();
          }
        }
      });
    }
    finally {
      if (!myInOuterCommand) {
        myCommandProcessor.finishCommand(myCurrentCommandToken, null);
        myCurrentCommandToken = null;
      }
      myInOuterCommand = false;
    }
  }

  public void beginUndoablePostProcessing() {
    if (myInOuterCommand) {
      return;
    }
    if (myCurrentCommandToken == null) {
      throw new IllegalStateException("Not in a typed action at this time");
    }
    CommandProcessorEx commandProcessorEx = (CommandProcessorEx)CommandProcessor.getInstance();
    Project project = myCurrentCommandToken.getProject();
    commandProcessorEx.finishCommand(myCurrentCommandToken, null);
    myCurrentCommandToken = commandProcessorEx.startCommand(project, "", null, UndoConfirmationPolicy.DEFAULT);
  }
}
