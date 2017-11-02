// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectModelModificationService;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ExperimentalVarSupportFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    if (PsiUtil.getLanguageLevel(ref).isAtLeast(LanguageLevel.JDK_X)) return;
    if (!PsiKeyword.VAR.equals(ref.getReferenceName())) return;
    registrar.register(new IntentionAction() {
      @Nls
      @NotNull
      @Override
      public String getText() {
        return getFamilyName();
      }

      @Nls
      @NotNull
      @Override
      public String getFamilyName() {
        return "Enable support for beta java version";
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        int result = Messages.NO;
        if (Registry.is(LanguageLevel.EXPERIMENTAL_KEY, false) || new LegalNoticeDialog(project).showAndGet()) {
          result = Messages.YES;
        }
        if (result == Messages.YES) {
          Registry.get(LanguageLevel.EXPERIMENTAL_KEY).setValue(true);
          final Module module = ModuleUtilCore.findModuleForPsiElement(file);
          if (module != null) {
            WriteAction.run(() -> JavaProjectModelModificationService.getInstance(project).changeLanguageLevel(module, LanguageLevel.JDK_X));
          }
        }
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    });
  }

  @NotNull
  @Override
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }

  private static class LegalNoticeDialog extends DialogWrapper {
    public LegalNoticeDialog(@Nullable Project project) {
      super(project, false);
      setTitle("Experimental Feature Alert");
      init();
      setOKButtonText("Accept");
      setCancelButtonText("Decline");
      pack();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      JPanel iconPanel = new JPanel(new BorderLayout());
      iconPanel.add(new JBLabel(AllIcons.General.WarningDialog), BorderLayout.NORTH);
      iconPanel.setBorder(JBUI.Borders.emptyRight(2));
      panel.add(iconPanel, BorderLayout.WEST);
      JEditorPane message = new JEditorPane();
      message.setEditorKit(UIUtil.getHTMLEditorKit());
      message.setEditable(false);
      message.setBackground(UIUtil.getOptionPaneBackground());
      message.setPreferredSize(JBUI.size(500, 100));
      message.setText(UIUtil.toHtml(
        "You must accept the terms of legal notice of the beta Java specification to enable support for local variable type inference, i.e. recognition of keyword 'var' and inspections to replace explicit types with 'var' and return.<br/><br/>" +
        "<b>The implementation of an early-draft specification developed under the Java Community Process (JCP) is made available for testing and evaluation purposes only and is not compatible with any specification of the JCP.</b>"));
      panel.add(message, BorderLayout.CENTER);
      return panel;
    }
  }
}
