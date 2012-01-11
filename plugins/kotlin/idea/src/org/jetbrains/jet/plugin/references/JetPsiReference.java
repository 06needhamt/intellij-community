package org.jetbrains.jet.plugin.references;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementResolveResult;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.ResolveResult;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.psi.JetReferenceExpression;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.BindingContextUtils;
import org.jetbrains.jet.plugin.compiler.WholeProjectAnalyzerFacade;

import java.util.ArrayList;
import java.util.Collection;

import static org.jetbrains.jet.lang.resolve.BindingContext.AMBIGUOUS_REFERENCE_TARGET;
import static org.jetbrains.jet.lang.resolve.BindingContext.DESCRIPTOR_TO_DECLARATION;

public abstract class JetPsiReference implements PsiPolyVariantReference {
    protected final JetReferenceExpression myExpression;

    protected JetPsiReference(JetReferenceExpression expression) {
        this.myExpression = expression;
    }

    @Override
    public PsiElement getElement() {
        return myExpression;
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        return doMultiResolve();
    }

    @Override
    public PsiElement resolve() {
        return doResolve();
    }

    @NotNull
    @Override
    public String getCanonicalText() {
        return "<TBD>";
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException();
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
        return resolve() == element;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        return EMPTY_ARRAY;
    }

    @Override
    public boolean isSoft() {
        return false;
    }

    protected PsiElement doResolve() {
        JetFile file = (JetFile) getElement().getContainingFile();
        BindingContext bindingContext = WholeProjectAnalyzerFacade.analyzeProjectWithCacheOnAFile(file);
        PsiElement psiElement = BindingContextUtils.resolveToDeclarationPsiElement(bindingContext, myExpression);
        if (psiElement != null) {
            return psiElement;
        }
        Collection<? extends DeclarationDescriptor> declarationDescriptors = bindingContext.get(AMBIGUOUS_REFERENCE_TARGET, myExpression);
        if (declarationDescriptors != null) return null;

        // TODO: Need a better resolution for intrinsic function (KT-975)
        return file;
    }

    protected ResolveResult[] doMultiResolve() {
        JetFile file = (JetFile) getElement().getContainingFile();
        BindingContext bindingContext = WholeProjectAnalyzerFacade.analyzeProjectWithCacheOnAFile(file);
        Collection<? extends DeclarationDescriptor> declarationDescriptors = bindingContext.get(AMBIGUOUS_REFERENCE_TARGET, myExpression);
        if (declarationDescriptors == null) return ResolveResult.EMPTY_ARRAY;

        ArrayList<ResolveResult> results = new ArrayList<ResolveResult>(declarationDescriptors.size());
        
        for (DeclarationDescriptor descriptor : declarationDescriptors) {
            PsiElement element = bindingContext.get(DESCRIPTOR_TO_DECLARATION, descriptor);
            if (element == null) {
                // TODO: Need a better resolution for intrinsic function (KT-975)
                element = file;
            }

            results.add(new PsiElementResolveResult(element, true));
        }

        return results.toArray(new ResolveResult[results.size()]);
    }
}
