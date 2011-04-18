package org.jetbrains.jet.lang.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author abreslav
 */
public class ValueParameterDescriptorImpl extends VariableDescriptorImpl implements MutableValueParameterDescriptor {
    private final boolean hasDefaultValue;
    private final boolean isVararg;
    private final boolean isVar;
    private final int index;

    public ValueParameterDescriptorImpl(
            @NotNull DeclarationDescriptor containingDeclaration,
            int index,
            @NotNull List<Attribute> attributes,
            @NotNull String name,
            @Nullable JetType inType,
            @NotNull JetType outType,
            boolean hasDefaultValue,
            boolean isVararg) {
        super(containingDeclaration, attributes, name, inType, outType);
        this.index = index;
        this.hasDefaultValue = hasDefaultValue;
        this.isVararg = isVararg;
        this.isVar = inType != null;
    }

    public ValueParameterDescriptorImpl(
            @NotNull DeclarationDescriptor containingDeclaration,
            int index,
            @NotNull List<Attribute> attributes,
            @NotNull String name,
            boolean isVar,
            boolean hasDefaultValue,
            boolean isVararg) {
        super(containingDeclaration, attributes, name, null, null);
        this.index = index;
        this.hasDefaultValue = hasDefaultValue;
        this.isVararg = isVararg;
        this.isVar = isVar;
    }

    @Override
    public void setType(@NotNull JetType type) {
        assert getOutType() == null;
        setOutType(type);
        if (isVar) {
            assert getInType() == null;
            setInType(type);
        }
    }

    @Override
    public int getIndex() {
        return index;
//        final JetDeclaration element = getPsiElement();
//        final PsiElement parent = element.getParent();
//        if (parent instanceof JetParameterList) {
//            return ((JetParameterList) parent).getParameters().indexOf(element);
//        }
//        throw new IllegalStateException("couldn't find index for parameter");
    }

    @Override
    public boolean hasDefaultValue() {
        return hasDefaultValue;
    }

    @Override
    public boolean isRef() {
        throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public boolean isVararg() {
        return isVararg;
    }

    @NotNull
    @Override
    public VariableDescriptor substitute(TypeSubstitutor substitutor) {
        throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public <R, D> R accept(DeclarationDescriptorVisitor<R, D> visitor, D data) {
        return visitor.visitValueParameterDescriptor(this, data);
    }
}
