package org.jetbrains.jet.lang.resolve;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.types.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author abreslav
 */
public class BindingTraceContext extends BindingTrace implements BindingContext {
    private final Map<JetExpression, JetType> expressionTypes = new HashMap<JetExpression, JetType>();
    private final Map<JetReferenceExpression, DeclarationDescriptor> resolutionResults = new HashMap<JetReferenceExpression, DeclarationDescriptor>();
    private final Map<JetReferenceExpression, PsiElement> labelResolutionResults = new HashMap<JetReferenceExpression, PsiElement>();
    private final Map<JetTypeReference, JetType> types = new HashMap<JetTypeReference, JetType>();
    private final Map<DeclarationDescriptor, PsiElement> descriptorToDeclarations = new HashMap<DeclarationDescriptor, PsiElement>();
    private final Map<PsiElement, DeclarationDescriptor> declarationsToDescriptors = new HashMap<PsiElement, DeclarationDescriptor>();
    private final Set<JetFunctionLiteralExpression> blocks = new HashSet<JetFunctionLiteralExpression>();
    private JetScope toplevelScope;

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void recordExpressionType(@NotNull JetExpression expression, @NotNull JetType type) {
        expressionTypes.put(expression, type);
    }

    @Override
    public void recordReferenceResolution(@NotNull JetReferenceExpression expression, @NotNull DeclarationDescriptor descriptor) {
        resolutionResults.put(expression, descriptor);
    }

    @Override
    public void recordLabelResolution(@NotNull JetReferenceExpression expression, @NotNull PsiElement element) {
        labelResolutionResults.put(expression, element);
    }

    @Override
    public void removeReferenceResolution(@NotNull JetReferenceExpression referenceExpression) {
        resolutionResults.remove(referenceExpression);
    }

    @Override
    public void recordTypeResolution(@NotNull JetTypeReference typeReference, @NotNull JetType type) {
        types.put(typeReference, type);
    }

    @Override
    public void recordDeclarationResolution(@NotNull PsiElement declaration, @NotNull DeclarationDescriptor descriptor) {
        descriptorToDeclarations.put(descriptor.getOriginal(), declaration);
        declarationsToDescriptors.put(declaration, descriptor.getOriginal());
    }

    @Override
    public void recordBlock(JetFunctionLiteralExpression expression) {
        blocks.add(expression);
    }

    public void setToplevelScope(JetScope toplevelScope) {
        this.toplevelScope = toplevelScope;
    }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public DeclarationDescriptor getDeclarationDescriptor(JetDeclaration declaration) {
        return declarationsToDescriptors.get(declaration);
    }

    public NamespaceDescriptor getNamespaceDescriptor(JetNamespace declaration) {
        return (NamespaceDescriptor) declarationsToDescriptors.get(declaration);
    }

    @Override
    public ClassDescriptor getClassDescriptor(JetClass declaration) {
        return (ClassDescriptor) declarationsToDescriptors.get(declaration);
    }

    @Override
    public TypeParameterDescriptor getTypeParameterDescriptor(JetTypeParameter declaration) {
        return (TypeParameterDescriptor) declarationsToDescriptors.get(declaration);
    }

    @Override
    public FunctionDescriptor getFunctionDescriptor(JetFunction declaration) {
        return (FunctionDescriptor) declarationsToDescriptors.get(declaration);
    }

    @Override
    public PropertyDescriptor getPropertyDescriptor(JetProperty declaration) {
        return (PropertyDescriptor) declarationsToDescriptors.get(declaration);
    }

    @Override
    public JetType resolveTypeReference(JetTypeReference typeReference) {
        return types.get(typeReference);
    }

    @Override
    public JetType getExpressionType(JetExpression expression) {
        return expressionTypes.get(expression);
    }

    @Override
    public DeclarationDescriptor resolveReferenceExpression(JetReferenceExpression referenceExpression) {
        return resolutionResults.get(referenceExpression);
    }

    @Override
    public JetScope getTopLevelScope() {
        return toplevelScope;
    }

    @Override
    public PsiElement resolveToDeclarationPsiElement(JetReferenceExpression referenceExpression) {
        DeclarationDescriptor declarationDescriptor = resolveReferenceExpression(referenceExpression);
        if (declarationDescriptor == null) {
            return labelResolutionResults.get(referenceExpression);
        }
        return descriptorToDeclarations.get(declarationDescriptor.getOriginal());
    }

    @Override
    public PsiElement getDeclarationPsiElement(DeclarationDescriptor descriptor) {
        return descriptorToDeclarations.get(descriptor.getOriginal());
    }

    @Override
    public boolean isBlock(JetFunctionLiteralExpression expression) {
        return !expression.hasParameterSpecification() && blocks.contains(expression);
    }
}
