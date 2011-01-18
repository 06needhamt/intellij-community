package org.jetbrains.jet.lang.types;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author abreslav
 */
public class ClassDescriptor extends MemberDescriptorImpl {
    private final TypeConstructor typeConstructor;

    public ClassDescriptor(
            List<Annotation> annotations,
            String name, List<TypeParameterDescriptor> typeParameters, Collection<Type> superclasses) {
        super(annotations, name);
        this.typeConstructor = new TypeConstructor(annotations, name, typeParameters, superclasses);
    }

    public ClassDescriptor(String name) {
        this(Collections.<Annotation>emptyList(),
                name, Collections.<TypeParameterDescriptor>emptyList(),
                Collections.<Type>singleton(JetStandardTypes.getAny()));
    }

    public TypeConstructor getTypeConstructor() {
        return typeConstructor;
    }
}
