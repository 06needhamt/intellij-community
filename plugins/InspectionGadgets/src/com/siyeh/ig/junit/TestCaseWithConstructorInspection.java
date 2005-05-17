package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class TestCaseWithConstructorInspection extends ClassInspection {
    public String getID(){
        return "JUnitTestCaseWithNonTrivialConstructors";
    }

    public String getDisplayName() {
        return "JUnit TestCase with non-trivial constructors";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Initialization logic in constructor #ref() instead of setUp()";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TestCaseWithConstructorVisitor();
    }

    private static class TestCaseWithConstructorVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            if (!method.isConstructor()) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null)
            {
                return;
            }
            if (isTrivial(method)) {
                return;
            }
            if(!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")){
                return;
            }
            registerMethodError(method);
        }

        private static boolean isTrivial(PsiMethod method) {
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return true;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements.length == 0) {
                return true;
            }
            if (statements.length > 1) {
                return false;
            }
            final PsiStatement statement = statements[0];
            if (!(statement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpression expression =
                    ((PsiExpressionStatement) statement).getExpression();
            if (expression == null) {
                return false;
            }
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) expression;
            final PsiReferenceExpression ref = call.getMethodExpression();
            if (ref == null) {
                return false;
            }
            final String text = ref.getText();
            return "super".equals(text);
        }

    }

}
