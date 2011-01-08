package org.jetbrains.jet.lang.psi;

import com.intellij.psi.PsiElementVisitor;

/**
 * @author max
 */
public class JetVisitor extends PsiElementVisitor {
    public void visitJetElement(JetElement elem) {
        visitElement(elem);
    }

    public void visitDeclaration(JetDeclaration dcl) {
        visitJetElement(dcl);
    }

    public void visitNamespace(JetNamespace namespace) {
        visitDeclaration(namespace);
    }

    public void visitClass(JetClass klass) {
        visitDeclaration(klass);
    }

    public void visitClassObject(JetClassObject classObject) {
        visitDeclaration(classObject);
    }

    public void visitConstructor(JetConstructor constructor) {
        visitDeclaration(constructor);
    }

    public void visitDecomposer(JetDecomposer decomposer) {
        visitDeclaration(decomposer);
    }

    public void visitExtension(JetExtension extension) {
        visitDeclaration(extension);
    }

    public void visitFunction(JetFunction fun) {
        visitDeclaration(fun);
    }

    public void visitProperty(JetProperty property) {
        visitDeclaration(property);
    }

    public void visitTypedef(JetTypedef typedef) {
        visitDeclaration(typedef);
    }

    public void visitJetFile(JetFile file) {
        visitFile(file);
    }

    public void visitImportDirective(JetImportDirective importDirective) {
        visitJetElement(importDirective);
    }

    public void visitClassBody(JetClassBody classBody) {
        visitJetElement(classBody);
    }

    public void visitNamespaceBody(JetNamespaceBody body) {
        visitJetElement(body);
    }

    public void visitModifierList(JetModifierList list) {
        visitJetElement(list);
    }

    public void visitAttributeAnnotation(JetAttributeAnnotation annotation) {
        visitJetElement(annotation);
    }

    public void visitAttribute(JetAttribute attribute) {
        visitJetElement(attribute);
    }

    public void visitTypeParameterList(JetTypeParameterList list) {
        visitJetElement(list);
    }

    public void visitTypeParameter(JetTypeParameter parameter) {
        visitDeclaration(parameter);
    }

    public void visitEnumEntry(JetEnumEntry enumEntry) {
        visitClass(enumEntry);
    }

    public void visitParameterList(JetParameterList list) {
        visitJetElement(list);
    }

    public void visitParameter(JetParameter parameter) {
        visitDeclaration(parameter);
    }

    public void visitDelegationSpecifierList(JetDelegationSpecifierList list) {
        visitJetElement(list);
    }

    public void visitDelegationSpecifier(JetDelegationSpecifier specifier) {
        visitJetElement(specifier);
    }

    public void visitDelegationByExpressionSpecifier(JetDelegatorByExpressionSpecifier specifier) {
        visitDelegationSpecifier(specifier);
    }

    public void visitDelegationToSuperCallSpecifier(JetDelegatorToSuperCall call) {
        visitDelegationSpecifier(call);
    }

    public void visitDelegationToSuperClassSpecifier(JetDelegatorToSuperClass specifier) {
        visitDelegationSpecifier(specifier);
    }

    public void visitTypeReference(JetTypeReference typeReference) {
        visitJetElement(typeReference);
    }

    public void visitArgumentList(JetArgumentList list) {
        visitJetElement(list);
    }

    public void visitArgument(JetArgument argument) {
        visitJetElement(argument);
    }

    public void visitExpression(JetExpression expression) {
        visitJetElement(expression);
    }

    public void visitConstantExpression(JetConstantExpression expression) {
        visitExpression(expression);
    }

    public void visitReferenceExpression(JetReferenceExpression expression) {
        visitExpression(expression);
    }

    public void visitTupleExpression(JetTupleExpression expression) {
        visitExpression(expression);
    }

    public void visitPrefixExpression(JetPrefixExpression expression) {
        visitExpression(expression);
    }

    public void visitPostfixExpression(JetPostfixExpression expression) {
        visitExpression(expression);
    }

    public void visitTypeofExpression(JetTypeofExpression expression) {
        visitExpression(expression);
    }

    public void visitBinaryExpression(JetBinaryExpression expression) {
        visitExpression(expression);
    }

    public void visitNewExpression(JetNewExpression expression) {
        visitExpression(expression);
    }

    public void visitReturnExpression(JetReturnExpression expression) {
        visitExpression(expression);
    }

    public void visitThrowExpression(JetThrowExpression expression) {
        visitExpression(expression);
    }

    public void visitBreakExpression(JetBreakExpression expression) {
        visitExpression(expression);
    }

    public void visitContinueExpression(JetContinueExpression expression) {
        visitExpression(expression);
    }

    public void visitIfExpression(JetIfExpression expression) {
        visitExpression(expression);
    }

    public void visitTryExpression(JetTryExpression expression) {
        visitExpression(expression);
    }

    public void visitForExpression(JetForExpression expression) {
        visitExpression(expression);
    }

    public void visitWhileExpression(JetWhileExpression expression) {
        visitExpression(expression);
    }

    public void visitDoWhileExpression(JetDoWhileExpression expression) {
        visitExpression(expression);
    }

    public void visitFunctionLiteralExpression(JetFunctionLiteralExpression expression) {
        visitExpression(expression);
    }

    public void visitAnnotatedExpression(JetAnnotatedExpression expression) {
        visitExpression(expression);
    }

    public void visitCallExpression(JetCallExpression expression) {
        visitExpression(expression);
    }

    public void visitArrayAccessExpression(JetArrayAccessExpression expression) {
        visitExpression(expression);
    }

    public void visitQualifiedExpression(JetQualifiedExpression expression) {
        visitExpression(expression);
    }

    public void visitHashQualifiedExpression(JetHashQualifiedExpression expression) {
        visitQualifiedExpression(expression);
    }

    public void visitDotQualifiedExpression(JetDotQualifiedExpression expression) {
        visitQualifiedExpression(expression);
    }

    public void visitSafeQualifiedExpression(JetSafeQualifiedExpression expression) {
        visitQualifiedExpression(expression);
    }

    public void visitObjectLiteralExpression(JetObjectLiteralExpression expression) {
        visitExpression(expression);
    }

    public void visitRootNamespaceExpression(JetRootNamespaceExpression expression) {
        visitExpression(expression);
    }

    public void visitBlockExpression(JetBlockExpression expression) {
        visitExpression(expression);
    }

    public void visitCatchSection(JetCatchSection catchSection) {
        visitJetElement(catchSection);
    }

    public void visitFinallySection(JetFinallySection finallySection) {
        visitJetElement(finallySection);
    }
}
