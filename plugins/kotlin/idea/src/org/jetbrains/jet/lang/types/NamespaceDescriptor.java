package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.resolve.JetScope;

import java.util.List;

/**
 * @author abreslav
 */
public class NamespaceDescriptor extends DeclarationDescriptorImpl {
    private NamespaceType namespaceType;

    private JetScope memberScope;

    public NamespaceDescriptor(@Nullable DeclarationDescriptor containingDeclaration, @NotNull List<Annotation> annotations, @NotNull String name) {
        super(containingDeclaration, annotations, name);
    }

    public void initialize(@NotNull JetScope memberScope) {
        this.memberScope = memberScope;
    }

    @NotNull
    public JetScope getMemberScope() {
        return memberScope;
    }

    @NotNull
    public NamespaceType getNamespaceType() {
        if (namespaceType == null) {
            assert memberScope != null : "Member scope not set";
            namespaceType = new NamespaceType(getName(), memberScope);
        }
        return namespaceType;
    }

    @NotNull
    @Override
    public NamespaceDescriptor substitute(TypeSubstitutor substitutor) {
        return this;
    }

    @Override
    public <R, D> R accept(DeclarationDescriptorVisitor<R, D> visitor, D data) {
        return visitor.visitNamespaceDescriptor(this, data);
    }
}
