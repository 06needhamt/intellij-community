package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnnecessaryFullyQualifiedNameInspection extends ClassInspection{
    @SuppressWarnings("PublicField")
    public boolean m_ignoreJavadoc = false;

    private final UnnecessaryFullyQualifiedNameFix fix =
            new UnnecessaryFullyQualifiedNameFix();

    public String getDisplayName(){
        return "Unnecessary fully qualified name";
    }

    public String getGroupDisplayName(){
        return GroupNames.STYLE_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel(
                "Ignore fully qualified names in javadoc",
                this,
                "m_ignoreJavadoc");
    }

    public String buildErrorString(PsiElement location){
        return "Fully qualified name #ref is unnecessary, and can be replaced with an import #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnnecessaryFullyQualifiedNameVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class UnnecessaryFullyQualifiedNameFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Replace with import";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final CodeStyleSettingsManager settingsManager =
                    CodeStyleSettingsManager.getInstance(project);
            final CodeStyleSettings settings =
                    settingsManager.getCurrentSettings();
            final boolean oldUseFQNamesInJavadoc =
                    settings.USE_FQ_CLASS_NAMES_IN_JAVADOC;
            final boolean oldUseFQNames = settings.USE_FQ_CLASS_NAMES;
            try{
                settings.USE_FQ_CLASS_NAMES_IN_JAVADOC = false;
                settings.USE_FQ_CLASS_NAMES = false;
                final PsiJavaCodeReferenceElement reference =
                        (PsiJavaCodeReferenceElement) descriptor
                                .getPsiElement();
                final PsiManager psiManager = reference.getManager();
                final CodeStyleManager styleManager =
                        psiManager.getCodeStyleManager();
                styleManager.shortenClassReferences(reference);
            } finally{
                settings.USE_FQ_CLASS_NAMES_IN_JAVADOC = oldUseFQNamesInJavadoc;
                settings.USE_FQ_CLASS_NAMES = oldUseFQNames;
            }
        }
    }

    private class UnnecessaryFullyQualifiedNameVisitor
            extends BaseInspectionVisitor{
        private boolean m_inClass = false;

        public void visitClass(@NotNull PsiClass aClass){
            final boolean wasInClass = m_inClass;
            if(!m_inClass){

                m_inClass = true;
                super.visitClass(aClass);
            }
            m_inClass = wasInClass;
        }

        public void visitReferenceElement(PsiJavaCodeReferenceElement element){
            super.visitReferenceElement(element);
            final String text = element.getText();
            if(text.indexOf((int) '.') < 0){
                return;
            }
            if(m_ignoreJavadoc){
                final PsiElement containingComment =
                        PsiTreeUtil.getParentOfType(element,
                                                    PsiDocComment.class);
                if(containingComment != null){
                    return;
                }
            }
            final PsiElement psiElement = element.resolve();
            if(!(psiElement instanceof PsiClass)){
                return;
            }
            final PsiReferenceParameterList typeParameters =
                    element.getParameterList();
            if(typeParameters == null)
            {
                return;
            }
            typeParameters.accept(this);
            final PsiClass aClass = (PsiClass) psiElement;
            final PsiClass outerClass =
                    ClassUtils.getOutermostContainingClass(aClass);
            final String fqName = outerClass.getQualifiedName();
            if(!text.startsWith(fqName)){
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) element.getContainingFile();
            if(!ImportUtils.nameCanBeImported(text, file)){
                return;
            }
            registerError(element);
        }
    }
}
