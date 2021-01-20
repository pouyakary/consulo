// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import consulo.util.dataholder.Key;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;
import java.awt.*;

abstract class InlayImpl<R extends EditorCustomElementRenderer, T extends InlayImpl> extends RangeMarkerImpl implements Inlay<R> {
  static final Key<Integer> OFFSET_BEFORE_DISPOSAL = Key.create("inlay.offset.before.disposal");

  @Nonnull
  final DesktopEditorImpl myEditor;
  @Nonnull
  final R myRenderer;
  private final boolean myRelatedToPrecedingText;

  int myWidthInPixels;

  @SuppressWarnings("AbstractMethodCallInConstructor")
  InlayImpl(@Nonnull DesktopEditorImpl editor, int offset, boolean relatesToPrecedingText, @Nonnull R renderer) {
    super(editor.getDocument(), offset, offset, false, true);
    myEditor = editor;
    myRelatedToPrecedingText = relatesToPrecedingText;
    myRenderer = renderer;
    doUpdateSize();
    //noinspection unchecked
    getTree().addInterval((T)this, offset, offset, false, false, relatesToPrecedingText, 0);
  }

  abstract RangeMarkerTree<T> getTree();

  @Nonnull
  @Override
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  public void updateSize() {
    int oldWidth = getWidthInPixels();
    int oldHeight = getHeightInPixels();
    doUpdateSize();
    if (oldWidth != getWidthInPixels() || oldHeight != getHeightInPixels()) {
      myEditor.getInlayModel().notifyChanged(this);
    }
    else {
      repaint();
    }
  }

  @Override
  public void repaint() {
    if (isValid() && !myEditor.isDisposed() && !myEditor.getDocument().isInBulkUpdate()) {
      JComponent contentComponent = myEditor.getContentComponent();
      if (contentComponent.isShowing()) {
        Rectangle bounds = getBounds();
        if (bounds != null) {
          if (this instanceof BlockInlayImpl) {
            bounds.width = contentComponent.getWidth();
          }
          contentComponent.repaint(bounds);
        }
      }
    }
  }

  abstract void doUpdateSize();

  @Override
  public void dispose() {
    if (isValid()) {
      int offset = getOffset(); // We want listeners notified after disposal, but want inlay offset to be available at that time
      putUserData(OFFSET_BEFORE_DISPOSAL, offset);
      //noinspection unchecked
      getTree().removeInterval((T)this);
      myEditor.getInlayModel().notifyRemoved(this);
    }
  }

  @Override
  public int getOffset() {
    Integer offsetBeforeDisposal = getUserData(OFFSET_BEFORE_DISPOSAL);
    return offsetBeforeDisposal == null ? getStartOffset() : offsetBeforeDisposal;
  }

  @Override
  public boolean isRelatedToPrecedingText() {
    return myRelatedToPrecedingText;
  }

  abstract Point getPosition();

  @Nullable
  @Override
  public Rectangle getBounds() {
    if (myEditor.getFoldingModel().isOffsetCollapsed(getOffset())) return null;
    Point pos = getPosition();
    return new Rectangle(pos.x, pos.y, getWidthInPixels(), getHeightInPixels());
  }

  @Nonnull
  @Override
  public R getRenderer() {
    return myRenderer;
  }

  @Override
  public int getWidthInPixels() {
    return myWidthInPixels;
  }
}
