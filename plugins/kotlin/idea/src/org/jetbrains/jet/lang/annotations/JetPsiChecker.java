package org.jetbrains.jet.lang.annotations;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.MultiRangeReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.ErrorHandler;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.psi.JetReferenceExpression;
import org.jetbrains.jet.lang.resolve.AnalyzingUtils;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.types.DeclarationDescriptor;
import org.jetbrains.jet.lang.types.JetType;

import java.util.Collection;
import java.util.HashSet;

/**
 * @author abreslav
 */
public class JetPsiChecker implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull final AnnotationHolder holder) {
        if (element instanceof JetFile) {
            Project project = element.getProject();

            JetFile file = (JetFile) element;
            try {
                final Collection<DeclarationDescriptor> redeclarations = new HashSet<DeclarationDescriptor>();
                final BindingContext bindingContext = AnalyzingUtils.analyzeFile(file, new ErrorHandler() {
                    @Override
                    public void unresolvedReference(JetReferenceExpression referenceExpression) {
                        PsiReference reference = referenceExpression.getReference();
                        if (reference instanceof MultiRangeReference) {
                            MultiRangeReference mrr = (MultiRangeReference) reference;
                            for (TextRange range : mrr.getRanges()) {
                                holder.createErrorAnnotation(range.shiftRight(referenceExpression.getTextOffset()), "Unresolved").setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
                            }
                        } else {
                            holder.createErrorAnnotation(referenceExpression, "Unresolved").setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
                        }
                    }

                    @Override
                    public void structuralError(ASTNode node, String errorMessage) {
                        holder.createErrorAnnotation(node, errorMessage);
                    }

                    @Override
                    public void typeMismatch(JetExpression expression, JetType expectedType, JetType actualType) {
                        holder.createErrorAnnotation(expression, "Type mismatch: inferred type is " + actualType + " but " + expectedType + " was expected");
                    }

                    @Override
                    public void redeclaration(DeclarationDescriptor existingDescriptor, DeclarationDescriptor redeclaredDescriptor) {
                        redeclarations.add(existingDescriptor);
                        redeclarations.add(redeclaredDescriptor);
                    }
                });
                for (DeclarationDescriptor redeclaration : redeclarations) {
                    PsiElement declarationPsiElement = bindingContext.getDeclarationPsiElement(redeclaration);
                    if (declarationPsiElement != null) {
                        holder.createErrorAnnotation(declarationPsiElement, "Redeclaration");
                    }
                }
            }
            catch (ProcessCanceledException e) {
                throw e;
            }
            catch (Throwable e) {
                // TODO
                holder.createErrorAnnotation(element, e.getClass().getCanonicalName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
