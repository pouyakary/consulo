/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import consulo.language.ast.*;
import consulo.language.impl.ASTFactory;
import consulo.language.lexer.EmptyLexer;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiParser;
import consulo.language.file.FileViewProvider;
import consulo.language.plain.PlainTextLanguage;
import consulo.language.plain.ast.PlainTextTokenTypes;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import com.intellij.psi.impl.source.PsiPlainTextFileImpl;
import consulo.language.psi.PsiUtilCore;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.version.LanguageVersion;
import javax.annotation.Nonnull;

public class PlainTextParserDefinition implements ParserDefinition {
  private static final IFileElementType PLAIN_FILE_ELEMENT_TYPE = new IFileElementType(PlainTextLanguage.INSTANCE) {
    @Override
    public ASTNode parseContents(ASTNode chameleon) {
      final CharSequence chars = chameleon.getChars();
      return ASTFactory.leaf(PlainTextTokenTypes.PLAIN_TEXT, chars);
    }
  };

  @Override
  @Nonnull
  public Lexer createLexer(@Nonnull LanguageVersion languageVersion) {
    return new EmptyLexer();
  }

  @Override
  @Nonnull
  public PsiParser createParser(@Nonnull LanguageVersion languageVersion) {
    throw new UnsupportedOperationException("Not supported");
  }

  @Nonnull
  @Override
  public IFileElementType getFileNodeType() {
    return PLAIN_FILE_ELEMENT_TYPE;
  }

  @Override
  @Nonnull
  public TokenSet getWhitespaceTokens(@Nonnull LanguageVersion languageVersion) {
    return TokenSet.EMPTY;
  }

  @Override
  @Nonnull
  public TokenSet getCommentTokens(@Nonnull LanguageVersion languageVersion) {
    return TokenSet.EMPTY;
  }

  @Override
  @Nonnull
  public TokenSet getStringLiteralElements(@Nonnull LanguageVersion languageVersion) {
    return TokenSet.EMPTY;
  }

  @RequiredReadAction
  @Override
  @Nonnull
  public PsiElement createElement(@Nonnull ASTNode node) {
    return PsiUtilCore.NULL_PSI_ELEMENT;
  }

  @Override
  public PsiFile createFile(FileViewProvider viewProvider) {
    return new PsiPlainTextFileImpl(viewProvider);
  }
}
