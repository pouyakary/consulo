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
package com.intellij.usages;

import com.intellij.ide.SelectInEditorManager;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.util.Comparing;
import consulo.document.util.ProperTextRange;
import com.intellij.reference.SoftReference;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.impl.rules.UsageType;
import com.intellij.usages.rules.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.NotNullFunction;
import consulo.application.AccessRule;
import consulo.application.ApplicationManager;
import consulo.application.util.function.Processor;
import consulo.application.util.function.ThrowableComputable;
import consulo.document.Document;
import consulo.document.util.Segment;
import consulo.document.util.TextRange;
import consulo.document.util.UnfairTextRange;
import consulo.codeEditor.Editor;
import consulo.codeEditor.markup.TextAttributes;
import consulo.codeEditor.util.TextAttributesUtil;
import consulo.language.file.inject.VirtualFileWindow;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.*;
import consulo.language.util.IncorrectOperationException;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.image.Image;
import consulo.util.collection.SmartList;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;

import javax.annotation.Nonnull;
import java.awt.*;
import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author max
 */
public class UsageInfo2UsageAdapter
        implements UsageInModule, UsageInfoAdapter, UsageInLibrary, UsageInFile, PsiElementUsage, MergeableUsage, Comparable<UsageInfo2UsageAdapter>, RenameableUsage, TypeSafeDataProvider,
                   UsagePresentation {
  public static final NotNullFunction<UsageInfo, Usage> CONVERTER = UsageInfo2UsageAdapter::new;
  private static final Comparator<UsageInfo> BY_NAVIGATION_OFFSET = Comparator.comparingInt(UsageInfo::getNavigationOffset);

  private final UsageInfo myUsageInfo;
  @Nonnull
  private Object myMergedUsageInfos; // contains all merged infos, including myUsageInfo. Either UsageInfo or UsageInfo[]
  private final int myLineNumber;
  private final int myOffset;
  protected Image myIcon;
  private volatile Reference<TextChunk[]> myTextChunks; // allow to be gced and recreated on-demand because it requires a lot of memory
  private volatile UsageType myUsageType;

  public UsageInfo2UsageAdapter(@Nonnull final UsageInfo usageInfo) {
    myUsageInfo = usageInfo;
    myMergedUsageInfos = usageInfo;

    ThrowableComputable<Point, RuntimeException> action = () -> {
      PsiElement element = getElement();
      PsiFile psiFile = usageInfo.getFile();
      Document document = psiFile == null ? null : PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);

      int offset;
      int lineNumber;
      if (document == null) {
        // element over light virtual file
        offset = element == null ? 0 : element.getTextOffset();
        lineNumber = -1;
      }
      else {
        int startOffset = myUsageInfo.getNavigationOffset();
        if (startOffset == -1) {
          offset = element == null ? 0 : element.getTextOffset();
          lineNumber = -1;
        }
        else {
          offset = -1;
          lineNumber = getLineNumber(document, startOffset);
        }
      }
      return new Point(offset, lineNumber);
    };
    Point data = AccessRule.read(action);
    myOffset = data.x;
    myLineNumber = data.y;
    myModificationStamp = getCurrentModificationStamp();
  }

  private static int getLineNumber(@Nonnull Document document, final int startOffset) {
    if (document.getTextLength() == 0) return 0;
    if (startOffset >= document.getTextLength()) return document.getLineCount();
    return document.getLineNumber(startOffset);
  }

  @Nonnull
  private TextChunk[] initChunks() {
    PsiFile psiFile = getPsiFile();
    Document document = psiFile == null ? null : PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
    TextChunk[] chunks;
    if (document == null) {
      // element over light virtual file
      PsiElement element = getElement();
      if (element == null) {
        chunks = new TextChunk[]{new TextChunk(TextAttributesUtil.toTextAttributes(SimpleTextAttributes.ERROR_ATTRIBUTES), UsageViewBundle.message("node.invalid"))};
      }
      else {
        chunks = new TextChunk[]{new TextChunk(new TextAttributes(), element.getText())};
      }
    }
    else {
      chunks = ChunkExtractor.extractChunks(psiFile, this);
    }

    myTextChunks = new SoftReference<>(chunks);
    return chunks;
  }

  @Override
  @Nonnull
  public UsagePresentation getPresentation() {
    return this;
  }

  @Override
  public boolean isValid() {
    PsiElement element = getElement();
    if (element == null || !element.isValid()) {
      return false;
    }
    for (UsageInfo usageInfo : getMergedInfos()) {
      if (usageInfo.isValid()) return true;
    }
    return false;
  }

  @Override
  public boolean isReadOnly() {
    PsiFile psiFile = getPsiFile();
    return psiFile == null || psiFile.isValid() && !psiFile.isWritable();
  }

  @Override
  @javax.annotation.Nullable
  public FileEditorLocation getLocation() {
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;
    FileEditor editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(virtualFile);
    if (!(editor instanceof TextEditor)) return null;

    Segment segment = getUsageInfo().getSegment();
    if (segment == null) return null;
    return new TextEditorLocation(segment.getStartOffset(), (TextEditor)editor);
  }

  @Override
  public void selectInEditor() {
    if (!isValid()) return;
    Editor editor = openTextEditor(true);
    Segment marker = getFirstSegment();
    if (marker != null) {
      editor.getSelectionModel().setSelection(marker.getStartOffset(), marker.getEndOffset());
    }
  }

  @Override
  public void highlightInEditor() {
    if (!isValid()) return;

    Segment marker = getFirstSegment();
    if (marker != null) {
      SelectInEditorManager.getInstance(getProject()).selectInEditor(getFile(), marker.getStartOffset(), marker.getEndOffset(), false, false);
    }
  }

  private Segment getFirstSegment() {
    return getUsageInfo().getSegment();
  }

  // must iterate in start offset order
  public boolean processRangeMarkers(@Nonnull Processor<Segment> processor) {
    for (UsageInfo usageInfo : getMergedInfos()) {
      Segment segment = usageInfo.getSegment();
      if (segment != null && !processor.process(segment)) {
        return false;
      }
    }
    return true;
  }

  public Document getDocument() {
    PsiFile file = getUsageInfo().getFile();
    if (file == null) return null;
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }


  @Override
  public void navigate(boolean focus) {
    if (canNavigate()) {
      openTextEditor(focus);
    }
  }

  public Editor openTextEditor(boolean focus) {
    return FileEditorManager.getInstance(getProject()).openTextEditor(getDescriptor(), focus);
  }

  @Override
  public boolean canNavigate() {
    VirtualFile file = getFile();
    return file != null && file.isValid();
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  private OpenFileDescriptor getDescriptor() {
    VirtualFile file = getFile();
    if (file == null) return null;
    Segment range = getNavigationRange();
    if (range != null && file instanceof VirtualFileWindow && range.getStartOffset() >= 0) {
      // have to use injectedToHost(TextRange) to calculate right offset in case of multiple shreds
      range = ((VirtualFileWindow)file).getDocumentWindow().injectedToHost(TextRange.create(range));
      file = ((VirtualFileWindow)file).getDelegate();
    }
    return new OpenFileDescriptor(getProject(), file, range == null ? getNavigationOffset() : range.getStartOffset());
  }

  @Override
  public int getNavigationOffset() {
    Document document = getDocument();
    if (document == null) return -1;
    int offset = getUsageInfo().getNavigationOffset();
    if (offset == -1) offset = myOffset;
    if (offset >= document.getTextLength()) {
      int line = Math.max(0, Math.min(myLineNumber, document.getLineCount() - 1));
      offset = document.getLineStartOffset(line);
    }
    return offset;
  }

  private Segment getNavigationRange() {
    Document document = getDocument();
    if (document == null) return null;
    Segment range = getUsageInfo().getNavigationRange();
    if (range == null) {
      ProperTextRange rangeInElement = getUsageInfo().getRangeInElement();
      range = myOffset < 0 ? new UnfairTextRange(-1, -1) : rangeInElement == null ? TextRange.from(myOffset, 1) : rangeInElement.shiftRight(myOffset);
    }
    if (range.getEndOffset() >= document.getTextLength()) {
      int line = Math.max(0, Math.min(myLineNumber, document.getLineCount() - 1));
      range = TextRange.from(document.getLineStartOffset(line), 1);
    }
    return range;
  }

  @Nonnull
  private Project getProject() {
    return getUsageInfo().getProject();
  }

  @Override
  public String toString() {
    TextChunk[] textChunks = getPresentation().getText();
    StringBuilder result = new StringBuilder();
    for (int j = 0; j < textChunks.length; j++) {
      if (j > 0) result.append("|");
      TextChunk textChunk = textChunks[j];
      result.append(textChunk);
    }

    return result.toString();
  }

  @Override
  public Module getModule() {
    if (!isValid()) return null;
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
    return fileIndex.getModuleForFile(virtualFile);
  }

  @Override
  public OrderEntry getLibraryEntry() {
    if (!isValid()) return null;
    PsiFile psiFile = getPsiFile();
    VirtualFile virtualFile = getFile();
    if (virtualFile == null) return null;

    ProjectRootManager projectRootManager = ProjectRootManager.getInstance(getProject());
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();

    if (psiFile instanceof PsiCompiledElement || fileIndex.isInLibrarySource(virtualFile)) {
      List<OrderEntry> orders = fileIndex.getOrderEntriesForFile(virtualFile);
      for (OrderEntry order : orders) {
        if (order instanceof LibraryOrderEntry || order instanceof ModuleExtensionWithSdkOrderEntry) {
          return order;
        }
      }
    }

    return null;
  }

  @Override
  public VirtualFile getFile() {
    return getUsageInfo().getVirtualFile();
  }

  private PsiFile getPsiFile() {
    return getUsageInfo().getFile();
  }

  @Override
  @Nonnull
  public String getPath() {
    return getFile().getPath();
  }

  @Override
  public int getLine() {
    return myLineNumber;
  }

  @Override
  public boolean merge(@Nonnull MergeableUsage other) {
    if (!(other instanceof UsageInfo2UsageAdapter)) return false;
    UsageInfo2UsageAdapter u2 = (UsageInfo2UsageAdapter)other;
    assert u2 != this;
    if (myLineNumber != u2.myLineNumber || !Comparing.equal(getFile(), u2.getFile())) return false;
    UsageInfo[] merged = ArrayUtil.mergeArrays(getMergedInfos(), u2.getMergedInfos());
    myMergedUsageInfos = merged.length == 1 ? merged[0] : merged;
    Arrays.sort(getMergedInfos(), BY_NAVIGATION_OFFSET);
    myTextChunks = null; // chunks will be rebuilt lazily (IDEA-126048)
    return true;
  }

  @Override
  public void reset() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myMergedUsageInfos = myUsageInfo;
    initChunks();
  }

  @Override
  public final PsiElement getElement() {
    return getUsageInfo().getElement();
  }

  public PsiReference getReference() {
    return getElement().getReference();
  }

  @Override
  public boolean isNonCodeUsage() {
    return getUsageInfo().isNonCodeUsage;
  }

  @Nonnull
  public UsageInfo getUsageInfo() {
    return myUsageInfo;
  }

  // by start offset
  @Override
  public int compareTo(@Nonnull final UsageInfo2UsageAdapter o) {
    return getUsageInfo().compareToByStartOffset(o.getUsageInfo());
  }

  @Override
  public void rename(String newName) throws IncorrectOperationException {
    final PsiReference reference = getUsageInfo().getReference();
    assert reference != null : this;
    reference.handleElementRename(newName);
  }

  @Nonnull
  public static UsageInfo2UsageAdapter[] convert(@Nonnull UsageInfo[] usageInfos) {
    UsageInfo2UsageAdapter[] result = new UsageInfo2UsageAdapter[usageInfos.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = new UsageInfo2UsageAdapter(usageInfos[i]);
    }

    return result;
  }

  @Override
  public void calcData(final Key<?> key, final DataSink sink) {
    if (key == UsageView.USAGE_INFO_KEY) {
      sink.put(UsageView.USAGE_INFO_KEY, getUsageInfo());
    }
    if (key == UsageView.USAGE_INFO_LIST_KEY) {
      List<UsageInfo> list = Arrays.asList(getMergedInfos());
      sink.put(UsageView.USAGE_INFO_LIST_KEY, list);
    }
  }

  @Override
  @Nonnull
  public UsageInfo[] getMergedInfos() {
    Object infos = myMergedUsageInfos;
    return infos instanceof UsageInfo ? new UsageInfo[]{(UsageInfo)infos} : (UsageInfo[])infos;
  }

  private long myModificationStamp;

  private long getCurrentModificationStamp() {
    final PsiFile containingFile = getPsiFile();
    return containingFile == null ? -1L : containingFile.getViewProvider().getModificationStamp();
  }

  @Override
  @Nonnull
  public TextChunk[] getText() {
    TextChunk[] chunks = SoftReference.dereference(myTextChunks);
    final long currentModificationStamp = getCurrentModificationStamp();
    boolean isModified = currentModificationStamp != myModificationStamp;
    if (chunks == null || isValid() && isModified) {
      // the check below makes sense only for valid PsiElement
      chunks = initChunks();
      myModificationStamp = currentModificationStamp;
    }
    return chunks;
  }

  @Override
  @Nonnull
  public String getPlainText() {
    int startOffset = getNavigationOffset();
    final PsiElement element = getElement();
    if (element != null && startOffset != -1) {
      final Document document = getDocument();
      if (document != null) {
        int lineNumber = document.getLineNumber(startOffset);
        int lineStart = document.getLineStartOffset(lineNumber);
        int lineEnd = document.getLineEndOffset(lineNumber);
        String prefixSuffix = null;

        if (lineEnd - lineStart > ChunkExtractor.MAX_LINE_LENGTH_TO_SHOW) {
          prefixSuffix = "...";
          lineStart = Math.max(startOffset - ChunkExtractor.OFFSET_BEFORE_TO_SHOW_WHEN_LONG_LINE, lineStart);
          lineEnd = Math.min(startOffset + ChunkExtractor.OFFSET_AFTER_TO_SHOW_WHEN_LONG_LINE, lineEnd);
        }
        String s = document.getCharsSequence().subSequence(lineStart, lineEnd).toString();
        if (prefixSuffix != null) s = prefixSuffix + s + prefixSuffix;
        return s;
      }
    }
    return UsageViewBundle.message("node.invalid");
  }

  @Override
  public Image getIcon() {
    Image icon = myIcon;
    if (icon == null) {
      PsiElement psiElement = getElement();
      myIcon = icon = psiElement != null && psiElement.isValid() ? IconDescriptorUpdaters.getIcon(psiElement, 0) : null;
    }
    return icon;
  }

  private boolean isFindInPathUsage(PsiElement psiElement) {
    return psiElement instanceof PsiFile && getUsageInfo().getPsiFileRange() != null;
  }

  @Override
  public String getTooltipText() {
    return myUsageInfo.getTooltipText();
  }

  @javax.annotation.Nullable
  public UsageType getUsageType() {
    UsageType usageType = myUsageType;

    if (usageType == null) {
      usageType = UsageType.UNCLASSIFIED;
      PsiFile file = getPsiFile();

      if (file != null) {
        Segment segment = getFirstSegment();

        if (segment != null) {
          Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
          if (document != null) {
            ChunkExtractor extractor = ChunkExtractor.getExtractor(file);
            SmartList<TextChunk> chunks = new SmartList<>();
            extractor.createTextChunks(this, document.getCharsSequence(), segment.getStartOffset(), segment.getEndOffset(), false, chunks);

            for (TextChunk chunk : chunks) {
              UsageType chunkUsageType = chunk.getType();
              if (chunkUsageType != null) {
                usageType = chunkUsageType;
                break;
              }
            }
          }
        }
      }
      myUsageType = usageType;
    }
    return usageType;
  }
}
