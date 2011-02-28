package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.psi.JetFunction;

import java.util.*;

/**
 * @author abreslav
 */
public class FunctionDescriptorImpl extends DeclarationDescriptorImpl<JetFunction> implements FunctionDescriptor {
    @NotNull
    private final List<TypeParameterDescriptor> typeParameters;
    @NotNull
    private final List<ValueParameterDescriptor> unsubstitutedValueParameters;
    @NotNull
    private final Type unsubstitutedReturnType;
    @Nullable
    private final FunctionDescriptor original;

    public FunctionDescriptorImpl(
            JetFunction psiElement,
            @Nullable FunctionDescriptor original,
            @NotNull List<Attribute> attributes,
            String name,
            @NotNull List<TypeParameterDescriptor> typeParameters,
            @NotNull List<ValueParameterDescriptor> unsubstitutedValueParameters,
            @NotNull Type unsubstitutedReturnType) {
        super(psiElement, attributes, name);
        this.original = original;
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

    @Override
    public FunctionDescriptor getOriginal() {
        return original;
    }

}
