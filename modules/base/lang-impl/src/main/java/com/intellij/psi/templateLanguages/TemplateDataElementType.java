/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.templateLanguages;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.CharTable;
import com.intellij.util.LocalTimeCounter;
import consulo.lang.LanguageVersion;
import consulo.lang.LanguageVersionResolvers;
import consulo.ui.image.Image;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class TemplateDataElementType extends IFileElementType implements ITemplateDataElementType {
  public static final LanguageExtension<TreePatcher> TREE_PATCHER = new LanguageExtension<>("com.intellij.lang.treePatcher", new SimpleTreePatcher());

  @Nonnull
  private final IElementType myTemplateElementType;
  @Nonnull
  private final IElementType myOuterElementType;

  public TemplateDataElementType(@NonNls String debugName,
                                 Language language,
                                 @Nonnull IElementType templateElementType,
                                 @Nonnull IElementType outerElementType) {
    super(debugName, language);
    myTemplateElementType = templateElementType;
    myOuterElementType = outerElementType;
  }

  protected Lexer createBaseLexer(PsiFile file, TemplateLanguageFileViewProvider viewProvider) {
    final Language baseLanguage = viewProvider.getBaseLanguage();
    final LanguageVersion languageVersion = LanguageVersionResolvers.INSTANCE.forLanguage(baseLanguage).getLanguageVersion(baseLanguage, file);

    return LanguageParserDefinitions.INSTANCE.forLanguage(viewProvider.getBaseLanguage()).createLexer(languageVersion);
  }

  protected LanguageFileType createTemplateFakeFileType(final Language language) {
    return new TemplateFileType(language);
  }

  @Override
  public ASTNode parseContents(ASTNode chameleon) {
    final CharTable charTable = SharedImplUtil.findCharTableByTree(chameleon);
    final FileElement fileElement = TreeUtil.getFileElement((TreeElement)chameleon);
    final PsiFile psiFile = (PsiFile)fileElement.getPsi();
    PsiFile originalPsiFile = psiFile.getOriginalFile();

    final TemplateLanguageFileViewProvider viewProvider = (TemplateLanguageFileViewProvider)originalPsiFile.getViewProvider();

    final Language templateLanguage = getTemplateFileLanguage(viewProvider);
    final CharSequence sourceCode = chameleon.getChars();

    RangesCollector collector = new RangesCollector();
    final PsiFile templatePsiFile = createTemplateFile(psiFile, templateLanguage, sourceCode, viewProvider, collector);

    final FileElement templateFileElement = ((PsiFileImpl)templatePsiFile).calcTreeElement();

    DebugUtil.startPsiModification("template language parsing");
    try {
      prepareParsedTemplateFile(templateFileElement);
      insertOuters(templateFileElement, sourceCode, collector.myRanges, charTable);

      TreeElement childNode = templateFileElement.getFirstChildNode();

      DebugUtil.checkTreeStructure(templateFileElement);
      DebugUtil.checkTreeStructure(chameleon);
      if (fileElement != chameleon) {
        DebugUtil.checkTreeStructure(psiFile.getNode());
        DebugUtil.checkTreeStructure(originalPsiFile.getNode());
      }

      return childNode;
    }
    finally {
      DebugUtil.finishPsiModification();
    }
  }

  protected void prepareParsedTemplateFile(@Nonnull FileElement root) {
  }

  protected Language getTemplateFileLanguage(TemplateLanguageFileViewProvider viewProvider) {
    return viewProvider.getTemplateDataLanguage();
  }

  /**
   * Removing all non-template language tokens from the source code provided and building a template psiTree from the rest.
   * Ranges of removed tokens should be stored in the outerRangesCollector
   *
   * @param psiFile              chameleon's psi file
   * @param templateLanguage     template language to parse
   * @param sourceCode           source code: base language with template language
   * @param viewProvider         multi-tree view provider
   * @param outerRangesCollector collector for non-template elements ranges
   * @return template psiFile
   */
  protected PsiFile createTemplateFile(final PsiFile psiFile,
                                       final Language templateLanguage,
                                       final CharSequence sourceCode,
                                       final TemplateLanguageFileViewProvider viewProvider,
                                       @Nonnull RangesCollector outerRangesCollector) {
    final CharSequence templateSourceCode = createTemplateText(sourceCode, createBaseLexer(psiFile, viewProvider), outerRangesCollector);
    return createPsiFileFromSource(templateLanguage, templateSourceCode, psiFile.getManager());
  }

  /**
   * Removes non-template tokens from the sourceCode and returns the rest. Ranges of removed tokens should be stored in the outerRangesCollector
   *
   * @param sourceCode           source code with base and template languages
   * @param baseLexer            base language lexer
   * @param outerRangesCollector collector for non-template elements ranges
   * @return template source code
   */
  protected CharSequence createTemplateText(@Nonnull CharSequence sourceCode, @Nonnull Lexer baseLexer, @Nonnull RangesCollector outerRangesCollector) {
    StringBuilder result = new StringBuilder(sourceCode.length());
    baseLexer.start(sourceCode);

    TextRange currentRange = TextRange.EMPTY_RANGE;
    while (baseLexer.getTokenType() != null) {
      TextRange newRange = TextRange.create(baseLexer.getTokenStart(), baseLexer.getTokenEnd());
      assert currentRange.getEndOffset() == newRange.getStartOffset() : "Inconsistent tokens stream from " +
                                                                        baseLexer +
                                                                        ": " +
                                                                        getRangeDump(currentRange, sourceCode) +
                                                                        " followed by " +
                                                                        getRangeDump(newRange, sourceCode);
      currentRange = newRange;
      if (baseLexer.getTokenType() == myTemplateElementType) {
        appendCurrentTemplateToken(result, sourceCode, baseLexer);
      }
      else {
        outerRangesCollector.addRange(currentRange);
      }
      baseLexer.advance();
    }

    return result;
  }

  @Nonnull
  private static String getRangeDump(@Nonnull TextRange range, @Nonnull CharSequence sequence) {
    return "'" + StringUtil.escapeLineBreak(range.subSequence(sequence).toString()) + "' " + range;
  }

  protected void appendCurrentTemplateToken(StringBuilder result, CharSequence buf, Lexer lexer) {
    result.append(buf, lexer.getTokenStart(), lexer.getTokenEnd());
  }

  private void insertOuters(TreeElement templateFileElement,
                            @Nonnull CharSequence sourceCode,
                            @Nonnull List<TextRange> outerElementsRanges,
                            final CharTable charTable) {
    TreePatcher templateTreePatcher = TREE_PATCHER.forLanguage(templateFileElement.getPsi().getLanguage());

    int treeOffset = 0;
    LeafElement currentLeaf = TreeUtil.findFirstLeaf(templateFileElement);

    for (TextRange outerElementRange : outerElementsRanges) {
      while (currentLeaf != null && treeOffset < outerElementRange.getStartOffset()) {
        treeOffset += currentLeaf.getTextLength();
        int currentTokenStart = outerElementRange.getStartOffset();
        if (treeOffset > currentTokenStart) {
          currentLeaf = templateTreePatcher.split(currentLeaf, currentLeaf.getTextLength() - (treeOffset - currentTokenStart), charTable);
          treeOffset = currentTokenStart;
        }
        currentLeaf = (LeafElement)TreeUtil.nextLeaf(currentLeaf);
      }

      if (currentLeaf == null) {
        assert outerElementsRanges.get(outerElementsRanges.size() - 1) == outerElementRange : "This should only happen for the last inserted range. Got " +
                                                                                              outerElementsRanges.lastIndexOf(outerElementRange) +
                                                                                              " of " +
                                                                                              (outerElementsRanges.size() - 1);
        ((CompositeElement)templateFileElement)
                .rawAddChildren(createOuterLanguageElement(charTable.intern(outerElementRange.subSequence(sourceCode)), myOuterElementType));
        ((CompositeElement)templateFileElement).subtreeChanged();
        break;
      }

      final OuterLanguageElementImpl newLeaf = createOuterLanguageElement(charTable.intern(outerElementRange.subSequence(sourceCode)), myOuterElementType);
      templateTreePatcher.insert(currentLeaf.getTreeParent(), currentLeaf, newLeaf);
      currentLeaf.getTreeParent().subtreeChanged();
      currentLeaf = newLeaf;
    }
  }

  protected OuterLanguageElementImpl createOuterLanguageElement(@Nonnull CharSequence internedTokenText, @Nonnull IElementType outerElementType) {
    return new OuterLanguageElementImpl(outerElementType, internedTokenText);
  }

  protected PsiFile createPsiFileFromSource(final Language language, CharSequence sourceCode, PsiManager manager) {
    @NonNls final LightVirtualFile virtualFile = new LightVirtualFile("foo", createTemplateFakeFileType(language), sourceCode, LocalTimeCounter.currentTime());

    FileViewProvider viewProvider = new SingleRootFileViewProvider(manager, virtualFile, false) {
      @Override
      @Nonnull
      public Language getBaseLanguage() {
        return language;
      }
    };

    // Since we're already inside a template language PSI that was built regardless of the file size (for whatever reason),
    // there should also be no file size checks for template data files.
    SingleRootFileViewProvider.doNotCheckFileSizeLimit(virtualFile);

    return viewProvider.getPsi(language);
  }

  protected static class TemplateFileType extends LanguageFileType {
    private final Language myLanguage;

    public TemplateFileType(final Language language) {
      super(language);
      myLanguage = language;
    }

    @Override
    @Nonnull
    public String getDefaultExtension() {
      return "";
    }

    @Override
    @Nonnull
    @NonNls
    public String getDescription() {
      return "fake for language" + myLanguage.getID();
    }

    @Override
    @Nullable
    public Image getIcon() {
      return null;
    }

    @Override
    @Nonnull
    @NonNls
    public String getId() {
      return myLanguage.getID();
    }
  }

  protected static class RangesCollector {
    private final List<TextRange> myRanges = new ArrayList<>();

    public void addRange(@Nonnull TextRange newRange) {
      if (newRange.isEmpty()) {
        return;
      }
      if (!myRanges.isEmpty()) {
        int lastItemIndex = myRanges.size() - 1;
        TextRange lastRange = myRanges.get(lastItemIndex);
        if (lastRange.getEndOffset() == newRange.getStartOffset()) {
          myRanges.set(lastItemIndex, TextRange.create(lastRange.getStartOffset(), newRange.getEndOffset()));
          return;
        }
      }
      myRanges.add(newRange);
    }
  }
}
