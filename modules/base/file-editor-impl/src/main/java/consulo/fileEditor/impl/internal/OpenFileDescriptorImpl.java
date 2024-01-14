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
package consulo.fileEditor.impl.internal;

import consulo.codeEditor.*;
import consulo.dataContext.DataContext;
import consulo.dataContext.DataManager;
import consulo.document.FileDocumentManager;
import consulo.document.LazyRangeMarkerFactory;
import consulo.document.RangeMarker;
import consulo.document.util.TextRange;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.TextEditor;
import consulo.navigation.Navigatable;
import consulo.navigation.OpenFileDescriptor;
import consulo.project.Project;
import consulo.project.ui.internal.ProjectIdeFocusManager;
import consulo.project.ui.view.FileSelectInContext;
import consulo.project.ui.view.SelectInContext;
import consulo.project.ui.view.SelectInManager;
import consulo.project.ui.view.SelectInTarget;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import consulo.virtualFileSystem.fileType.INativeFileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class OpenFileDescriptorImpl implements Navigatable, OpenFileDescriptor {
  /**
   * Tells descriptor to navigate in specific editor rather than file editor in main IDEA window.
   * For example if you want to navigate in editor embedded into modal dialog, you should provide this data.
   */
  public static final Key<Editor> NAVIGATE_IN_EDITOR = Key.create("NAVIGATE_IN_EDITOR");

  private final Project myProject;
  private final VirtualFile myFile;
  private final int myLogicalLine;
  private final int myLogicalColumn;
  private final int myOffset;
  private final RangeMarker myRangeMarker;

  private boolean myUseCurrentWindow = false;
  private ScrollType myScrollType = ScrollType.CENTER;

  public OpenFileDescriptorImpl(@Nonnull Project project, @Nonnull VirtualFile file, int offset) {
    this(project, file, -1, -1, offset, false);
  }

  public OpenFileDescriptorImpl(@Nonnull Project project, @Nonnull VirtualFile file, int logicalLine, int logicalColumn) {
    this(project, file, logicalLine, logicalColumn, -1, false);
  }

  public OpenFileDescriptorImpl(@Nonnull Project project,
                                @Nonnull VirtualFile file,
                                int logicalLine,
                                int logicalColumn,
                                boolean persistent) {
    this(project, file, logicalLine, logicalColumn, -1, persistent);
  }

  public OpenFileDescriptorImpl(@Nonnull Project project, @Nonnull VirtualFile file) {
    this(project, file, -1, -1, -1, false);
  }

  public OpenFileDescriptorImpl(@Nonnull Project project,
                                @Nonnull VirtualFile file,
                                int logicalLine,
                                int logicalColumn,
                                int offset,
                                boolean persistent) {
    myProject = project;
    myFile = file;
    myLogicalLine = logicalLine;
    myLogicalColumn = logicalColumn;
    myOffset = offset;
    if (offset >= 0) {
      myRangeMarker = LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, offset);
    }
    else if (logicalLine >= 0) {
      myRangeMarker =
        LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, logicalLine, Math.max(0, logicalColumn), persistent);
    }
    else {
      myRangeMarker = null;
    }
  }

  @Override
  @Nonnull
  public VirtualFile getFile() {
    return myFile;
  }

  @Nullable
  public RangeMarker getRangeMarker() {
    return myRangeMarker;
  }

  @Override
  public int getOffset() {
    return myRangeMarker != null && myRangeMarker.isValid() ? myRangeMarker.getStartOffset() : myOffset;
  }

  @Override
  public int getLine() {
    return myLogicalLine;
  }

  @Override
  public int getColumn() {
    return myLogicalColumn;
  }

  @Override
  public boolean isValid() {
    RangeMarker rangeMarker = getRangeMarker();
    return rangeMarker == null || rangeMarker.isValid();
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (!canNavigate()) {
      throw new IllegalStateException("target not valid");
    }

    if (!myFile.isDirectory() && navigateInEditorOrNativeApp(requestFocus)) return;

    navigateInProjectView(requestFocus);
  }

  private boolean navigateInEditorOrNativeApp(boolean requestFocus) {
    FileType type = FileTypeRegistry.getInstance().getKnownFileTypeOrAssociate(myFile, myProject);
    if (type == null || !myFile.isValid()) return false;

    if (type instanceof INativeFileType) {
      return ((INativeFileType)type).openFileInAssociatedApplication(myProject, myFile);
    }

    return navigateInEditor(requestFocus);
  }

  private boolean navigateInRequestedEditor() {
    DataContext ctx = DataManager.getInstance().getDataContext();
    Editor e = ctx.getData(NAVIGATE_IN_EDITOR);
    if (e == null) return false;
    if (!Objects.equals(FileDocumentManager.getInstance().getFile(e.getDocument()), myFile)) return false;

    navigateIn(e);
    return true;
  }

  public boolean navigateInEditor(boolean requestFocus) {
    return navigateInRequestedEditor() || navigateInAnyFileEditor(requestFocus);
  }

  private boolean navigateInAnyFileEditor(boolean focusEditor) {
    List<FileEditor> editors = FileEditorManager.getInstance(myProject).openEditor(this, focusEditor);
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        Editor e = ((TextEditor)editor).getEditor();
        unfoldCurrentLine(e);
        if (focusEditor) {
          if (myProject.getApplication().isSwingApplication()) {
            ProjectIdeFocusManager.getInstance(myProject).requestFocus(e.getContentComponent(), true);
          }
        }
      }
    }
    return !editors.isEmpty();
  }

  private void navigateInProjectView(boolean requestFocus) {
    SelectInContext context = new FileSelectInContext(myProject, myFile);

    for (SelectInTarget target : SelectInManager.getInstance(myProject).getTargets()) {
      if (target.canSelect(context)) {
        target.selectIn(context, requestFocus);
        return;
      }
    }
  }

  @Nonnull
  @Override
  public CompletableFuture<?> navigateAsync(boolean requestFocus) {
    if (!canNavigate()) {
      return CompletableFuture.failedFuture(new IllegalStateException("target not valid"));
    }

    // if its directory - just navigate in project view
    if (myFile.isDirectory()) {
      return navigateInProjectViewAsync(requestFocus);
    }

    return navigateInEditorOrNativeAppAsync(requestFocus);
  }

  @Nonnull
  private CompletableFuture<Boolean> navigateInEditorOrNativeAppAsync(boolean requestFocus) {
    FileType type = FileTypeRegistry.getInstance().getKnownFileTypeOrAssociate(myFile, myProject);
    if (type == null || !myFile.isValid()) return CompletableFuture.completedFuture(false);

    if (type instanceof INativeFileType) {
      return ((INativeFileType)type).openInExternalApplication(myProject, myFile);
    }

    return navigateInEditorAsync(requestFocus);
  }

  @Nonnull
  public CompletableFuture<Boolean> navigateInEditorAsync(boolean requestFocus) {
    return navigateInRequestedEditorAsync()
      .thenCompose(result -> {
        if (result) {
          return CompletableFuture.completedFuture(result);
        }

        return navigateInAnyFileEditorAsync(requestFocus);
      });
  }

  private CompletableFuture<Boolean> navigateInRequestedEditorAsync() {
    return DataManager.getInstance().getDataContextFromFocusAsync().handle((dataContext, throwable) -> {
      Editor editor = dataContext.getData(NAVIGATE_IN_EDITOR);
      if (editor == null) {
        return false;
      }

      if (!Objects.equals(FileDocumentManager.getInstance().getFile(editor.getDocument()), myFile)) {
        return false;
      }

      navigateIn(editor);
      return true;
    });
  }

  private CompletableFuture<Boolean> navigateInAnyFileEditorAsync(boolean focusEditor) {
    return FileEditorManager.getInstance(myProject)
                            .openFileAsync(myFile, focusEditor, myProject.getUIAccess())
                            .handleAsync((fileEditors, throwable) -> {
                              if (fileEditors == null) {
                                return false;
                              }

                              for (FileEditor editor : fileEditors) {
                                if (editor instanceof TextEditor) {
                                  Editor e = ((TextEditor)editor).getEditor();
                                  unfoldCurrentLine(e);
                                  if (focusEditor) {
                                    if (myProject.getApplication().isSwingApplication()) {
                                      ProjectIdeFocusManager.getInstance(myProject).requestFocus(e.getContentComponent(), true);
                                    }
                                  }
                                }
                              }
                              return !fileEditors.isEmpty();
                            }, myProject.getUIAccess());
  }

  @Nonnull
  private CompletableFuture<?> navigateInProjectViewAsync(boolean requestFocus) {
    SelectInContext context = new FileSelectInContext(myProject, myFile);

    for (SelectInTarget target : SelectInManager.getInstance(myProject).getTargets()) {
      if (target.canSelect(context)) {
        return target.selectInAsync(context, requestFocus);
      }
    }

    return CompletableFuture.completedFuture(null);
  }

  public void navigateIn(@Nonnull Editor e) {
    final int offset = getOffset();
    CaretModel caretModel = e.getCaretModel();
    boolean caretMoved = false;
    if (myLogicalLine >= 0) {
      LogicalPosition pos = new LogicalPosition(myLogicalLine, Math.max(myLogicalColumn, 0));
      if (offset < 0 || offset == e.logicalPositionToOffset(pos)) {
        caretModel.removeSecondaryCarets();
        caretModel.moveToLogicalPosition(pos);
        caretMoved = true;
      }
    }
    if (!caretMoved && offset >= 0) {
      caretModel.removeSecondaryCarets();
      caretModel.moveToOffset(Math.min(offset, e.getDocument().getTextLength()));
      caretMoved = true;
    }

    if (caretMoved) {
      e.getSelectionModel().removeSelection();
      scrollToCaret(e);
      unfoldCurrentLine(e);
    }
  }

  private static void unfoldCurrentLine(@Nonnull final Editor editor) {
    final FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
    final TextRange range = getRangeToUnfoldOnNavigation(editor);
    editor.getFoldingModel().runBatchFoldingOperation(() -> {
      for (FoldRegion region : allRegions) {
        if (!region.isExpanded() && range.intersects(TextRange.create(region))) {
          region.setExpanded(true);
        }
      }
    });
  }

  @Nonnull
  public static TextRange getRangeToUnfoldOnNavigation(@Nonnull Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
    int line = editor.getDocument().getLineNumber(offset);
    int start = editor.getDocument().getLineStartOffset(line);
    int end = editor.getDocument().getLineEndOffset(line);
    return new TextRange(start, end);
  }

  private void scrollToCaret(@Nonnull Editor e) {
    e.getScrollingModel().scrollToCaret(myScrollType);
  }

  @Override
  public boolean canNavigate() {
    return myFile.isValid();
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Override
  @Nonnull
  public Project getProject() {
    return myProject;
  }

  public OpenFileDescriptorImpl setUseCurrentWindow(boolean search) {
    myUseCurrentWindow = search;
    return this;
  }

  @Override
  public boolean isUseCurrentWindow() {
    return myUseCurrentWindow;
  }

  public void setScrollType(@Nonnull ScrollType scrollType) {
    myScrollType = scrollType;
  }

  public void dispose() {
    if (myRangeMarker != null) {
      myRangeMarker.dispose();
    }
  }

  @Override
  public int compareTo(OpenFileDescriptor o) {
    int i = myProject.getName().compareTo(((Project)o.getProject()).getName());
    if (i != 0) return i;
    i = myFile.getName().compareTo(o.getFile().getName());
    if (i != 0) return i;
    RangeMarker rangeMarker = ((OpenFileDescriptorImpl)o).getRangeMarker();
    if (myRangeMarker != null) {
      if (rangeMarker == null) return 1;
      i = myRangeMarker.getStartOffset() - rangeMarker.getStartOffset();
      if (i != 0) return i;
      return myRangeMarker.getEndOffset() - rangeMarker.getEndOffset();
    }
    return rangeMarker == null ? 0 : -1;
  }
}
