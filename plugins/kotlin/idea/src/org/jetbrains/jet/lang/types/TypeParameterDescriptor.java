package org.jetbrains.jet.lang.types;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author abreslav
 */
public class TypeParameterDescriptor extends NamedAnnotatedImpl {
    private final Variance variance;
    private final Collection<Type> upperBounds;
    private final TypeConstructor typeConstructor;

    public TypeParameterDescriptor(List<Annotation> annotations, String name, Variance variance, Collection<Type> upperBounds) {
        super(annotations, name);
        this.variance = variance;
        this.upperBounds = upperBounds;
        // TODO: Should we actually pass the annotations on to the type constructor?
        this.typeConstructor = new TypeConstructor(
                annotations,
                name,
                Collections.<TypeParameterDescriptor>emptyList(),
                upperBounds);
    }

    public Variance getVariance() {
        return variance;
    }

    public Collection<Type> getUpperBounds() {
        return upperBounds;
    }

    public TypeConstructor getTypeConstructor() {
        return typeConstructor;
    }
}
