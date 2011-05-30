package org.jetbrains.jet.lang.types;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.AnnotatedImpl;
import org.jetbrains.jet.lang.descriptors.Annotation;
import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.resolve.JetScope;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * @author abreslav
 */
public final class JetTypeImpl extends AnnotatedImpl implements JetType {

    private static final HashBiMap<TypeConstructor,TypeConstructor> EMPTY_AXIOMS = HashBiMap.<TypeConstructor, TypeConstructor>create();

    private final TypeConstructor constructor;
    private final List<TypeProjection> arguments;
    private final boolean nullable;
    private JetScope memberScope;

    public JetTypeImpl(List<Annotation> annotations, TypeConstructor constructor, boolean nullable, List<TypeProjection> arguments, JetScope memberScope) {
        super(annotations);
        this.constructor = constructor;
        this.nullable = nullable;
        this.arguments = arguments;
        this.memberScope = memberScope;
    }

    public JetTypeImpl(TypeConstructor constructor, JetScope memberScope) {
        this(Collections.<Annotation>emptyList(), constructor, false, Collections.<TypeProjection>emptyList(), memberScope);
    }

    public JetTypeImpl(@NotNull ClassDescriptor classDescriptor) {
        this(Collections.<Annotation>emptyList(),
                classDescriptor.getTypeConstructor(),
                false,
                Collections.<TypeProjection>emptyList(),
                classDescriptor.getMemberScope(Collections.<TypeProjection>emptyList()));
    }

    @Override
    public TypeConstructor getConstructor() {
        return constructor;
    }

    @Override
    public List<TypeProjection> getArguments() {
        return arguments;
    }

    @Override
    public boolean isNullable() {
        return nullable;
    }

    @NotNull
    @Override
    public JetScope getMemberScope() {
        if (memberScope == null) {
            // TODO : this was supposed to mean something...
            throw new IllegalStateException(this.toString());
        }
        return memberScope;
    }

    @Override
    public String toString() {
        return constructor + (arguments.isEmpty() ? "" : "<" + argumentsToString() + ">") + (isNullable() ? "?" : "");
    }

    private StringBuilder argumentsToString() {
        StringBuilder stringBuilder = new StringBuilder();
        for (Iterator<TypeProjection> iterator = arguments.iterator(); iterator.hasNext();) {
            TypeProjection argument = iterator.next();
            stringBuilder.append(argument);
            if (iterator.hasNext()) {
                stringBuilder.append(", ");
            }
        }
        return stringBuilder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JetTypeImpl type = (JetTypeImpl) o;

        // TODO
        return equalTypes(this, type, EMPTY_AXIOMS);
//        if (nullable != type.nullable) return false;
//        if (arguments != null ? !arguments.equals(type.arguments) : type.arguments != null) return false;
//        if (constructor != null ? !constructor.equals(type.constructor) : type.constructor != null) return false;
//        if (memberScope != null ? !memberScope.equals(type.memberScope) : type.memberScope != null) return false;

//        return true;
    }

    @Override
    public int hashCode() {
        int result = constructor != null ? constructor.hashCode() : 0;
        result = 31 * result + (arguments != null ? arguments.hashCode() : 0);
        result = 31 * result + (nullable ? 1 : 0);
        return result;
    }


    public static boolean equalTypes(@NotNull JetType type1, @NotNull JetType type2, @NotNull BiMap<TypeConstructor, TypeConstructor> equalityAxioms) {
        if (type1.isNullable() != type2.isNullable()) {
            return false;
        }
        TypeConstructor constructor1 = type1.getConstructor();
        TypeConstructor constructor2 = type2.getConstructor();
        if (!constructor1.equals(constructor2)) {
            TypeConstructor img1 = equalityAxioms.get(constructor1);
            TypeConstructor img2 = equalityAxioms.get(constructor2);
            if (!(img1 != null && img1.equals(constructor2)) &&
                    !(img2 != null && img2.equals(constructor1))) {
                return false;
            }
        }
        List<TypeProjection> type1Arguments = type1.getArguments();
        List<TypeProjection> type2Arguments = type2.getArguments();
        if (type1Arguments.size() != type2Arguments.size()) {
            return false;
        }

        for (int i = 0; i < type1Arguments.size(); i++) {
            TypeProjection typeProjection1 = type1Arguments.get(i);
            TypeProjection typeProjection2 = type2Arguments.get(i);
            if (typeProjection1.getProjectionKind() != typeProjection2.getProjectionKind()) {
                return false;
            }
            if (!equalTypes(typeProjection1.getType(), typeProjection2.getType(), equalityAxioms)) {
                return false;
            }
        }
        return true;
    }


}
