package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.javadoc.CommentFormatter;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.util.IncorrectOperationException;

/**
 *
 */
public class CodeFormatterFacade implements Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade");

  private CodeStyleSettings mySettings;
  private Helper myHelper;
  private CommentFormatter myCommentFormatter;

  public static int USE_NEW_CODE_FORMATTER = 1;

  public CodeFormatterFacade(CodeStyleSettings settings, Helper helper) {
    mySettings = settings;
    myHelper = helper;
    myCommentFormatter = new CommentFormatter(helper.getProject());
  }

  private TextRange formatComments(ASTNode element, int startOffset, int endOffset) {
    TextRange range = element.getTextRange();
    TextRange result = new TextRange(startOffset, endOffset);
    if (range.getStartOffset() >= startOffset && range.getEndOffset() <= endOffset) {
      myCommentFormatter.process(element);
      final TextRange newRange = element.getTextRange();
      result = new TextRange(startOffset, endOffset + newRange.getLength() - range.getLength());
    }

    if (element instanceof CompositeElement) {
      for (ASTNode elem = element.getFirstChildNode(); elem != null; elem = elem.getTreeNext()) {
        result = formatComments(elem, result.getStartOffset(), result.getEndOffset());
      }
    }
    return result;
  }

  public ASTNode process(ASTNode element, int parent_indent) {
    final FormattingModelBuilder builder = SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile().getLanguage()
      .getFormattingModelBuilder();
    if (builder != null) {
      TextRange range = element.getTextRange();
      return processRange(element, range.getStartOffset(), range.getEndOffset());
    }

    if (useNewFormatter(myHelper.getFileType())) {
      TextRange range = element.getTextRange();
      int startOffset = range.getStartOffset();
      int endOffset = range.getEndOffset();

      processRange(element, startOffset, endOffset);
      return element;
    }
    return element;
  }

  public ASTNode processRange(final ASTNode element, final int startOffset, final int endOffset) {
    final FileType fileType = myHelper.getFileType();

    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(element);
    final FormattingModelBuilder builder = SourceTreeToPsiMap.treeElementToPsi(element).getContainingFile().getLanguage()
      .getFormattingModelBuilder();
    
    if (builder != null) {
      TextRange range = formatComments(element, startOffset, endOffset);
      final SmartPsiElementPointer pointer = SmartPointerManager.getInstance(psiElement.getProject()).createSmartPsiElementPointer(psiElement);
      final PsiFile containingFile = psiElement.getContainingFile();
      final FormattingModel model = builder.createModel(containingFile, mySettings);
      if (containingFile.getTextLength() > 0) {
        try {
          FormatterEx.getInstanceEx().format(model, mySettings,
                                         mySettings.getIndentOptions(fileType), range);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }


      return SourceTreeToPsiMap.psiElementToTree(pointer.getElement());

    }

    return element;
  }

  private boolean useNewFormatter(FileType fileType) {
    return fileType instanceof LanguageFileType && USE_NEW_CODE_FORMATTER > 0;
  }
}

