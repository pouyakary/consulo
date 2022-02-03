/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.actions;

import com.intellij.diff.contents.DiffContentBase;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.LineCol;
import com.intellij.openapi.command.undo.UndoManager;
import consulo.document.Document;
import com.intellij.openapi.editor.EditorFactory;
import consulo.document.RangeMarker;
import consulo.document.event.DocumentEvent;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.virtualFileSystem.VirtualFile;
import consulo.navigation.Navigatable;
import com.intellij.util.LineSeparator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.nio.charset.Charset;

/**
 * Represents sub text of other content.
 */
public class DocumentFragmentContent extends DiffContentBase implements DocumentContent {
  // TODO: reuse DocumentWindow ?

  @Nonnull
  private final DocumentContent myOriginal;
  @Nonnull
  private final RangeMarker myRangeMarker;

  @Nonnull
  private final MyDocumentsSynchronizer mySynchronizer;

  private int myAssignments = 0;

  public DocumentFragmentContent(@Nullable Project project, @Nonnull DocumentContent original, @Nonnull TextRange range) {
    this(project, original, createRangeMarker(original.getDocument(), range));
  }

  public DocumentFragmentContent(@Nullable Project project, @Nonnull DocumentContent original, @Nonnull RangeMarker rangeMarker) {
    myOriginal = original;
    myRangeMarker = rangeMarker;

    Document document1 = myOriginal.getDocument();

    Document document2 = EditorFactory.getInstance().createDocument("");
    document2.putUserData(UndoManager.ORIGINAL_DOCUMENT, document1);

    mySynchronizer = new MyDocumentsSynchronizer(project, myRangeMarker, document1, document2);
  }

  @Nonnull
  private static RangeMarker createRangeMarker(@Nonnull Document document, @Nonnull TextRange range) {
    RangeMarker rangeMarker = document.createRangeMarker(range.getStartOffset(), range.getEndOffset(), true);
    rangeMarker.setGreedyToLeft(true);
    rangeMarker.setGreedyToRight(true);
    return rangeMarker;
  }

  @Nonnull
  @Override
  public Document getDocument() {
    return mySynchronizer.getDocument2();
  }

  @Nullable
  @Override
  public VirtualFile getHighlightFile() {
    return myOriginal.getHighlightFile();
  }

  @Nullable
  @Override
  public Navigatable getNavigatable(@Nonnull LineCol position) {
    if (!myRangeMarker.isValid()) return null;
    int offset = position.toOffset(getDocument());
    int originalOffset = offset + myRangeMarker.getStartOffset();
    LineCol originalPosition = LineCol.fromOffset(myOriginal.getDocument(), originalOffset);
    return myOriginal.getNavigatable(originalPosition);
  }

  @javax.annotation.Nullable
  @Override
  public LineSeparator getLineSeparator() {
    return null;
  }

  @javax.annotation.Nullable
  @Override
  public Charset getCharset() {
    return null;
  }

  @Nullable
  @Override
  public FileType getContentType() {
    return myOriginal.getContentType();
  }

  @javax.annotation.Nullable
  @Override
  public Navigatable getNavigatable() {
    return getNavigatable(new LineCol(0));
  }

  @Override
  public void onAssigned(boolean isAssigned) {
    if (isAssigned) {
      if (myAssignments == 0) mySynchronizer.startListen();
      myAssignments++;
    }
    else {
      myAssignments--;
      if (myAssignments == 0) mySynchronizer.stopListen();
    }
    assert myAssignments >= 0;
  }

  private static class MyDocumentsSynchronizer extends DocumentsSynchronizer {
    @Nonnull
    private final RangeMarker myRangeMarker;

    public MyDocumentsSynchronizer(@javax.annotation.Nullable Project project,
                                   @Nonnull RangeMarker range,
                                   @Nonnull Document document1,
                                   @Nonnull Document document2) {
      super(project, document1, document2);
      myRangeMarker = range;
    }

    @Override
    protected void onDocumentChanged1(@Nonnull DocumentEvent event) {
      if (!myRangeMarker.isValid()) {
        myDocument2.setReadOnly(false);
        replaceString(myDocument2, 0, myDocument2.getTextLength(), "Invalid selection range");
        myDocument2.setReadOnly(true);
        return;
      }
      CharSequence newText = myDocument1.getCharsSequence().subSequence(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset());
      replaceString(myDocument2, 0, myDocument2.getTextLength(), newText);
    }

    @Override
    protected void onDocumentChanged2(@Nonnull DocumentEvent event) {
      if (!myRangeMarker.isValid()) return;
      if (!myDocument1.isWritable()) return;

      CharSequence newText = event.getNewFragment();
      int originalOffset = event.getOffset() + myRangeMarker.getStartOffset();
      int originalEnd = originalOffset + event.getOldLength();
      replaceString(myDocument1, originalOffset, originalEnd, newText);
    }

    @Override
    public void startListen() {
      if (myRangeMarker.isValid()) {
        myDocument2.setReadOnly(false);
        CharSequence nexText = myDocument1.getCharsSequence().subSequence(myRangeMarker.getStartOffset(), myRangeMarker.getEndOffset());
        replaceString(myDocument2, 0, myDocument2.getTextLength(), nexText);
        myDocument2.setReadOnly(!myDocument1.isWritable());
      }
      else {
        myDocument2.setReadOnly(false);
        replaceString(myDocument2, 0, myDocument2.getTextLength(), "Invalid selection range");
        myDocument2.setReadOnly(true);
      }
      super.startListen();
    }
  }
}
