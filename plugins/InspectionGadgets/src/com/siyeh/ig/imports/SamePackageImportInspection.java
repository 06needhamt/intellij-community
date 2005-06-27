package com.siyeh.ig.imports;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;
import org.jetbrains.annotations.NotNull;

public class SamePackageImportInspection extends ClassInspection {
    private final DeleteImportFix fix = new DeleteImportFix();

    public String getDisplayName() {
        return "Import from same package";
    }

    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unnecessary import from same package '#ref' #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SamePackageImportVisitor();
    }

    private static class SamePackageImportVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            if(aClass.getContainingFile() instanceof JspFile){
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            if(file == null){
                return;
            }
            if (!file.getClasses()[0].equals(aClass)) {
                return;
            }
            final String packageName = file.getPackageName();
            final PsiImportList importList = file.getImportList();
            final PsiImportStatement[] importStatements = importList.getImportStatements();
            for(final PsiImportStatement importStatement : importStatements){
                final PsiJavaCodeReferenceElement reference = importStatement.getImportReference();
                if(reference != null){
                    final String text = importStatement.getQualifiedName();
                    if(importStatement.isOnDemand()){
                        if(packageName.equals(text)){
                            registerError(importStatement);
                        }
                    } else{
                        final int classNameIndex = text.lastIndexOf((int) '.');
                        final String parentName = classNameIndex < 0?"":
                                text.substring(0, classNameIndex);
                        if(packageName.equals(parentName)){
                            registerError(importStatement);
                        }
                    }
                }
            }
        }

    }
}
