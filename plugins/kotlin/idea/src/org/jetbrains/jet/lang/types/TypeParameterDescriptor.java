package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author abreslav
 */
public class TypeParameterDescriptor extends DeclarationDescriptorImpl implements ClassifierDescriptor {
    private final Variance variance;
    private final Set<JetType> upperBounds;
    private final TypeConstructor typeConstructor;
    private final JetType boundsAsType;

    public TypeParameterDescriptor(
            @NotNull DeclarationDescriptor containingDeclaration,
            @NotNull List<Attribute> attributes,
            @NotNull Variance variance,
            @NotNull String name,
            @NotNull Set<JetType> upperBounds,
            @NotNull JetType boundsAsType) {
        super(containingDeclaration, attributes, name);
        this.variance = variance;
        this.upperBounds = upperBounds;
        // TODO: Should we actually pass the attributes on to the type constructor?
        this.typeConstructor = new TypeConstructorImpl(
                this,
                attributes,
                false,
                "&" + name,
                Collections.<TypeParameterDescriptor>emptyList(),
                upperBounds);
        this.boundsAsType = boundsAsType;
    }

    public TypeParameterDescriptor(
            @NotNull DeclarationDescriptor containingDeclaration,
            @NotNull List<Attribute> attributes,
            @NotNull Variance variance,
            @NotNull String name) {
        this(
            containingDeclaration,
            attributes,
            variance,
            name,
            Collections.singleton(JetStandardClasses.getNullableAnyType()),
            JetStandardClasses.getNullableAnyType());
    }

    public Variance getVariance() {
        return variance;
    }

    public Set<JetType> getUpperBounds() {
        return upperBounds;
    }

    @NotNull
    @Override
    public TypeConstructor getTypeConstructor() {
        return typeConstructor;
    }

    @Override
    public String toString() {
        return typeConstructor.toString();
    }

    @NotNull
    public JetType getBoundsAsType() {
        return boundsAsType;
    }

    @NotNull
    @Override
    public TypeParameterDescriptor substitute(TypeSubstitutor substitutor) {
        throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public <R, D> R accept(DeclarationDescriptorVisitor<R, D> visitor, D data) {
        return visitor.visitTypeParameterDescriptor(this, data);
    }
}
