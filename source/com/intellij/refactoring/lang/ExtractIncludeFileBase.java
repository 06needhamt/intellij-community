package com.intellij.refactoring.lang;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author ven
 */
public abstract class ExtractIncludeFileBase implements RefactoringActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.lang.ExtractIncludeFileBase");
  private static final String REFACTORING_NAME = "Extract include file";
  protected PsiFile myIncludingFile;

  protected ExtractIncludeFileBase(final PsiFile includingFile) {
    myIncludingFile = includingFile;
  }

  protected abstract void doReplaceRange(final String includePath, final XmlTagChild first, final XmlTagChild last);

  protected abstract String doExtract(final PsiDirectory targetDirectory,
                           final String targetfileName,
                           final XmlTagChild first,
                           final XmlTagChild last,
                           final Language includingLanguage) throws IncorrectOperationException;

  protected abstract boolean verifyChildRange (final XmlTagChild first, final XmlTagChild last);

  protected void replaceDuplicates(final String includePath,
                                 final List<Pair<PsiElement, PsiElement>> duplicates,
                                 final Editor editor,
                                 final Project project) {
    if (duplicates.size() > 0) {
      final String message = "IDEA has found fragments that can be replaced with include directive\n" +
                             "Do you want to review them?";
      final int exitCode = Messages.showYesNoDialog(project, message, REFACTORING_NAME, Messages.getInformationIcon());
      if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            boolean replaceAll = false;
            for (Iterator<Pair<PsiElement, PsiElement>> it = duplicates.iterator(); it.hasNext();) {
              Pair<PsiElement, PsiElement> pair = it.next();
              if (!replaceAll) {

                highlightInEditor(project, pair, editor);

                ReplacePromptDialog promptDialog = new ReplacePromptDialog(false, "Replace Fragment", project);
                promptDialog.show();
                final int promptResult = promptDialog.getExitCode();
                if (promptResult == ReplacePromptDialog.PromptResult.SKIP) continue;
                if (promptResult == ReplacePromptDialog.PromptResult.CANCEL) break;

                if (promptResult == ReplacePromptDialog.PromptResult.OK) {
                  doReplaceRange(includePath, ((XmlTagChild)pair.getFirst()), (XmlTagChild)pair.getSecond());
                }
                else if (promptResult == ReplacePromptDialog.PromptResult.ALL) {
                  doReplaceRange(includePath, ((XmlTagChild)pair.getFirst()), (XmlTagChild)pair.getSecond());
                  replaceAll = true;
                }
                else {
                  LOG.error("Unknown return status");
                }
              }
              else {
                doReplaceRange(includePath, ((XmlTagChild)pair.getFirst()), (XmlTagChild)pair.getSecond());
              }
            }
          }
        }, "Remove Duplicates", null);
      }
    }
  }

  private void highlightInEditor(final Project project, final Pair<PsiElement, PsiElement> pair, final Editor editor) {
    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final int startOffset = pair.getFirst().getTextRange().getStartOffset();
    final int endOffset = pair.getSecond().getTextRange().getEndOffset();
    highlightManager.addRangeHighlight(editor, startOffset, endOffset, attributes, true, null);
    final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(startOffset);
    editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
  }

  public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {
  }

  private FileType getFileType(final Language language) {
    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      if (fileType instanceof LanguageFileType && language.equals(((LanguageFileType)fileType).getLanguage())) return fileType;
    }

    return null;
  }

  public void invoke(final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    if (!editor.getSelectionModel().hasSelection()) {
      String message = "Cannot extract: no selection";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_INCLUDE, project);
      return;
    }
    final int start = editor.getSelectionModel().getSelectionStart();
    final int end = editor.getSelectionModel().getSelectionEnd();

    final Pair<XmlTagChild, XmlTagChild> children = XmlUtil.findTagChildrenInRange(myIncludingFile, start, end);
    if (children == null) {
      String message = "Cannot extract: selection does not form a fragment for extraction";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_INCLUDE, project);
      return;
    }

    if (!verifyChildRange(children.getFirst(), children.getSecond())) {
      String message = "Cannot extract selected elements into include file";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_INCLUDE, project);
      return;
    }

    Language language = children.getFirst().getLanguage();
    if (language == null) {
      String message = "Cannot determine the language for selected elements";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_INCLUDE, project);
      return;
    }

    final FileType fileType = getFileType(language);
    if (!(fileType instanceof LanguageFileType)) {
      String message = "The language for selected elements has no associated file type";
      RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.EXTRACT_INCLUDE, project);
      return;
    }

    ExtractIncludeDialog dialog = new ExtractIncludeDialog(file.getContainingDirectory(), (LanguageFileType)fileType);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      final PsiDirectory targetDirectory = dialog.getTargetDirectory();
      LOG.assertTrue(targetDirectory != null);
      final String targetfileName = dialog.getTargetFileName();
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                final List<Pair<PsiElement, PsiElement>> duplicates = new ArrayList<Pair<PsiElement, PsiElement>>();
                final XmlTagChild first = children.getFirst();
                final XmlTagChild second = children.getSecond();
                CodeInsightUtil.findChildRangeDuplicates(first, second, duplicates, file);
                final String includePath = doExtract(targetDirectory, targetfileName, first, second,
                                                     file.getLanguage());

                LOG.assertTrue(includePath != null);
                doReplaceRange(includePath, first, second);

                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    replaceDuplicates(includePath, duplicates, editor, project);
                  }
                });
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          });
        }
      }, REFACTORING_NAME, null);

    }

    return;
  }
}
