package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author abreslav
 */
public class FunctionDescriptorImpl extends DeclarationDescriptorImpl implements FunctionDescriptor {

    private List<TypeParameterDescriptor> typeParameters;

    private List<ValueParameterDescriptor> unsubstitutedValueParameters;

    private Type unsubstitutedReturnType;
    private final FunctionDescriptor original;

    public FunctionDescriptorImpl(
            @NotNull DeclarationDescriptor containingDeclaration,
            @NotNull List<Attribute> attributes,
            String name) {
        super(containingDeclaration, attributes, name);
        this.original = this;
    }

    public FunctionDescriptorImpl(
            @NotNull FunctionDescriptor original,
            @NotNull List<Attribute> attributes,
            String name) {
        super(original.getContainingDeclaration(), attributes, name);
        this.original = original;
    }

    public final void initialize(
            @NotNull List<TypeParameterDescriptor> typeParameters,
            @NotNull List<ValueParameterDescriptor> unsubstitutedValueParameters,
            @NotNull Type unsubstitutedReturnType) {
        this.typeParameters = typeParameters;
        this.unsubstitutedValueParameters = unsubstitutedValueParameters;
        this.unsubstitutedReturnType = unsubstitutedReturnType;
    }

    @Override
    @NotNull
    public List<TypeParameterDescriptor> getTypeParameters() {
        return typeParameters;
    }

    @Override
    @NotNull
    public List<ValueParameterDescriptor> getUnsubstitutedValueParameters() {
        return unsubstitutedValueParameters;
    }

    @Override
    @NotNull
    public Type getUnsubstitutedReturnType() {
        return unsubstitutedReturnType;
    }

    @NotNull
    @Override
    public FunctionDescriptor getOriginal() {
        return original;
    }

    @Override
    public <R, D> R accept(DeclarationDescriptorVisitor<R, D> visitor, D data) {
        return visitor.visitFunctionDescriptor(this, data);
    }
}
