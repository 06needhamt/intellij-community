package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotatedImpl;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.TypeParameterDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author abreslav
 */
public class TypeConstructorImpl extends AnnotatedImpl implements TypeConstructor {
    private final List<TypeParameterDescriptor> parameters;
    private Collection<JetType> supertypes;
    private final String debugName;
    private final boolean sealed;

    @Nullable
    private final DeclarationDescriptor declarationDescriptor;

    public TypeConstructorImpl(
            @Nullable DeclarationDescriptor declarationDescriptor,
            @NotNull List<AnnotationDescriptor> annotations,
            boolean sealed,
            @NotNull String debugName,
            @NotNull List<TypeParameterDescriptor> parameters,
            @NotNull Collection<JetType> supertypes) {
        super(annotations);
        this.declarationDescriptor = declarationDescriptor;
        this.sealed = sealed;
        this.debugName = debugName;
        this.parameters = new ArrayList<TypeParameterDescriptor>(parameters);
        this.supertypes = supertypes;
    }

    @Override
    @NotNull
    public List<TypeParameterDescriptor> getParameters() {
        return parameters;
    }

    @Override
    @NotNull
    public Collection<JetType> getSupertypes() {
        return supertypes;
    }

    @Override
    public String toString() {
        return debugName;
    }

    @Override
    public boolean isSealed() {
        return sealed;
    }

    @Override
    @Nullable
    public DeclarationDescriptor getDeclarationDescriptor() {
        return declarationDescriptor;
    }
}
