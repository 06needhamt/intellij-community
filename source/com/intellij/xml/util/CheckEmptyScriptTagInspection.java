/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.xml.util;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim Mossienko
 */
public class CheckEmptyScriptTagInspection extends LocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.util.CheckEmptyScriptTagInspection");
  @NonNls private static final String SCRIPT_TAG_NAME = "script";

  public boolean isEnabledByDefault() {
    return true;
  }

  public PsiElementVisitor buildVisitor(final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {}

      public void visitXmlTag(final XmlTag tag) {
        if (( SCRIPT_TAG_NAME.equals(tag.getName()) ||
              (tag instanceof HtmlTag && SCRIPT_TAG_NAME.equalsIgnoreCase(tag.getLocalName()))
            ) && tag.getLanguage() != StdLanguages.XML) {
          final ASTNode child = XmlChildRole.EMPTY_TAG_END_FINDER.findChild(tag.getNode());

          if (child != null) {

            final LocalQuickFix fix = new LocalQuickFix() {
              public String getName() {
                return XmlBundle.message("html.inspections.check.empty.script.tag.fix.message");
              }

              public void applyFix(Project project, ProblemDescriptor descriptor) {
                final StringBuilder builder = new StringBuilder(tag.getText());
                builder.replace(builder.length() - 2, builder.length(), "></" + SCRIPT_TAG_NAME + ">");

                try {
                  final FileType fileType = tag.getContainingFile().getFileType();
                  PsiFile file = tag.getManager().getElementFactory().createFileFromText(
                    "dummy." + (fileType == StdFileTypes.JSP || fileType == StdFileTypes.HTML ? "html":"xml"),
                    builder.toString()
                  );

                  tag.replace(((XmlFile)file).getDocument().getRootTag());
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }

              //to appear in "Apply Fix" statement when multiple Quick Fixes exist
              public String getFamilyName() {
                return getName();
              }
            };

            holder.registerProblem(tag,
                                   XmlBundle.message("html.inspections.check.empty.script.message"),
                                   fix);
          }
        }
      }
    };
  }

  public String getGroupDisplayName() {
    return GroupNames.HTML_INSPECTIONS;
  }

  public String getDisplayName() {
    return XmlBundle.message("html.inspections.check.empty.script.tag");
  }

  @NonNls
  public String getShortName() {
    return "CheckEmptyScriptTag";
  }
}
