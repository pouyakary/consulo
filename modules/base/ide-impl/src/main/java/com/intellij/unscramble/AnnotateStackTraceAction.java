// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.unscramble;

import com.intellij.execution.filters.FileHyperlinkInfo;
import consulo.execution.ui.console.HyperlinkInfo;
import com.intellij.execution.impl.EditorHyperlinkSupport;
import consulo.application.AllIcons;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorGutterAction;
import consulo.codeEditor.TextAnnotationGutterProvider;
import consulo.colorScheme.EditorColorKey;
import consulo.colorScheme.EditorFontType;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import consulo.codeEditor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.OpenFileDescriptorImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import consulo.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.annotate.AnnotationSource;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsHistorySession;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import consulo.virtualFileSystem.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import consulo.util.collection.MultiMap;
import com.intellij.util.text.DateFormatUtil;
import consulo.ui.ex.util.MergingUpdateQueue;
import consulo.ui.ex.util.Update;
import com.intellij.vcs.history.VcsHistoryProviderEx;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.color.ColorValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.*;

public class AnnotateStackTraceAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(AnnotateStackTraceAction.class);

  private final EditorHyperlinkSupport myHyperlinks;
  private final Editor myEditor;

  private boolean myIsLoading = false;

  public AnnotateStackTraceAction(@Nonnull Editor editor, @Nonnull EditorHyperlinkSupport hyperlinks) {
    super("Show files modification info", null, AllIcons.Actions.Annotate);
    myHyperlinks = hyperlinks;
    myEditor = editor;
  }

  @Override
  public void update(@Nonnull AnActionEvent e) {
    boolean isShown = myEditor.getGutter().isAnnotationsShown();
    e.getPresentation().setEnabled(!isShown && !myIsLoading);
  }

  @Override
  public void actionPerformed(@Nonnull final AnActionEvent e) {
    myIsLoading = true;

    ProgressManager.getInstance().run(new Task.Backgroundable(myEditor.getProject(), "Getting File History", true) {
      private final Object LOCK = new Object();
      private final MergingUpdateQueue myUpdateQueue = new MergingUpdateQueue("AnnotateStackTraceAction", 200, true, null);

      private MyActiveAnnotationGutter myGutter;

      @Override
      public void onCancel() {
        myEditor.getGutter().closeAllAnnotations();
      }

      @Override
      public void onFinished() {
        myIsLoading = false;
        Disposer.dispose(myUpdateQueue);
      }

      @Override
      public void run(@Nonnull ProgressIndicator indicator) {
        MultiMap<VirtualFile, Integer> files2lines = new MultiMap<>();
        Map<Integer, LastRevision> revisions = new HashMap<>();

        ApplicationManager.getApplication().runReadAction(() -> {
          for (int line = 0; line < myEditor.getDocument().getLineCount(); line++) {
            indicator.checkCanceled();
            VirtualFile file = getHyperlinkVirtualFile(myHyperlinks.findAllHyperlinksOnLine(line));
            if (file == null) continue;

            files2lines.putValue(file, line);
          }
        });

        files2lines.entrySet().forEach(entry -> {
          indicator.checkCanceled();
          VirtualFile file = entry.getKey();
          Collection<Integer> lines = entry.getValue();

          LastRevision revision = getLastRevision(file);
          if (revision == null) return;

          synchronized (LOCK) {
            for (Integer line : lines) {
              revisions.put(line, revision);
            }
          }

          myUpdateQueue.queue(new Update("update") {
            @Override
            public void run() {
              updateGutter(indicator, revisions);
            }
          });
        });

        // myUpdateQueue can be disposed before the last revisions are passed to the gutter
        ApplicationManager.getApplication().invokeLater(() -> updateGutter(indicator, revisions));
      }

      @CalledInAwt
      private void updateGutter(@Nonnull ProgressIndicator indicator, @Nonnull Map<Integer, LastRevision> revisions) {
        if (indicator.isCanceled()) return;

        if (myGutter == null) {
          myGutter = new MyActiveAnnotationGutter((Project)getProject(), myHyperlinks, indicator);
          myEditor.getGutter().registerTextAnnotation(myGutter, myGutter);
        }

        Map<Integer, LastRevision> revisionsCopy;
        synchronized (LOCK) {
          revisionsCopy = new HashMap<>(revisions);
        }

        myGutter.updateData(revisionsCopy);
        ((EditorGutterComponentEx)myEditor.getGutter()).revalidateMarkup();
      }

      @Nullable
      private LastRevision getLastRevision(@Nonnull VirtualFile file) {
        try {
          AbstractVcs vcs = VcsUtil.getVcsFor(myEditor.getProject(), file);
          if (vcs == null) return null;

          VcsHistoryProvider historyProvider = vcs.getVcsHistoryProvider();
          if (historyProvider == null) return null;

          FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(file);

          if (historyProvider instanceof VcsHistoryProviderEx) {
            VcsFileRevision revision = ((VcsHistoryProviderEx)historyProvider).getLastRevision(filePath);
            if (revision == null) return null;
            return LastRevision.create(revision);
          }
          else {
            VcsHistorySession session = historyProvider.createSessionFor(filePath);
            if (session == null) return null;

            List<VcsFileRevision> list = session.getRevisionList();
            if (list == null || list.isEmpty()) return null;

            return LastRevision.create(list.get(0));
          }
        }
        catch (VcsException ignored) {
          LOG.warn(ignored);
          return null;
        }
      }
    });
  }

  @Nullable
  //@CalledWithReadLock
  private static VirtualFile getHyperlinkVirtualFile(@Nonnull List<RangeHighlighter> links) {
    RangeHighlighter key = ContainerUtil.getLastItem(links);
    if (key == null) return null;
    HyperlinkInfo info = EditorHyperlinkSupport.getHyperlinkInfo(key);
    if (!(info instanceof FileHyperlinkInfo)) return null;
    OpenFileDescriptorImpl descriptor = ((FileHyperlinkInfo)info).getDescriptor();
    return descriptor != null ? descriptor.getFile() : null;
  }

  private static class LastRevision {
    @Nonnull
    private final VcsRevisionNumber myNumber;
    @Nonnull
    private final String myAuthor;
    @Nonnull
    private final Date myDate;
    @Nonnull
    private final String myMessage;

    LastRevision(@Nonnull VcsRevisionNumber number, @Nonnull String author, @Nonnull Date date, @Nonnull String message) {
      myNumber = number;
      myAuthor = author;
      myDate = date;
      myMessage = message;
    }

    @Nonnull
    public static LastRevision create(@Nonnull VcsFileRevision revision) {
      VcsRevisionNumber number = revision.getRevisionNumber();
      String author = StringUtil.notNullize(revision.getAuthor(), "Unknown");
      Date date = revision.getRevisionDate();
      String message = StringUtil.notNullize(revision.getCommitMessage());
      return new LastRevision(number, author, date, message);
    }

    @Nonnull
    public VcsRevisionNumber getNumber() {
      return myNumber;
    }

    @Nonnull
    public String getAuthor() {
      return myAuthor;
    }

    @Nonnull
    public Date getDate() {
      return myDate;
    }

    @Nonnull
    public String getMessage() {
      return myMessage;
    }
  }

  private static class MyActiveAnnotationGutter implements TextAnnotationGutterProvider, EditorGutterAction {
    @Nonnull
    private final Project myProject;
    @Nonnull
    private final EditorHyperlinkSupport myHyperlinks;
    @Nonnull
    private final ProgressIndicator myIndicator;

    @Nonnull
    private Map<Integer, LastRevision> myRevisions = Collections.emptyMap();
    private Date myNewestDate = null;
    private int myMaxDateLength = 0;

    MyActiveAnnotationGutter(@Nonnull Project project, @Nonnull EditorHyperlinkSupport hyperlinks, @Nonnull ProgressIndicator indicator) {
      myProject = project;
      myHyperlinks = hyperlinks;
      myIndicator = indicator;
    }

    @Override
    public void doAction(int lineNum) {
      LastRevision revision = myRevisions.get(lineNum);
      if (revision == null) return;

      VirtualFile file = getHyperlinkVirtualFile(myHyperlinks.findAllHyperlinksOnLine(lineNum));
      if (file == null) return;

      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
      if (vcs != null) {
        VcsRevisionNumber number = revision.getNumber();
        VcsKey vcsKey = vcs.getKeyInstanceMethod();
        ShowAllAffectedGenericAction.showSubmittedFiles(myProject, number, file, vcsKey);
      }
    }

    @Override
    public Cursor getCursor(int lineNum) {
      return myRevisions.containsKey(lineNum) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor();
    }

    @Override
    public String getLineText(int line, Editor editor) {
      LastRevision revision = myRevisions.get(line);
      if (revision != null) {
        return String.format("%" + myMaxDateLength + "s", FileAnnotation.formatDate(revision.getDate())) + " " + revision.getAuthor();
      }
      return "";
    }

    @Override
    public String getToolTip(int line, Editor editor) {
      LastRevision revision = myRevisions.get(line);
      if (revision != null) {
        return XmlStringUtil.escapeString(revision.getAuthor() + " " + DateFormatUtil.formatDateTime(revision.getDate()) + "\n" + VcsUtil.trimCommitMessageToSaneSize(revision.getMessage()));
      }
      return null;
    }

    @Override
    public EditorFontType getStyle(int line, Editor editor) {
      LastRevision revision = myRevisions.get(line);
      return revision != null && revision.getDate().equals(myNewestDate) ? EditorFontType.BOLD : EditorFontType.PLAIN;
    }

    @Override
    public EditorColorKey getColor(int line, Editor editor) {
      return AnnotationSource.LOCAL.getColor();
    }

    @Override
    public ColorValue getBgColor(int line, Editor editor) {
      return null;
    }

    @Override
    public List<AnAction> getPopupActions(int line, Editor editor) {
      return Collections.emptyList();
    }

    @Override
    public void gutterClosed() {
      myIndicator.cancel();
    }

    @RequiredUIAccess
    public void updateData(@Nonnull Map<Integer, LastRevision> revisions) {
      myRevisions = revisions;

      Date newestDate = null;
      int maxDateLength = 0;

      for (LastRevision revision : myRevisions.values()) {
        Date date = revision.getDate();
        if (newestDate == null || date.after(newestDate)) {
          newestDate = date;
        }
        int length = DateFormatUtil.formatPrettyDate(date).length();
        if (length > maxDateLength) {
          maxDateLength = length;
        }
      }

      myNewestDate = newestDate;
      myMaxDateLength = maxDateLength;
    }
  }
}
