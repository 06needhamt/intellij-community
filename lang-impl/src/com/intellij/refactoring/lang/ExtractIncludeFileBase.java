package com.intellij.refactoring.lang;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.find.FindManager;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.PsiFileSystemItemUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public abstract class ExtractIncludeFileBase<T extends PsiElement> implements ExtractIncludeHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.lang.ExtractIncludeFileBase");
  private static final String REFACTORING_NAME = RefactoringBundle.message("extract.include.file.title");
  protected PsiFile myIncludingFile;
  public static final String HELP_ID = "refactoring.extractInclude";

  protected abstract void doReplaceRange(final String includePath, final T first, final T last);

  @NotNull
  protected String doExtract(final PsiDirectory targetDirectory,
                             final String targetfileName,
                             final T first,
                             final T last,
                             final Language includingLanguage) throws IncorrectOperationException {
    final PsiFile file = targetDirectory.createFile(targetfileName);
    Project project = targetDirectory.getProject();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = documentManager.getDocument(file);
    document.replaceString(0, document.getTextLength(), first.getText().trim());
    documentManager.commitDocument(document);
    PsiManager.getInstance(project).getCodeStyleManager().reformat(file);  //TODO: adjustLineIndent

    final String relativePath = PsiFileSystemItemUtil.getRelativePath(first.getContainingFile(), file);
    if (relativePath == null) throw new IncorrectOperationException("Cannot extract!");
    return relativePath;
  }

  protected abstract boolean verifyChildRange (final T first, final T last);

  private void replaceDuplicates(final String includePath,
                                   final List<Pair<PsiElement, PsiElement>> duplicates,
                                   final Editor editor,
                                   final Project project) {
    if (duplicates.size() > 0) {
      final String message = RefactoringBundle.message("idea.has.found.fragments.that.can.be.replaced.with.include.directive",
                                                  ApplicationNamesInfo.getInstance().getProductName());
      final int exitCode = Messages.showYesNoDialog(project, message, REFACTORING_NAME, Messages.getInformationIcon());
      if (exitCode == DialogWrapper.OK_EXIT_CODE) {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            boolean replaceAll = false;
            for (Pair<PsiElement, PsiElement> pair : duplicates) {
              if (!replaceAll) {

                highlightInEditor(project, pair, editor);

                ReplacePromptDialog promptDialog = new ReplacePromptDialog(false, RefactoringBundle.message("replace.fragment"), project);
                promptDialog.show();
                final int promptResult = promptDialog.getExitCode();
                if (promptResult == FindManager.PromptResult.SKIP) continue;
                if (promptResult == FindManager.PromptResult.CANCEL) break;

                if (promptResult == FindManager.PromptResult.OK) {
                  doReplaceRange(includePath, ((T)pair.getFirst()), (T)pair.getSecond());
                }
                else if (promptResult == FindManager.PromptResult.ALL) {
                  doReplaceRange(includePath, ((T)pair.getFirst()), (T)pair.getSecond());
                  replaceAll = true;
                }
                else {
                  LOG.error("Unknown return status");
                }
              }
              else {
                doReplaceRange(includePath, ((T)pair.getFirst()), (T)pair.getSecond());
              }
            }
          }
        }, RefactoringBundle.message("remove.duplicates.command"), null);
      }
    }
  }

  private static void highlightInEditor(final Project project, final Pair<PsiElement, PsiElement> pair, final Editor editor) {
    final HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    final int startOffset = pair.getFirst().getTextRange().getStartOffset();
    final int endOffset = pair.getSecond().getTextRange().getEndOffset();
    highlightManager.addRangeHighlight(editor, startOffset, endOffset, attributes, true, null);
    final LogicalPosition logicalPosition = editor.offsetToLogicalPosition(startOffset);
    editor.getScrollingModel().scrollTo(logicalPosition, ScrollType.MAKE_VISIBLE);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
  }

  @NotNull
  protected Language getLanguageForExtract(PsiElement firstExtracted) {
    return firstExtracted.getLanguage();
  }

  @Nullable
  private static FileType getFileType(final Language language) {
    final FileType[] fileTypes = FileTypeManager.getInstance().getRegisteredFileTypes();
    for (FileType fileType : fileTypes) {
      if (fileType instanceof LanguageFileType && language.equals(((LanguageFileType)fileType).getLanguage())) return fileType;
    }

    return null;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, DataContext dataContext) {
    myIncludingFile = file;
    if (!editor.getSelectionModel().hasSelection()) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("no.selection"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }
    final int start = editor.getSelectionModel().getSelectionStart();
    final int end = editor.getSelectionModel().getSelectionEnd();

    final Pair<T, T> children = findPairToExtract(start, end);
    if (children == null) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("selection.does.not.form.a.fragment.for.extraction"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }

    if (!verifyChildRange(children.getFirst(), children.getSecond())) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("cannot.extract.selected.elements.into.include.file"));
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }

    final FileType fileType = getFileType(getLanguageForExtract(children.getFirst()));
    if (!(fileType instanceof LanguageFileType)) {
      String message = RefactoringBundle.message("the.language.for.selected.elements.has.no.associated.file.type");
      CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HELP_ID);
      return;
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, file)) return;

    ExtractIncludeDialog dialog = createDialog(file.getContainingDirectory(), getExtractExtension(fileType, children.first));
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
                final T first = children.getFirst();
                final T second = children.getSecond();
                PsiEquivalenceUtil.findChildRangeDuplicates(first, second, duplicates, file);
                final String includePath = processPrimaryFragment(first, second, targetDirectory, targetfileName, file);

                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    replaceDuplicates(includePath, duplicates, editor, project);
                  }
                });
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }

              editor.getSelectionModel().removeSelection();
            }
          });
        }
      }, REFACTORING_NAME, null);

    }
  }

  protected ExtractIncludeDialog createDialog(final PsiDirectory containingDirectory, final String extractExtension) {
    return new ExtractIncludeDialog(containingDirectory, extractExtension);
  }

  @Nullable
  protected abstract Pair<T, T> findPairToExtract(int start, int end);

  @NonNls
  protected String getExtractExtension(final FileType extractFileType, final T first) {
    return extractFileType.getDefaultExtension();
  }

  public boolean isValidRange(final T firstToExtract, final T lastToExtract) {
    return verifyChildRange(firstToExtract, lastToExtract);
  }

  public String processPrimaryFragment(final T firstToExtract,
                                       final T lastToExtract,
                                       final PsiDirectory targetDirectory,
                                       final String targetfileName,
                                       final PsiFile srcFile) throws IncorrectOperationException {
    final String includePath = doExtract(targetDirectory, targetfileName, firstToExtract, lastToExtract,
                                         srcFile.getLanguage());

    doReplaceRange(includePath, firstToExtract, lastToExtract);
    return includePath;
  }

  public String getActionTitle() {
    return "Extract Include File...";
  }
}
