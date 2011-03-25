package org.jetbrains.jet.lang.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.types.*;

/**
 * @author abreslav
 */
public class JetScopeAdapter implements JetScope {
    @NotNull
    private final JetScope scope;

    public JetScopeAdapter(@NotNull JetScope scope) {
        this.scope = scope;
    }

    @NotNull
    @Override
    public JetType getThisType() {
        return scope.getThisType();
    }

    @NotNull
    @Override
    public FunctionGroup getFunctionGroup(@NotNull String name) {
        return scope.getFunctionGroup(name);
    }

    @Override
    public NamespaceDescriptor getNamespace(@NotNull String name) {
        return scope.getNamespace(name);
    }

    @Override
    public ClassifierDescriptor getClassifier(@NotNull String name) {
        return scope.getClassifier(name);
    }

    @Override
    public PropertyDescriptor getProperty(@NotNull String name) {
        return scope.getProperty(name);
    }

    @NotNull
    @Override
    public DeclarationDescriptor getContainingDeclaration() {
        return scope.getContainingDeclaration();
    }
}