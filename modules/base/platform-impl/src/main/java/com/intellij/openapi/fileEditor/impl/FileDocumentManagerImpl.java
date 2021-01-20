// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.AppTopics;
import com.intellij.CommonBundle;
import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.text.DesktopTextEditorImpl;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.pom.core.impl.PomModelImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.containers.ContainerUtil;
import consulo.application.TransactionGuardEx;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.*;
import java.util.function.Predicate;

public class FileDocumentManagerImpl extends FileDocumentManagerBase implements SafeWriteRequestor {
  private static final Logger LOG = Logger.getInstance(FileDocumentManagerImpl.class);

  public static final Key<Object> NOT_RELOADABLE_DOCUMENT_KEY = new Key<>("NOT_RELOADABLE_DOCUMENT_KEY");

  private static final Key<String> LINE_SEPARATOR_KEY = Key.create("LINE_SEPARATOR_KEY");
  private static final Key<Boolean> MUST_RECOMPUTE_FILE_TYPE = Key.create("Must recompute file type");

  private final Set<Document> myUnsavedDocuments = ContainerUtil.newConcurrentSet();

  private final FileDocumentManagerListener myMultiCaster;
  private final TrailingSpacesStripper myTrailingSpacesStripper = new TrailingSpacesStripper();

  private boolean myOnClose;

  private volatile MemoryDiskConflictResolver myConflictResolver = new MemoryDiskConflictResolver();
  private final PrioritizedDocumentListener myPhysicalDocumentChangeTracker = new PrioritizedDocumentListener() {
    @Override
    public int getPriority() {
      return Integer.MIN_VALUE;
    }

    @Override
    public void documentChanged(@Nonnull DocumentEvent e) {
      Document document = e.getDocument();
      if (!ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction.ExternalDocumentChange.class)) {
        myUnsavedDocuments.add(document);
      }
      Runnable currentCommand = CommandProcessor.getInstance().getCurrentCommand();
      Project project = currentCommand == null ? null : CommandProcessor.getInstance().getCurrentCommandProject();
      if (project == null) {
        project = ProjectUtil.guessProjectForFile(getFile(document));
      }
      String lineSeparator = CodeStyle.getProjectOrDefaultSettings(project).getLineSeparator();
      document.putUserData(LINE_SEPARATOR_KEY, lineSeparator);

      // avoid documents piling up during batch processing
      if (areTooManyDocumentsInTheQueue(myUnsavedDocuments)) {
        saveAllDocumentsLater();
      }
    }
  };

  public FileDocumentManagerImpl() {
    InvocationHandler handler = (__, method, args) -> {
      if (method.getDeclaringClass() != FileDocumentManagerListener.class) {
        // only FileDocumentManagerListener methods should be called on this proxy
        throw new UnsupportedOperationException(method.toString());
      }
      multiCast(method, args);
      return null;
    };

    ClassLoader loader = FileDocumentManagerListener.class.getClassLoader();
    myMultiCaster = (FileDocumentManagerListener)Proxy.newProxyInstance(loader, new Class[]{FileDocumentManagerListener.class}, handler);

    // remove VirtualFiles sitting in the DocumentImpl.rmTreeQueue reference queue which could retain plugin-registered FS in their VirtualDirectoryImpl.myFs
    //ApplicationManager.getApplication().getMessageBus().connect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
    //  @Override
    //  public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
    //    DocumentImpl.processQueue();
    //  }
    //});
  }

  static final class MyProjectCloseHandler implements Predicate<Project> {
    @Override
    public boolean test(@Nonnull Project project) {
      FileDocumentManagerImpl manager = (FileDocumentManagerImpl)getInstance();
      if (!manager.myUnsavedDocuments.isEmpty()) {
        manager.myOnClose = true;
        try {
          manager.saveAllDocuments();
        }
        finally {
          manager.myOnClose = false;
        }
      }
      return manager.myUnsavedDocuments.isEmpty();
    }
  }

  private static void unwrapAndRethrow(@Nonnull Exception e) {
    Throwable unwrapped = e;
    if (e instanceof InvocationTargetException) {
      unwrapped = e.getCause() == null ? e : e.getCause();
    }
    ExceptionUtil.rethrowUnchecked(unwrapped);
    LOG.error(unwrapped);
  }

  @SuppressWarnings("OverlyBroadCatchBlock")
  private void multiCast(@Nonnull Method method, Object[] args) {
    try {
      method.invoke(ApplicationManager.getApplication().getMessageBus().syncPublisher(AppTopics.FILE_DOCUMENT_SYNC), args);
    }
    catch (ClassCastException e) {
      LOG.error("Arguments: " + Arrays.toString(args), e);
    }
    catch (Exception e) {
      unwrapAndRethrow(e);
    }

    // Allows pre-save document modification
    for (FileDocumentManagerListener listener : getListeners()) {
      try {
        method.invoke(listener, args);
      }
      catch (Exception e) {
        unwrapAndRethrow(e);
      }
    }

    // stripping trailing spaces
    try {
      method.invoke(myTrailingSpacesStripper, args);
    }
    catch (Exception e) {
      unwrapAndRethrow(e);
    }
  }

  public static boolean areTooManyDocumentsInTheQueue(@Nonnull Collection<? extends Document> documents) {
    if (documents.size() > 100) return true;
    int totalSize = 0;
    for (Document document : documents) {
      totalSize += document.getTextLength();
      if (totalSize > FileUtilRt.LARGE_FOR_CONTENT_LOADING) return true;
    }
    return false;
  }

  @Override
  @Nonnull
  protected Document createDocument(@Nonnull CharSequence text, @Nonnull VirtualFile file) {
    boolean acceptSlashR = file instanceof LightVirtualFile && StringUtil.indexOf(text, '\r') >= 0;
    boolean freeThreaded = Boolean.TRUE.equals(file.getUserData(AbstractFileViewProvider.FREE_THREADED));
    DocumentImpl document = (DocumentImpl)((EditorFactoryImpl)EditorFactory.getInstance()).createDocument(text, acceptSlashR, freeThreaded);
    document.documentCreatedFrom(file);
    return document;
  }

  @TestOnly
  public void dropAllUnsavedDocuments() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new RuntimeException("This method is only for test mode!");
    }
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (!myUnsavedDocuments.isEmpty()) {
      myUnsavedDocuments.clear();
      myMultiCaster.unsavedDocumentsDropped();
    }
  }

  private void saveAllDocumentsLater() {
    // later because some document might have been blocked by PSI right now
    ApplicationManager.getApplication().invokeLater(() -> {
      Document[] unsavedDocuments = getUnsavedDocuments();
      for (Document document : unsavedDocuments) {
        VirtualFile file = getFile(document);
        if (file == null) continue;
        Project project = ProjectUtil.guessProjectForFile(file);
        if (project == null) continue;
        if (PsiDocumentManager.getInstance(project).isDocumentBlockedByPsi(document)) continue;

        saveDocument(document);
      }
    });
  }

  @Override
  public void saveAllDocuments() {
    saveAllDocuments(true);
  }

  /**
   * @param isExplicit caused by user directly (Save action) or indirectly (e.g. Compile)
   */
  public void saveAllDocuments(boolean isExplicit) {
    saveDocuments(null, isExplicit);
  }

  @Override
  public void saveDocuments(@Nonnull Predicate<Document> filter) {
    saveDocuments(filter, true);
  }

  private void saveDocuments(@Nullable Predicate<Document> filter, boolean isExplicit) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ((TransactionGuardEx)TransactionGuard.getInstance()).assertWriteActionAllowed();

    myMultiCaster.beforeAllDocumentsSaving();
    if (myUnsavedDocuments.isEmpty()) return;

    Map<Document, IOException> failedToSave = new HashMap<>();
    Set<Document> vetoed = new HashSet<>();
    while (true) {
      int count = 0;

      for (Document document : myUnsavedDocuments) {
        if (filter != null && !filter.test(document)) continue;
        if (failedToSave.containsKey(document)) continue;
        if (vetoed.contains(document)) continue;
        try {
          doSaveDocument(document, isExplicit);
        }
        catch (IOException e) {
          failedToSave.put(document, e);
        }
        catch (SaveVetoException e) {
          vetoed.add(document);
        }
        count++;
      }

      if (count == 0) break;
    }

    if (!failedToSave.isEmpty()) {
      handleErrorsOnSave(failedToSave);
    }
  }

  @Override
  public void saveDocument(@Nonnull Document document) {
    saveDocument(document, true);
  }

  public void saveDocument(@Nonnull Document document, boolean explicit) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ((TransactionGuardEx)TransactionGuard.getInstance()).assertWriteActionAllowed();

    if (!myUnsavedDocuments.contains(document)) return;

    try {
      doSaveDocument(document, explicit);
    }
    catch (IOException e) {
      handleErrorsOnSave(Collections.singletonMap(document, e));
    }
    catch (SaveVetoException ignored) {
    }
  }

  @Override
  public void saveDocumentAsIs(@Nonnull Document document) {
    VirtualFile file = getFile(document);
    boolean spaceStrippingEnabled = true;
    if (file != null) {
      spaceStrippingEnabled = TrailingSpacesStripper.isEnabled(file);
      TrailingSpacesStripper.setEnabled(file, false);
    }
    try {
      saveDocument(document);
    }
    finally {
      if (file != null) {
        TrailingSpacesStripper.setEnabled(file, spaceStrippingEnabled);
      }
    }
  }

  private static class SaveVetoException extends Exception {
  }

  private void doSaveDocument(@Nonnull Document document, boolean isExplicit) throws IOException, SaveVetoException {
    VirtualFile file = getFile(document);
    if (LOG.isTraceEnabled()) LOG.trace("saving: " + file);

    if (file == null || file instanceof LightVirtualFile || file.isValid() && !isFileModified(file)) {
      removeFromUnsaved(document);
      return;
    }

    if (file.isValid() && needsRefresh(file)) {
      LOG.trace("  refreshing...");
      file.refresh(false, false);
      if (!myUnsavedDocuments.contains(document)) return;
    }

    if (!maySaveDocument(file, document, isExplicit)) {
      throw new SaveVetoException();
    }

    LOG.trace("  writing...");
    WriteAction.run(() -> doSaveDocumentInWriteAction(document, file));
    LOG.trace("  done");
  }

  private boolean maySaveDocument(@Nonnull VirtualFile file, @Nonnull Document document, boolean isExplicit) {
    return !myConflictResolver.hasConflict(file) && FileDocumentSynchronizationVetoer.EP_NAME.getExtensionList().stream().allMatch(vetoer -> vetoer.maySaveDocument(document, isExplicit));
  }

  private void doSaveDocumentInWriteAction(@Nonnull Document document, @Nonnull VirtualFile file) throws IOException {
    if (!file.isValid()) {
      removeFromUnsaved(document);
      return;
    }

    if (!file.equals(getFile(document))) {
      registerDocument(document, file);
    }

    boolean saveNeeded = false;
    Exception ioException = null;
    try {
      saveNeeded = isSaveNeeded(document, file);
    }
    catch (IOException | RuntimeException e) {
      // in case of corrupted VFS try to stay consistent
      ioException = e;
    }
    if (!saveNeeded) {
      if (document instanceof DocumentEx) {
        ((DocumentEx)document).setModificationStamp(file.getModificationStamp());
      }
      removeFromUnsaved(document);
      updateModifiedProperty(file);
      if (ioException instanceof IOException) throw (IOException)ioException;
      if (ioException != null) throw (RuntimeException)ioException;
      return;
    }

    PomModelImpl.guardPsiModificationsIn(() -> {
      myMultiCaster.beforeDocumentSaving(document);
      LOG.assertTrue(file.isValid());

      String text = document.getText();
      String lineSeparator = getLineSeparator(document, file);
      if (!lineSeparator.equals("\n")) {
        text = StringUtil.convertLineSeparators(text, lineSeparator);
      }

      Project project = ProjectLocator.getInstance().guessProjectForFile(file);
      LoadTextUtil.write(project, file, this, text, document.getModificationStamp());

      myUnsavedDocuments.remove(document);
      LOG.assertTrue(!myUnsavedDocuments.contains(document));
      myTrailingSpacesStripper.clearLineModificationFlags(document);
    });
  }

  private static void updateModifiedProperty(@Nonnull VirtualFile file) {
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
      for (FileEditor editor : fileEditorManager.getAllEditors(file)) {
        if (editor instanceof DesktopTextEditorImpl) {
          ((DesktopTextEditorImpl)editor).updateModifiedProperty();
        }
      }
    }
  }

  private void removeFromUnsaved(@Nonnull Document document) {
    myUnsavedDocuments.remove(document);
    myMultiCaster.unsavedDocumentDropped(document);
    LOG.assertTrue(!myUnsavedDocuments.contains(document));
  }

  private static boolean isSaveNeeded(@Nonnull Document document, @Nonnull VirtualFile file) throws IOException {
    if (file.getFileType().isBinary() || document.getTextLength() > 1000 * 1000) {    // don't compare if the file is too big
      return true;
    }

    byte[] bytes = file.contentsToByteArray();
    CharSequence loaded = LoadTextUtil.getTextByBinaryPresentation(bytes, file, false, false);

    return !Comparing.equal(document.getCharsSequence(), loaded);
  }

  private static boolean needsRefresh(@Nonnull VirtualFile file) {
    VirtualFileSystem fs = file.getFileSystem();
    return fs instanceof NewVirtualFileSystem && file.getTimeStamp() != ((NewVirtualFileSystem)fs).getTimeStamp(file);
  }

  @Nonnull
  public static String getLineSeparator(@Nonnull Document document, @Nonnull VirtualFile file) {
    String lineSeparator = file.getDetectedLineSeparator();
    if (lineSeparator == null) {
      lineSeparator = document.getUserData(LINE_SEPARATOR_KEY);
      assert lineSeparator != null : document;
    }
    return lineSeparator;
  }

  @Override
  @Nonnull
  public String getLineSeparator(@Nullable VirtualFile file, @Nullable Project project) {
    String lineSeparator = file == null ? null : file.getDetectedLineSeparator();
    if (lineSeparator == null) {
      lineSeparator = CodeStyle.getProjectOrDefaultSettings(project).getLineSeparator();
    }
    return lineSeparator;
  }

  @Override
  public boolean requestWriting(@Nonnull Document document, Project project) {
    return requestWritingStatus(document, project).hasWriteAccess();
  }

  @Nonnull
  @Override
  public WriteAccessStatus requestWritingStatus(@Nonnull Document document, @Nullable Project project) {
    VirtualFile file = getInstance().getFile(document);
    if (project != null && file != null && file.isValid()) {
      if (file.getFileType().isBinary()) return WriteAccessStatus.NON_WRITABLE;
      ReadonlyStatusHandler.OperationStatus writableStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(Collections.singletonList(file));
      if (writableStatus.hasReadonlyFiles()) {
        return new WriteAccessStatus(writableStatus.getReadonlyFilesMessage());
      }
      assert file.isWritable() : file;
    }
    if (document.isWritable()) {
      return WriteAccessStatus.WRITABLE;
    }
    document.fireReadOnlyModificationAttempt();
    return WriteAccessStatus.NON_WRITABLE;
  }

  @Override
  public void reloadFiles(@Nonnull VirtualFile... files) {
    for (VirtualFile file : files) {
      if (file.exists()) {
        Document doc = getCachedDocument(file);
        if (doc != null) {
          reloadFromDisk(doc);
        }
      }
    }
  }

  @Override
  @Nonnull
  public Document[] getUnsavedDocuments() {
    if (myUnsavedDocuments.isEmpty()) {
      return Document.EMPTY_ARRAY;
    }

    List<Document> list = new ArrayList<>(myUnsavedDocuments);
    return list.toArray(Document.EMPTY_ARRAY);
  }

  @Override
  public boolean isDocumentUnsaved(@Nonnull Document document) {
    return myUnsavedDocuments.contains(document);
  }

  @Override
  public boolean isFileModified(@Nonnull VirtualFile file) {
    //ModelBranch branch = ModelBranch.getFileBranch(file);
    //if (branch != null && ((ModelBranchImpl)branch).hasModifications(file)) {
    //  return true;
    //}
    Document doc = getCachedDocument(file);
    return doc != null && isDocumentUnsaved(doc) && doc.getModificationStamp() != file.getModificationStamp();
  }

  private void propertyChanged(@Nonnull VFilePropertyChangeEvent event) {
    if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName())) {
      VirtualFile file = event.getFile();
      Document document = getCachedDocument(file);
      if (document != null) {
        ApplicationManager.getApplication().runWriteAction((ExternalChangeAction)() -> document.setReadOnly(!file.isWritable()));
      }
    }
    else if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      VirtualFile file = event.getFile();
      Document document = getCachedDocument(file);
      if (document != null) {
        if (isBinaryWithoutDecompiler(file)) {
          // a file is linked to a document - chances are it is an "unknown text file" now
          unbindFileFromDocument(file, document);
        }
        else if (FileContentUtilCore.FORCE_RELOAD_REQUESTOR.equals(event.getRequestor()) && isBinaryWithDecompiler(file)) {
          reloadFromDisk(document);
        }
      }
    }
  }

  private static boolean isBinaryWithDecompiler(@Nonnull VirtualFile file) {
    FileType type = file.getFileType();
    return type.isBinary() && BinaryFileTypeDecompilers.INSTANCE.forFileType(type) != null;
  }

  static final class MyAsyncFileListener implements AsyncFileListener {
    private final FileDocumentManagerImpl myFileDocumentManager = (FileDocumentManagerImpl)getInstance();

    @Override
    public ChangeApplier prepareChange(@Nonnull List<? extends VFileEvent> events) {
      List<VirtualFile> toRecompute = new ArrayList<>();
      Map<VirtualFile, Document> strongRefsToDocuments = new HashMap<>();
      List<VFileContentChangeEvent> contentChanges = ContainerUtil.findAll(events, VFileContentChangeEvent.class);
      for (VFileContentChangeEvent event : contentChanges) {
        ProgressManager.checkCanceled();
        VirtualFile virtualFile = event.getFile();

        // when an empty unknown file is written into, re-run file type detection
        if (virtualFile instanceof VirtualFileWithId) {
          long lastRecordedLength = PersistentFS.getInstance().getLastRecordedLength(virtualFile);
          if (lastRecordedLength == 0 && FileTypeRegistry.getInstance().isFileOfType(virtualFile, UnknownFileType.INSTANCE)) { // check file type last to avoid content detection running
            toRecompute.add(virtualFile);
          }
        }

        prepareForRangeMarkerUpdate(strongRefsToDocuments, virtualFile);
      }

      return new ChangeApplier() {
        @Override
        public void beforeVfsChange() {
          for (VFileContentChangeEvent event : contentChanges) {
            // new range markers could've appeared after "prepareChange" in some read action
            prepareForRangeMarkerUpdate(strongRefsToDocuments, event.getFile());
            if (ourConflictsSolverEnabled) {
              myFileDocumentManager.myConflictResolver.beforeContentChange(event);
            }
          }

          for (VirtualFile file : toRecompute) {
            file.putUserData(MUST_RECOMPUTE_FILE_TYPE, Boolean.TRUE);
          }
        }

        @Override
        public void afterVfsChange() {
          for (VFileEvent event : events) {
            if (event instanceof VFileContentChangeEvent && ((VFileContentChangeEvent)event).getFile().isValid()) {
              myFileDocumentManager.contentsChanged((VFileContentChangeEvent)event);
            }
            else if (event instanceof VFileDeleteEvent && ((VFileDeleteEvent)event).getFile().isValid()) {
              myFileDocumentManager.fileDeleted((VFileDeleteEvent)event);
            }
            else if (event instanceof VFilePropertyChangeEvent && ((VFilePropertyChangeEvent)event).getFile().isValid()) {
              myFileDocumentManager.propertyChanged((VFilePropertyChangeEvent)event);
            }
          }
          Reference.reachabilityFence(strongRefsToDocuments);
        }
      };
    }

    private void prepareForRangeMarkerUpdate(Map<VirtualFile, Document> strongRefsToDocuments, VirtualFile virtualFile) {
      Document document = myFileDocumentManager.getCachedDocument(virtualFile);
      if (document == null && DocumentImpl.areRangeMarkersRetainedFor(virtualFile)) {
        // re-create document with the old contents prior to this event
        // then contentChanged() will diff the document with the new contents and update the markers
        document = myFileDocumentManager.getDocument(virtualFile);
      }
      // save document strongly to make it live until contentChanged()
      if (document != null) {
        strongRefsToDocuments.put(virtualFile, document);
      }
    }
  }

  public void contentsChanged(@Nonnull VFileContentChangeEvent event) {
    VirtualFile virtualFile = event.getFile();
    Document document = getCachedDocument(virtualFile);

    boolean shouldTraceEvent = document != null && LOG.isTraceEnabled();
    String eventMessage = null;

    if (shouldTraceEvent) {
      eventMessage = "content changed for " + event.getFile() + " with document stamp =  " + document.getModificationStamp();
    }

    if (event.isFromSave()) {
      if (shouldTraceEvent) {
        eventMessage += " , dispatched from save";
        LOG.trace(eventMessage);
      }

      return;
    }

    if (document == null || isBinaryWithDecompiler(virtualFile)) {
      myMultiCaster.fileWithNoDocumentChanged(virtualFile); // This will generate PSI event at FileManagerImpl
    }

    if (document != null) {
      if (shouldTraceEvent) {
        eventMessage += " event old modification stamp = " + event.getOldModificationStamp() + ", is unsaved = " + isDocumentUnsaved(document);
        LOG.trace(eventMessage);
      }

      if (document.getModificationStamp() == event.getOldModificationStamp() || !isDocumentUnsaved(document)) {
        reloadFromDisk(document);
      }
    }
  }

  @Override
  public void reloadFromDisk(@Nonnull Document document) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    VirtualFile file = getFile(document);
    assert file != null;
    if (!file.isValid()) return;

    if (!fireBeforeFileContentReload(file, document)) {
      return;
    }

    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    boolean[] isReloadable = {isReloadable(file, document, project)};
    if (isReloadable[0]) {
      CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(new ExternalChangeAction.ExternalDocumentChange(document, project) {
        @Override
        public void run() {
          if (!isBinaryWithoutDecompiler(file)) {
            LoadTextUtil.clearCharsetAutoDetectionReason(file);
            file.setBOM(null); // reset BOM in case we had one and the external change stripped it away
            file.setCharset(null, null, false);
            boolean wasWritable = document.isWritable();
            document.setReadOnly(false);
            boolean tooLarge = FileUtilRt.isTooLarge(file.getLength());
            isReloadable[0] = isReloadable(file, document, project);
            if (isReloadable[0]) {
              CharSequence reloaded = tooLarge ? LoadTextUtil.loadText(file, getPreviewCharCount(file)) : LoadTextUtil.loadText(file);
              ((DocumentEx)document).replaceText(reloaded, file.getModificationStamp());
              setDocumentTooLarge(document, tooLarge);
            }
            document.setReadOnly(!wasWritable);
          }
        }
      }), UIBundle.message("file.cache.conflict.action"), null, UndoConfirmationPolicy.REQUEST_CONFIRMATION);
    }
    if (isReloadable[0]) {
      myMultiCaster.fileContentReloaded(file, document);
    }
    else {
      unbindFileFromDocument(file, document);
      myMultiCaster.fileWithNoDocumentChanged(file);
    }
    myUnsavedDocuments.remove(document);
  }

  private static boolean isReloadable(@Nonnull VirtualFile file, @Nonnull Document document, @Nullable Project project) {
    PsiFile cachedPsiFile = project == null ? null : PsiDocumentManager.getInstance(project).getCachedPsiFile(document);
    return !(FileUtilRt.isTooLarge(file.getLength()) && file.getFileType().isBinary()) &&
           (cachedPsiFile == null || cachedPsiFile instanceof PsiFileImpl || isBinaryWithDecompiler(file)) &&
           document.getUserData(NOT_RELOADABLE_DOCUMENT_KEY) == null;
  }

  @TestOnly
  void setAskReloadFromDisk(@Nonnull Disposable disposable, @Nonnull MemoryDiskConflictResolver newProcessor) {
    MemoryDiskConflictResolver old = myConflictResolver;
    myConflictResolver = newProcessor;
    Disposer.register(disposable, () -> myConflictResolver = old);
  }

  private void fileDeleted(@Nonnull VFileDeleteEvent event) {
    Document doc = getCachedDocument(event.getFile());
    if (doc != null) {
      myTrailingSpacesStripper.documentDeleted(doc);
    }
  }

  public static boolean recomputeFileTypeIfNecessary(@Nonnull VirtualFile virtualFile) {
    if (virtualFile.getUserData(MUST_RECOMPUTE_FILE_TYPE) != null) {
      virtualFile.getFileType();
      virtualFile.putUserData(MUST_RECOMPUTE_FILE_TYPE, null);
      return true;
    }
    return false;
  }

  private boolean fireBeforeFileContentReload(@Nonnull VirtualFile file, @Nonnull Document document) {
    for (FileDocumentSynchronizationVetoer vetoer : FileDocumentSynchronizationVetoer.EP_NAME.getExtensionList()) {
      try {
        if (!vetoer.mayReloadFileContent(file, document)) {
          return false;
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    myMultiCaster.beforeFileContentReload(file, document);
    return true;
  }

  @Nonnull
  private static List<FileDocumentManagerListener> getListeners() {
    return FileDocumentManagerListener.EP_NAME.getExtensionList();
  }

  @Override
  @Nullable
  public FileViewProvider findCachedPsiInAnyProject(@Nonnull VirtualFile file) {
    ProjectManagerEx manager = ProjectManagerEx.getInstanceEx();
    for (Project project : manager.getOpenProjects()) {
      FileViewProvider vp = PsiManagerEx.getInstanceEx(project).getFileManager().findCachedViewProvider(file);
      if (vp != null) return vp;
    }

    // FIXME [VISTALL] DefaultProjectFactory always initialized?
    //if (manager.isDefaultProjectInitialized()) {
      FileViewProvider vp = PsiManagerEx.getInstanceEx(manager.getDefaultProject()).getFileManager().findCachedViewProvider(file);
      if (vp != null) return vp;
    //}

    return null;
  }

  private void handleErrorsOnSave(@Nonnull Map<Document, IOException> failures) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      IOException ioException = ContainerUtil.getFirstItem(failures.values());
      if (ioException != null) throw new RuntimeException(ioException);
      return;
    }

    for (Map.Entry<Document, IOException> entry : failures.entrySet()) {
      LOG.warn("file: " + getFile(entry.getKey()), entry.getValue());
    }

    // later to get out of write action
    ApplicationManager.getApplication().invokeLater(() -> {
      String text = StringUtil.join(failures.values(), Throwable::getMessage, "\n");

      DialogWrapper dialog = new DialogWrapper(null) {
        {
          init();
          setTitle(UIBundle.message("cannot.save.files.dialog.title"));
        }

        @Override
        protected void createDefaultActions() {
          super.createDefaultActions();
          myOKAction.putValue(Action.NAME, UIBundle.message(myOnClose ? "cannot.save.files.dialog.ignore.changes" : "cannot.save.files.dialog.revert.changes"));
          myOKAction.putValue(DEFAULT_ACTION, null);

          if (!myOnClose) {
            myCancelAction.putValue(Action.NAME, CommonBundle.getCloseButtonText());
          }
        }

        @Override
        protected JComponent createCenterPanel() {
          JPanel panel = new JPanel(new BorderLayout(0, 5));

          panel.add(new JLabel(UIBundle.message("cannot.save.files.dialog.message")), BorderLayout.NORTH);

          JTextPane area = new JTextPane();
          area.setText(text);
          area.setEditable(false);
          area.setMinimumSize(new Dimension(area.getMinimumSize().width, 50));
          panel.add(new JBScrollPane(area, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);

          return panel;
        }
      };

      if (dialog.showAndGet()) {
        for (Document document : failures.keySet()) {
          reloadFromDisk(document);
        }
      }
    });
  }

  /**
   * @deprecated another dirty Rider hack; don't use
   */
  @Deprecated
  @SuppressWarnings("StaticNonFinalField")
  public static boolean ourConflictsSolverEnabled = true;

  @Override
  protected void fileContentLoaded(@Nonnull VirtualFile file, @Nonnull Document document) {
    myMultiCaster.fileContentLoaded(file, document);
  }

  @Override
  protected
  @Nonnull
  DocumentListener getDocumentListener() {
    return myPhysicalDocumentChangeTracker;
  }
}