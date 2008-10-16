/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class RenameTagBeginOrEndIntentionAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.htmlInspections.RenameTagBeginOrEndIntentionAction");

  private boolean myStart;
  private String myTargetName;
  private String mySourceName;

  RenameTagBeginOrEndIntentionAction(@NotNull final String targetName, @NotNull final String sourceName, final boolean start) {
    myTargetName = targetName;
    mySourceName = sourceName;
    myStart = start;
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  @NotNull
  public String getText() {
    return getName();
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return true;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement psiElement = file.findElementAt(offset);

    if (psiElement == null || !psiElement.isValid()) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(psiElement.getContainingFile())) return;

    if (psiElement instanceof XmlToken) {
      final IElementType tokenType = ((XmlToken)psiElement).getTokenType();
      if (tokenType != XmlTokenType.XML_NAME) {
        if (tokenType == XmlTokenType.XML_TAG_END) {
          psiElement = psiElement.getPrevSibling();
          if (psiElement == null) return;
        }
      }

      PsiElement target = null;
      final String text = psiElement.getText();
      if (!myTargetName.equals(text)) {
        target = psiElement;
      }
      else {
        // we're in the other
        PsiElement parent = psiElement.getParent();
        if (parent instanceof PsiErrorElement) {
          parent = parent.getParent();
        }

        if (parent instanceof XmlTag) {
          if (myStart) {
            target = XmlTagUtil.getStartTagNameElement((XmlTag)parent);
          }
          else {
            target = XmlTagUtil.getEndTagNameElement((XmlTag)parent);
            if (target == null) {
              final PsiErrorElement errorElement = PsiTreeUtil.getChildOfType(parent, PsiErrorElement.class);
              target = XmlWrongClosingTagNameInspection.findEndTagName(errorElement);
            }
          }
        }
      }

      if (target != null) {
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document != null) {
          final TextRange textRange = target.getTextRange();
          document.replaceString(textRange.getStartOffset(), textRange.getEndOffset(), myTargetName);
        }
      }

    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  @NotNull
  public String getName() {
    return myStart
           ? XmlErrorMessages.message("rename.start.tag.name.intention", mySourceName, myTargetName)
           : XmlErrorMessages.message("rename.end.tag.name.intention", mySourceName, myTargetName);
  }
}
