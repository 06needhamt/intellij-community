package org.jetbrains.jet.lang.resolve.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.descriptors.*;
import org.jetbrains.jet.lang.descriptors.annotations.AnnotationDescriptor;
import org.jetbrains.jet.lang.resolve.JetScope;
import org.jetbrains.jet.lang.resolve.SubstitutingScope;
import org.jetbrains.jet.lang.types.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author abreslav
 */
public class JavaClassDescriptor extends MutableDeclarationDescriptor implements ClassDescriptor {

    private TypeConstructor typeConstructor;
    private JavaClassMembersScope unsubstitutedMemberScope;
    private JetType classObjectType;
    private final WritableFunctionGroup constructors = new WritableFunctionGroup("<init>");
    private ClassModifiers modifiers;
//    private boolean isAbstract;
//    private boolean isOpen;
//    private boolean isTrait;

    public JavaClassDescriptor(DeclarationDescriptor containingDeclaration) {
        super(containingDeclaration);
    }

    public void setTypeConstructor(TypeConstructor typeConstructor) {
        this.typeConstructor = typeConstructor;
    }

    public void setModifiers(boolean isAbstract, boolean isOpen, boolean isTrait) {
        this.modifiers = new ClassModifiers(isAbstract, isOpen, isTrait);
//        this.isAbstract = isAbstract;
//        this.isOpen = isOpen;
//        this.isTrait = isTrait;
    }

    public void setUnsubstitutedMemberScope(JavaClassMembersScope memberScope) {
        this.unsubstitutedMemberScope = memberScope;
    }

    public void setClassObjectMemberScope(JavaClassMembersScope memberScope) {
        classObjectType = new JetTypeImpl(
                new TypeConstructorImpl(
                        JavaDescriptorResolver.JAVA_CLASS_OBJECT,
                        Collections.<AnnotationDescriptor>emptyList(),
                        true,
                        "Class object emulation for " + getName(),
                        Collections.<TypeParameterDescriptor>emptyList(),
                        Collections.<JetType>emptyList()
                ),
                memberScope
        );
    }

    public void addConstructor(ConstructorDescriptor constructorDescriptor) {
        this.constructors.addFunction(constructorDescriptor);
    }

    private TypeSubstitutor createTypeSubstitutor(List<TypeProjection> typeArguments) {
        List<TypeParameterDescriptor> parameters = getTypeConstructor().getParameters();
        Map<TypeConstructor, TypeProjection> context = TypeUtils.buildSubstitutionContext(parameters, typeArguments);
        return TypeSubstitutor.create(context);
    }

    @NotNull
    @Override
    public JetScope getMemberScope(List<TypeProjection> typeArguments) {
        assert typeArguments.size() == typeConstructor.getParameters().size();

        if (typeArguments.isEmpty()) return unsubstitutedMemberScope;

        TypeSubstitutor substitutor = createTypeSubstitutor(typeArguments);
        return new SubstitutingScope(unsubstitutedMemberScope, substitutor);
    }

    @NotNull
    @Override
    public FunctionGroup getConstructors() {
//        assert typeArguments.size() == typeConstructor.getParameters().size();
//        if (typeArguments.isEmpty()) return constructors;
//        return new LazySubstitutingFunctionGroup(createTypeSubstitutor(typeArguments), constructors);
        return constructors;
    }

    @Override
    public ConstructorDescriptor getUnsubstitutedPrimaryConstructor() {
        return null;
    }

    @Override
    public boolean hasConstructors() {
        return !constructors.isEmpty();
    }

    @NotNull
    @Override
    public TypeConstructor getTypeConstructor() {
        return typeConstructor;
    }

    @NotNull
    @Override
    public JetType getDefaultType() {
        return TypeUtils.makeUnsubstitutedType(this, unsubstitutedMemberScope);
    }

    @NotNull
    @Override
    public ClassDescriptor substitute(TypeSubstitutor substitutor) {
        throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public JetType getClassObjectType() {
        return classObjectType;
    }

    @Override
    public boolean isClassObjectAValue() {
        return false;
    }

    @Override
    public boolean isObject() {
        return false;
    }

//    @Override
//    public boolean isAbstract() {
//        return isAbstract;
//    }
//
//    @Override
//    public boolean isOpen() {
//        return isOpen;
//    }
//
//    @Override
//    public boolean isTrait() {
//        return isTrait;
//    }
    
    @Override
    @NotNull
    public ClassModifiers getClassModifiers() {
        return modifiers;
    }

    @Override
    public <R, D> R accept(DeclarationDescriptorVisitor<R, D> visitor, D data) {
        return visitor.visitClassDescriptor(this, data);
    }

    @Override
    public String toString() {
        return "java class " + typeConstructor;
    }
}
