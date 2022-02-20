// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.util.EditorGotoLineNumberDialog;
import com.intellij.ide.util.GotoLineNumberDialog;
import consulo.application.Application;
import consulo.undoRedo.CommandProcessor;
import consulo.fileEditor.event.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.keymap.KeymapUtil;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorFactory;
import consulo.codeEditor.LogicalPosition;
import consulo.codeEditor.SelectionModel;
import consulo.codeEditor.event.*;
import consulo.project.Project;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.StatusBarWidget;
import com.intellij.ui.UIBundle;
import consulo.ui.ex.util.Alarm;
import com.intellij.util.Consumer;
import consulo.disposer.Disposer;
import consulo.document.Document;
import consulo.document.event.BulkAwareDocumentListener;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public final class PositionPanel extends EditorBasedWidget
        implements StatusBarWidget.Multiframe, StatusBarWidget.TextPresentation, CaretListener, SelectionListener, BulkAwareDocumentListener.Simple, PropertyChangeListener {

  public static final Key<Object> DISABLE_FOR_EDITOR = Key.create("positionPanel.disableForEditor");

  public static final String SPACE = "     ";
  public static final String SEPARATOR = ":";

  private static final int CHAR_COUNT_SYNC_LIMIT = 500_000;
  private static final String CHAR_COUNT_UNKNOWN = "...";

  private Alarm myAlarm;
  private CodePointCountTask myCountTask;

  private String myText;

  public PositionPanel(@Nonnull Project project) {
    super(project);
  }

  @Override
  public void selectionChanged(@Nonnull FileEditorManagerEvent event) {
    updatePosition(getEditor());
  }

  @Override
  @Nonnull
  public String ID() {
    return StatusBar.StandardWidgets.POSITION_PANEL;
  }

  @Override
  public StatusBarWidget copy() {
    return new PositionPanel(getProject());
  }

  @Override
  public WidgetPresentation getPresentation() {
    return this;
  }

  @Override
  @Nonnull
  public String getText() {
    return myText == null ? "" : myText;
  }

  @Override
  public float getAlignment() {
    return Component.CENTER_ALIGNMENT;
  }

  @Override
  public String getTooltipText() {
    final String shortcut = KeymapUtil.getFirstKeyboardShortcutText("GotoLine");
    if (!shortcut.isEmpty()) {
      return UIBundle.message("go.to.line.command.name") + " (" + shortcut + ")";
    }
    return UIBundle.message("go.to.line.command.name");
  }

  @Override
  public Consumer<MouseEvent> getClickConsumer() {
    return mouseEvent -> {
      Project project = getProject();
      Editor editor = getFocusedEditor();
      if (editor == null) return;

      CommandProcessor.getInstance().executeCommand(project, () -> {
        GotoLineNumberDialog dialog = new EditorGotoLineNumberDialog(project, editor);
        dialog.show();
        IdeDocumentHistory.getInstance(project).includeCurrentCommandAsNavigation();
      }, UIBundle.message("go.to.line.command.name"), null);
    };
  }

  @Override
  public void install(@Nonnull StatusBar statusBar) {
    super.install(statusBar);
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addCaretListener(this, this);
    multicaster.addSelectionListener(this, this);
    multicaster.addDocumentListener(this, this);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, this);
    Disposer.register(this, () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(SWING_FOCUS_OWNER_PROPERTY, this));
  }

  @Override
  public void selectionChanged(@Nonnull final SelectionEvent e) {
    Editor editor = e.getEditor();
    if (isFocusedEditor(editor)) updatePosition(editor);
  }

  @Override
  public void caretPositionChanged(@Nonnull final CaretEvent e) {
    Editor editor = e.getEditor();
    // When multiple carets exist in editor, we don't show information about caret positions
    if (editor.getCaretModel().getCaretCount() == 1 && isFocusedEditor(editor)) updatePosition(editor);
  }

  @Override
  public void caretAdded(@Nonnull CaretEvent e) {
    updatePosition(e.getEditor());
  }

  @Override
  public void caretRemoved(@Nonnull CaretEvent e) {
    updatePosition(e.getEditor());
  }

  @Override
  public void afterDocumentChange(@Nonnull Document document) {
    Editor[] editors = EditorFactory.getInstance().getEditors(document);
    for (Editor editor : editors) {
      if (isFocusedEditor(editor)) {
        updatePosition(editor);
        break;
      }
    }
  }

  private boolean isFocusedEditor(Editor editor) {
    // TODO [VISTALL] temp hack
    if (!Application.get().isSwingApplication()) {
      return editor.isShowing();
    }
    Component focusOwner = getFocusedComponent();
    return focusOwner == editor.getContentComponent();
  }

  private void updatePosition(final Editor editor) {
    if (editor == null || DISABLE_FOR_EDITOR.isIn(editor)) {
      myText = "";
    }
    else {
      if (!isOurEditor(editor)) return;
      myText = getPositionText(editor);
    }
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
  }

  private void updateTextWithCodePointCount(int codePointCount) {
    if (myText != null) {
      myText = myText.replace(CHAR_COUNT_UNKNOWN, Integer.toString(codePointCount));
      if (myStatusBar != null) {
        myStatusBar.updateWidget(ID());
      }
    }
  }

  private String getPositionText(@Nonnull Editor editor) {
    myCountTask = null;
    if (!editor.isDisposed() && !myAlarm.isDisposed()) {
      StringBuilder message = new StringBuilder();

      SelectionModel selectionModel = editor.getSelectionModel();
      int caretCount = editor.getCaretModel().getCaretCount();
      if (caretCount > 1) {
        message.append(UIBundle.message("position.panel.caret.count", caretCount));
      }
      else {
        if (selectionModel.hasSelection()) {
          int selectionStart = selectionModel.getSelectionStart();
          int selectionEnd = selectionModel.getSelectionEnd();
          if (selectionEnd > selectionStart) {
            CodePointCountTask countTask = new CodePointCountTask(editor.getDocument().getImmutableCharSequence(), selectionStart, selectionEnd);
            if (countTask.isQuick()) {
              int charCount = countTask.calculate();
              message.append(charCount).append(' ').append(UIBundle.message("position.panel.selected.chars.count", charCount));
            }
            else {
              message.append(CHAR_COUNT_UNKNOWN).append(' ').append(UIBundle.message("position.panel.selected.chars.count", 2));
              myCountTask = countTask;
              myAlarm.cancelAllRequests();
              myAlarm.addRequest(countTask, 0);
            }
            int selectionStartLine = editor.getDocument().getLineNumber(selectionStart);
            int selectionEndLine = editor.getDocument().getLineNumber(selectionEnd);
            if (selectionEndLine > selectionStartLine) {
              message.append(", ");
              message.append(UIBundle.message("position.panel.selected.line.breaks.count", selectionEndLine - selectionStartLine));
            }
            message.append(SPACE);
          }
        }
        LogicalPosition caret = editor.getCaretModel().getLogicalPosition();
        message.append(caret.line + 1).append(SEPARATOR).append(caret.column + 1);
      }

      return message.toString();
    }
    else {
      return "";
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent e) {
    updatePosition(getFocusedEditor());
  }

  private class CodePointCountTask implements Runnable {
    private final CharSequence text;
    private final int startOffset;
    private final int endOffset;

    private CodePointCountTask(CharSequence text, int startOffset, int endOffset) {
      this.text = text;
      this.startOffset = startOffset;
      this.endOffset = endOffset;
    }

    private boolean isQuick() {
      return endOffset - startOffset < CHAR_COUNT_SYNC_LIMIT;
    }

    private int calculate() {
      return Character.codePointCount(text, startOffset, endOffset);
    }

    @Override
    public void run() {
      int count = calculate();
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(() -> {
        if (this == myCountTask) {
          updateTextWithCodePointCount(count);
          myCountTask = null;
        }
      });
    }
  }
}
