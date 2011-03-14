package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.resolve.DescriptorUtil;

import java.util.List;

/**
 * @author abreslav
 */
public abstract class DeclarationDescriptorImpl extends AnnotatedImpl implements Named, DeclarationDescriptor {

    private final String name;
    private final DeclarationDescriptor containingDeclaration;

    public DeclarationDescriptorImpl(@Nullable DeclarationDescriptor containingDeclaration, List<Attribute> attributes, String name) {
        super(attributes);
        this.name = name;
        this.containingDeclaration = containingDeclaration;
    }

    @Override
    public String getName() {
        return name;
    }

    @NotNull
    @Override
    public DeclarationDescriptor getOriginal() {
        return this;
    }

    @Override
    public DeclarationDescriptor getContainingDeclaration() {
        return containingDeclaration;
    }

    @Override
    public void acceptVoid(DeclarationDescriptorVisitor<Void, Void> visitor) {
        accept(visitor, null);
    }

    @Override
    public String toString() {
        return DescriptorUtil.renderPresentableText(this) + "[" + getClass().getCanonicalName()+ "]";
    }
}
