package org.jetbrains.jet.lang.types;

import java.util.Collection;
import java.util.List;

/**
 * @author abreslav
 */
public class TypeConstructor extends AnnotatedImpl {
    private final List<TypeParameterDescriptor> parameters;
    private final Collection<? extends Type> supertypes;
    private final String debugName;
    private final boolean sealed;

    public TypeConstructor(List<Attribute> attributes, boolean sealed, String debugName, List<TypeParameterDescriptor> parameters, Collection<? extends Type> supertypes) {
        super(attributes);
        this.sealed = sealed;
        this.debugName = debugName;
        this.parameters = parameters;
        this.supertypes = supertypes;
    }

    public List<TypeParameterDescriptor> getParameters() {
        return parameters;
    }

    public Collection<? extends Type> getSupertypes() {
        return supertypes;
    }

    @Override
    public String toString() {
        return debugName;
    }

    public boolean isSealed() {
        return sealed;
    }
}
