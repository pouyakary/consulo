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
package consulo.language.impl.file;

import consulo.component.ProcessCanceledException;
import consulo.language.Language;
import consulo.language.file.FileViewProvider;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.DebugUtil;
import consulo.language.impl.ast.FileElement;
import consulo.language.impl.psi.internal.PsiFileEx;
import consulo.language.impl.psi.PsiFileImpl;
import consulo.language.plain.PlainTextLanguage;
import consulo.language.psi.*;
import consulo.language.util.LanguageUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.reflect.ReflectionUtil;
import consulo.virtualFileSystem.RawFileLoader;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SingleRootFileViewProvider extends AbstractFileViewProvider implements FileViewProvider {
  private static final Key<Boolean> OUR_NO_SIZE_LIMIT_KEY = Key.create("no.size.limit");
  private static final Logger LOG = Logger.getInstance(SingleRootFileViewProvider.class);
  @SuppressWarnings("unused")
  private volatile PsiFile myPsiFile;
  private static final VarHandle ourPsiFileUpdater;

  static {
    try {
      ourPsiFileUpdater = MethodHandles.lookup().findVarHandle(SingleRootFileViewProvider.class, "myPsiFile", PsiFile.class);
    }
    catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @Nonnull
  private final Language myBaseLanguage;

  public SingleRootFileViewProvider(@Nonnull PsiManager manager, @Nonnull VirtualFile file) {
    this(manager, file, true);
  }

  public SingleRootFileViewProvider(@Nonnull PsiManager manager, @Nonnull VirtualFile virtualFile, final boolean eventSystemEnabled) {
    this(manager, virtualFile, eventSystemEnabled, calcBaseLanguage(virtualFile, manager.getProject(), virtualFile.getFileType()));
  }

  public SingleRootFileViewProvider(@Nonnull PsiManager manager, @Nonnull VirtualFile virtualFile, final boolean eventSystemEnabled, @Nonnull final FileType fileType) {
    this(manager, virtualFile, eventSystemEnabled, calcBaseLanguage(virtualFile, manager.getProject(), fileType));
  }

  protected SingleRootFileViewProvider(@Nonnull PsiManager manager, @Nonnull VirtualFile virtualFile, final boolean eventSystemEnabled, @Nonnull Language language) {
    super(manager, virtualFile, eventSystemEnabled);
    myBaseLanguage = language;
  }

  @Override
  @Nonnull
  public Language getBaseLanguage() {
    return myBaseLanguage;
  }

  private static Language calcBaseLanguage(@Nonnull VirtualFile file, @Nonnull Project project, @Nonnull final FileType fileType) {
    if (fileType.isBinary()) return Language.ANY;
    if (isTooLargeForIntelligence(file)) return PlainTextLanguage.INSTANCE;

    Language language = LanguageUtil.getLanguageForPsi(project, file);

    return language != null ? language : PlainTextLanguage.INSTANCE;
  }

  @Override
  @Nonnull
  public Set<Language> getLanguages() {
    return Collections.singleton(getBaseLanguage());
  }

  @Override
  @Nonnull
  public List<PsiFile> getAllFiles() {
    return ContainerUtil.createMaybeSingletonList(getPsi(getBaseLanguage()));
  }

  @Override
  @Nullable
  protected PsiFile getPsiInner(@Nonnull Language target) {
    if (target != getBaseLanguage()) {
      return null;
    }
    PsiFile psiFile = myPsiFile;
    if (psiFile == null) {
      psiFile = createFile();
      if (psiFile == null) {
        psiFile = PsiUtilCore.NULL_PSI_FILE;
      }
      boolean set = ourPsiFileUpdater.compareAndSet(this, null, psiFile);
      if (!set && psiFile != PsiUtilCore.NULL_PSI_FILE) {
        PsiFile alreadyCreated = myPsiFile;
        if (alreadyCreated == psiFile) {
          LOG.error(this + ".createFile() must create new file instance but got the same: " + psiFile);
        }
        if (psiFile instanceof PsiFileEx) {
          PsiFile finalPsiFile = psiFile;
          DebugUtil.performPsiModification("invalidating throw-away copy", () -> ((PsiFileEx)finalPsiFile).markInvalidated());
        }
        psiFile = alreadyCreated;
      }
    }
    return psiFile == PsiUtilCore.NULL_PSI_FILE ? null : psiFile;
  }

  @Override
  public final PsiFile getCachedPsi(@Nonnull Language target) {
    if (target != getBaseLanguage()) return null;
    PsiFile file = myPsiFile;
    return file == PsiUtilCore.NULL_PSI_FILE ? null : file;
  }

  @Nonnull
  @Override
  public final List<PsiFile> getCachedPsiFiles() {
    return ContainerUtil.createMaybeSingletonList(getCachedPsi(getBaseLanguage()));
  }

  @Override
  @Nonnull
  public final List<FileElement> getKnownTreeRoots() {
    PsiFile psiFile = getCachedPsi(getBaseLanguage());
    if (!(psiFile instanceof PsiFileImpl)) return Collections.emptyList();
    FileElement element = ((PsiFileImpl)psiFile).getTreeElement();
    return ContainerUtil.createMaybeSingletonList(element);
  }

  private PsiFile createFile() {
    try {
      return shouldCreatePsi() ? createFile(getManager().getProject(), getVirtualFile(), getFileType()) : null;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return null;
    }
  }

  public static boolean isTooLargeForIntelligence(@Nonnull VirtualFile vFile) {
    if (!checkFileSizeLimit(vFile)) return false;
    return fileSizeIsGreaterThan(vFile, RawFileLoader.getInstance().getMaxIntellisenseFileSize());
  }

  public static boolean isTooLargeForContentLoading(@Nonnull VirtualFile vFile) {
    return fileSizeIsGreaterThan(vFile, RawFileLoader.getInstance().getFileLengthToCacheThreshold());
  }

  private static boolean checkFileSizeLimit(@Nonnull VirtualFile vFile) {
    if (Boolean.TRUE.equals(vFile.getCopyableUserData(OUR_NO_SIZE_LIMIT_KEY))) {
      return false;
    }
    if (vFile instanceof LightVirtualFile) {
      VirtualFile original = ((LightVirtualFile)vFile).getOriginalFile();
      if (original != null) return checkFileSizeLimit(original);
    }
    return true;
  }

  public static void doNotCheckFileSizeLimit(@Nonnull VirtualFile vFile) {
    vFile.putCopyableUserData(OUR_NO_SIZE_LIMIT_KEY, Boolean.TRUE);
  }

  public static boolean isTooLargeForIntelligence(@Nonnull VirtualFile vFile, final long contentSize) {
    if (!checkFileSizeLimit(vFile)) return false;
    return contentSize > RawFileLoader.getInstance().getMaxIntellisenseFileSize();
  }

  public static boolean isTooLargeForContentLoading(@Nonnull VirtualFile vFile, final long contentSize) {
    return contentSize > RawFileLoader.getInstance().getFileLengthToCacheThreshold();
  }

  public static boolean fileSizeIsGreaterThan(@Nonnull VirtualFile vFile, final long maxBytes) {
    if (vFile instanceof LightVirtualFile) {
      // This is optimization in order to avoid conversion of [large] file contents to bytes
      final int lengthInChars = ((LightVirtualFile)vFile).getContent().length();
      if (lengthInChars < maxBytes / 2) return false;
      if (lengthInChars > maxBytes) return true;
    }

    return vFile.getLength() > maxBytes;
  }

  @Nonnull
  @Override
  public SingleRootFileViewProvider createCopy(@Nonnull final VirtualFile copy) {
    return new SingleRootFileViewProvider(getManager(), copy, false, getBaseLanguage());
  }

  @Override
  public PsiReference findReferenceAt(final int offset) {
    final PsiFile psiFile = getPsi(getBaseLanguage());
    return findReferenceAt(psiFile, offset);
  }

  @Override
  public PsiElement findElementAt(final int offset) {
    return findElementAt(getPsi(getBaseLanguage()), offset);
  }


  @Override
  public PsiElement findElementAt(int offset, @Nonnull Class<? extends Language> lang) {
    if (!ReflectionUtil.isAssignable(lang, getBaseLanguage().getClass())) return null;
    return findElementAt(offset);
  }

  public final void forceCachedPsi(@Nonnull PsiFile psiFile) {
    while (true) {
      PsiFile prev = myPsiFile;
      // jdk 6 doesn't have getAndSet()
      if (ourPsiFileUpdater.compareAndSet(this, prev, psiFile)) {
        if (prev != psiFile && prev instanceof PsiFileEx) {
          ((PsiFileEx)prev).markInvalidated();
        }
        break;
      }
    }
    getManager().getFileManager().setViewProvider(getVirtualFile(), this);
  }
}
