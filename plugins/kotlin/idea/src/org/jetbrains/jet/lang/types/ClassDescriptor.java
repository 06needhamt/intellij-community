package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.resolve.JetScope;

import java.util.List;

/**
 * @author abreslav
 */
public interface ClassDescriptor extends DeclarationDescriptor {
    @NotNull
    TypeConstructor getTypeConstructor();

    @NotNull
    JetScope getMemberScope(List<TypeProjection> typeArguments);

    @NotNull
    FunctionGroup getConstructors(List<TypeProjection> typeArguments);

    @Override
    @NotNull
    DeclarationDescriptor getContainingDeclaration();

    /**
     * @return type A&lt;T&gt; for the class A&lt;T&gt;
     */
    @NotNull
    JetType getDefaultType();
}
