package org.jetbrains.jet.lang.types;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.JetScope;

/**
 * @author abreslav
 */
public class BindingTrace {
    public static final BindingTrace DUMMY = new BindingTrace();

    public void recordExpressionType(@NotNull JetExpression expression, @NotNull JetType type) {
    }

    public void recordReferenceResolution(@NotNull JetReferenceExpression expression, @NotNull DeclarationDescriptor descriptor) {

    }

    public void recordLabelResolution(@NotNull JetReferenceExpression expression, @NotNull PsiElement element) {

    }

    public void recordDeclarationResolution(@NotNull PsiElement declaration, @NotNull DeclarationDescriptor descriptor) {

    }

    public void recordTypeResolution(@NotNull JetTypeReference typeReference, @NotNull JetType type) {

    }

    public void setToplevelScope(JetScope toplevelScope) {

    }

    public void recordBlock(JetFunctionLiteralExpression expression) {

    }

    public void recordStatement(@NotNull JetElement statement) {

    }

    public void removeStatementRecord(@NotNull JetElement statement) {

    }

    public void removeReferenceResolution(@NotNull JetReferenceExpression referenceExpression) {

    }
}
