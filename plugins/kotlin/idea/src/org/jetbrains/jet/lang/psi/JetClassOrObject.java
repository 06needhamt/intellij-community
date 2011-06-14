package org.jetbrains.jet.lang.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author max
 */
public interface JetClassOrObject {
    List<JetDeclaration> getDeclarations();

    @Nullable
    JetDelegationSpecifierList getDelegationSpecifierList();

    @NotNull
    List<JetDelegationSpecifier> getDelegationSpecifiers();

    @NotNull
    List<JetClassInitializer> getAnonymousInitializers();

    // Objects always "have" a primary constructor
    boolean hasPrimaryConstructor();

    @Nullable
    String getName();

    @Nullable
    JetModifierList getModifierList();

    @Nullable
    JetObjectDeclarationName getNameAsDeclaration();
}
