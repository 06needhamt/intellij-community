package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FieldInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class RedundantFieldInitializationInspection extends FieldInspection {
    private static final Set<String> s_defaultValues = new HashSet<String>(10);
    private final RedundantFieldInitializationFix fix = new RedundantFieldInitializationFix();

    static {
        s_defaultValues.add("null");
        s_defaultValues.add("0");
        s_defaultValues.add("false");
        s_defaultValues.add("0.0");
        s_defaultValues.add("0.0F");
        s_defaultValues.add("0.0f");
        s_defaultValues.add("0L");
        s_defaultValues.add("0l");
        s_defaultValues.add("0x0");
        s_defaultValues.add("0X0");
    }

    public String getDisplayName() {
        return "Redundant field initialization";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RedundantFieldInitializationVisitor();
    }

    public String buildErrorString(PsiElement location) {
        return "Field initialization to '#ref' is redundant #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class RedundantFieldInitializationFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove initializer";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiExpression expression = (PsiExpression) descriptor.getPsiElement();
            PsiElement prevSibling = expression.getPrevSibling();
            PsiElement assignment = null;
            do {
                assert prevSibling != null;
                final PsiElement newPrevSibling = prevSibling.getPrevSibling();
                deleteElement(prevSibling);
                final String text = prevSibling.getText();
                if ("=".equals(text)) {
                    assignment = prevSibling;
                }
                prevSibling = newPrevSibling;
            }while(assignment == null);
            deleteElement(expression);
        }

    }

    private static class RedundantFieldInitializationVisitor extends BaseInspectionVisitor {

        public void visitField(@NotNull PsiField field) {
            super.visitField(field);
            if (!field.hasInitializer()) {
                return;
            }
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                return;
            }
            final PsiExpression initializer = field.getInitializer();
            final String text = initializer.getText();
            if (!s_defaultValues.contains(text)) {
                return;
            }
            registerError(initializer);
        }

    }
}
