package org.jetbrains.jet.lang.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.types.*;
import org.jetbrains.jet.lexer.JetTokens;

import java.util.*;

/**
 * @author abreslav
 */
public class ClassDescriptorResolver {
    public static final ClassDescriptorResolver INSTANCE = new ClassDescriptorResolver();

    private ClassDescriptorResolver() {}

    @Nullable
    public ClassDescriptor resolveClassDescriptor(@NotNull JetScope scope, @NotNull JetClass classElement) {
        ParameterExtensibleScope parameterScope = new ParameterExtensibleScope(scope);

        // This call has side-effects on the parameterScope (fills it in)
        List<TypeParameterDescriptor> typeParameters
                = resolveTypeParameters(parameterScope, classElement.getTypeParameters());

        List<JetDelegationSpecifier> delegationSpecifiers = classElement.getDelegationSpecifiers();
        // TODO : assuming that the hierarchy is acyclic
        Collection<? extends Type> superclasses = delegationSpecifiers.isEmpty()
                ? Collections.singleton(JetStandardClasses.getAnyType())
                : resolveTypes(parameterScope, delegationSpecifiers);
        boolean open = classElement.hasModifier(JetTokens.OPEN_KEYWORD);
        WritableScope members = resolveMembers(classElement, typeParameters, scope, parameterScope, superclasses);

        return new ClassDescriptor(
                AttributeResolver.INSTANCE.resolveAttributes(classElement.getModifierList()),
                !open,
                classElement.getName(),
                typeParameters,
                superclasses,
                members
        );
    }

    private WritableScope resolveMembers(
            final JetClass classElement,
            List<TypeParameterDescriptor> typeParameters,
            final JetScope outerScope,
            final JetScope typeParameterScope,
            final Collection<? extends Type> supertypes) {

        final WritableScope memberDeclarations = new WritableScope();

        List<JetDeclaration> declarations = classElement.getDeclarations();
        for (JetDeclaration declaration : declarations) {
            declaration.accept(new JetVisitor() {
                @Override
                public void visitProperty(JetProperty property) {
                    if (property.getPropertyTypeRef() != null) {
                        memberDeclarations.addPropertyDescriptor(resolvePropertyDescriptor(typeParameterScope, property));
                    } else {
                        // TODO : Caution: a cyclic dependency possible
                        throw new UnsupportedOperationException();
                    }
                }

                @Override
                public void visitFunction(JetFunction function) {
                    if (function.getReturnTypeRef() != null) {
                        memberDeclarations.addFunctionDescriptor(resolveFunctionDescriptor(typeParameterScope, function));
                    } else {
                        // TODO : Caution: a cyclic dependency possible
                        throw new UnsupportedOperationException();
                    }
                }

                @Override
                public void visitJetElement(JetElement elem) {
                    throw new UnsupportedOperationException(elem.toString());
                }
            });
        }

        return memberDeclarations;
    }

    private FunctionDescriptor resolveFunctionDescriptor(JetScope scope, JetFunction function) {
        ParameterExtensibleScope parameterScope = new ParameterExtensibleScope(scope);
        // The two calls below have side-effects on parameterScope
        List<TypeParameterDescriptor> typeParameterDescriptors = resolveTypeParameters(parameterScope, function.getTypeParameters());
        List<ValueParameterDescriptor> valueParameterDescriptors = resolveValueParameters(parameterScope, function.getValueParameters());

        Type returnType;
        JetTypeReference returnTypeRef = function.getReturnTypeRef();
        if (returnTypeRef != null) {
            returnType = TypeResolver.INSTANCE.resolveType(parameterScope, returnTypeRef);
            // TODO : CHeck type of body
        } else {
            JetExpression bodyExpression = function.getBodyExpression();
            assert bodyExpression != null : "No type, no body"; // TODO
            // TODO : Recursion possible
            returnType = JetTypeChecker.INSTANCE.getType(parameterScope, bodyExpression, function.hasBlockBody());
        }

        return new FunctionDescriptor(
                AttributeResolver.INSTANCE.resolveAttributes(function.getModifierList()),
                function.getName(),
                typeParameterDescriptors,
                valueParameterDescriptors,
                returnType
        );
    }

    private List<ValueParameterDescriptor> resolveValueParameters(ParameterExtensibleScope parameterScope, List<JetParameter> valueParameters) {
        List<ValueParameterDescriptor> result = new ArrayList<ValueParameterDescriptor>();
        for (JetParameter valueParameter : valueParameters) {
            JetTypeReference typeReference = valueParameter.getTypeReference();

            assert typeReference != null : "Parameters without type annotations are not supported"; // TODO

            ValueParameterDescriptor valueParameterDescriptor = new ValueParameterDescriptorImpl(
                    AttributeResolver.INSTANCE.resolveAttributes(valueParameter.getModifierList()),
                    valueParameter.getName(),
                    TypeResolver.INSTANCE.resolveType(parameterScope, typeReference),
                    valueParameter.getDefaultValue() != null
            );

            // TODO : Default values???

            result.add(valueParameterDescriptor);
            parameterScope.addPropertyDescriptor(valueParameterDescriptor);
        }
        return result;
    }

    private static List<TypeParameterDescriptor> resolveTypeParameters(ParameterExtensibleScope extensibleScope, List<JetTypeParameter> typeParameters) {
        // TODO : When-clause
        List<TypeParameterDescriptor> result = new ArrayList<TypeParameterDescriptor>();
        for (JetTypeParameter typeParameter : typeParameters) {
            result.add(resolveTypeParameter(extensibleScope, typeParameter));
        }
        return result;
    }

    private static TypeParameterDescriptor resolveTypeParameter(ParameterExtensibleScope extensibleScope, JetTypeParameter typeParameter) {
        JetTypeReference extendsBound = typeParameter.getExtendsBound();
        TypeParameterDescriptor typeParameterDescriptor = new TypeParameterDescriptor(
                AttributeResolver.INSTANCE.resolveAttributes(typeParameter.getModifierList()),
                typeParameter.getVariance(),
                typeParameter.getName(),
                extendsBound == null
                        ? Collections.<Type>singleton(JetStandardClasses.getAnyType())
                        : Collections.singleton(TypeResolver.INSTANCE.resolveType(extensibleScope, extendsBound))
        );
        extensibleScope.addTypeParameterDescriptor(typeParameterDescriptor);
        return typeParameterDescriptor;
    }

    private static Collection<? extends Type> resolveTypes(ParameterExtensibleScope extensibleScope, List<JetDelegationSpecifier> delegationSpecifiers) {
        if (delegationSpecifiers.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<Type> result = new ArrayList<Type>();
        for (JetDelegationSpecifier delegationSpecifier : delegationSpecifiers) {
            result.add(resolveType(extensibleScope, delegationSpecifier));
        }
        return result;
    }

    private static Type resolveType(JetScope scope, JetDelegationSpecifier delegationSpecifier) {
        JetTypeReference typeReference = delegationSpecifier.getTypeReference(); // TODO : make it not null
        return TypeResolver.INSTANCE.resolveType(scope, typeReference);
    }

    @NotNull
    public PropertyDescriptor resolvePropertyDescriptor(@NotNull JetScope scope, @NotNull JetParameter parameter) {
        return new PropertyDescriptorImpl(
                AttributeResolver.INSTANCE.resolveAttributes(parameter.getModifierList()),
                parameter.getName(),
                TypeResolver.INSTANCE.resolveType(scope, parameter.getTypeReference()));
    }

    public static PropertyDescriptor resolvePropertyDescriptor(@NotNull JetScope scope, JetProperty property) {
        // TODO : receiver?
        JetTypeReference propertyTypeRef = property.getPropertyTypeRef();

        Type type;
        if (propertyTypeRef == null) {
            JetExpression initializer = property.getInitializer();
            assert initializer != null;
            // TODO : ??? Fix-point here: what if we have something like "val a = foo {a.bar()}"
            type = JetTypeChecker.INSTANCE.getType(scope, initializer, false);
        } else {
            type = TypeResolver.INSTANCE.resolveType(scope, propertyTypeRef);
        }

        return new PropertyDescriptorImpl(
                AttributeResolver.INSTANCE.resolveAttributes(property.getModifierList()),
                property.getName(),
                type);
    }

    private static final class ParameterExtensibleScope extends JetScopeAdapter {

        private final Map<String, TypeParameterDescriptor> typeParameterDescriptors = new HashMap<String, TypeParameterDescriptor>();
        private final Map<String, PropertyDescriptor> propertyDescriptors = new HashMap<String, PropertyDescriptor>();

        private ParameterExtensibleScope(JetScope scope) {
            super(scope);
        }

        public void addTypeParameterDescriptor(TypeParameterDescriptor typeParameterDescriptor) {
            String name = typeParameterDescriptor.getName();
            if (typeParameterDescriptors.containsKey(name)) {
                throw new UnsupportedOperationException(); // TODO
            }
            typeParameterDescriptors.put(name, typeParameterDescriptor);
        }

        @Override
        public TypeParameterDescriptor getTypeParameter(String name) {
            TypeParameterDescriptor typeParameterDescriptor = typeParameterDescriptors.get(name);
            if (typeParameterDescriptor != null) {
                return typeParameterDescriptor;
            }
            return super.getTypeParameter(name);
        }

        public void addPropertyDescriptor(PropertyDescriptor descriptor) {
            String name = descriptor.getName();
            if (propertyDescriptors.containsKey(name)) {
                throw new UnsupportedOperationException(); // TODO
            }
            propertyDescriptors.put(name, descriptor);
        }

        @Override
        public PropertyDescriptor getProperty(String name) {
            PropertyDescriptor PropertyDescriptor = propertyDescriptors.get(name);
            if (PropertyDescriptor != null) {
                return PropertyDescriptor;
            }
            return super.getProperty(name);
        }

    }
}
