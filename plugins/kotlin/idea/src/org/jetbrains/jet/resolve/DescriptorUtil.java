package org.jetbrains.jet.resolve;

import org.jetbrains.jet.lang.types.*;

import java.util.Iterator;
import java.util.List;

/**
 * @author abreslav
 */
public class DescriptorUtil {
    private DescriptorUtil() {}

    public static String renderPresentableText(DeclarationDescriptor declarationDescriptor) {
        if (declarationDescriptor == null) return "<null>";
        StringBuilder stringBuilder = new StringBuilder();
        declarationDescriptor.accept(
                new DeclarationDescriptorVisitor<Void, StringBuilder>() {
                    @Override
                    public Void visitPropertyDescriptor(PropertyDescriptor descriptor, StringBuilder builder) {
                        JetType outType = descriptor.getOutType();
                        JetType inType = descriptor.getInType();
                        String typeString = "<no type>";
                        if (inType != null && outType != null) {
                            builder.append("var ");
                            if (inType.equals(outType)) {
                                typeString = outType.toString();
                            }
                            else {
                                typeString = "<in " + inType + " out " + outType + ">";
                            }
                        }
                        else if (outType != null) {
                            builder.append("val ");
                            typeString = outType.toString();
                        }
                        else if (inType != null) {
                            builder.append("<write-only> ");
                            typeString = inType.toString();
                        }
                        builder.append(renderName(descriptor)).append(" : ").append(typeString);
                        return super.visitPropertyDescriptor(descriptor, builder); 
                    }

                    @Override
                    public Void visitFunctionDescriptor(FunctionDescriptor descriptor, StringBuilder builder) {
                        builder.append("fun ").append(renderName(descriptor));
                        List<TypeParameterDescriptor> typeParameters = descriptor.getTypeParameters();
                        renderTypeParameters(typeParameters, builder);
                        builder.append("(");
                        for (Iterator<ValueParameterDescriptor> iterator = descriptor.getUnsubstitutedValueParameters().iterator(); iterator.hasNext(); ) {
                            ValueParameterDescriptor parameterDescriptor = iterator.next();
                            visitPropertyDescriptor(parameterDescriptor, builder);
                            if (iterator.hasNext()) {
                                builder.append(", ");
                            }
                        }
                        builder.append(") : ").append(descriptor.getUnsubstitutedReturnType());
                        return super.visitFunctionDescriptor(descriptor, builder);
                    }

                    private void renderTypeParameters(List<TypeParameterDescriptor> typeParameters, StringBuilder builder) {
                        if (!typeParameters.isEmpty()) {
                            builder.append("<");
                            for (Iterator<TypeParameterDescriptor> iterator = typeParameters.iterator(); iterator.hasNext(); ) {
                                TypeParameterDescriptor typeParameterDescriptor = iterator.next();
                                renderTypeParameter(typeParameterDescriptor, builder);
                                if (iterator.hasNext()) {
                                    builder.append(", ");
                                }
                            }
                            builder.append(">");
                        }
                    }

                    @Override
                    public Void visitTypeParameterDescriptor(TypeParameterDescriptor descriptor, StringBuilder builder) {
                        builder.append("<");
                        renderTypeParameter(descriptor, builder);
                        builder.append(">");
                        return super.visitTypeParameterDescriptor(descriptor, builder);
                    }

                    @Override
                    public Void visitNamespaceDescriptor(NamespaceDescriptor namespaceDescriptor, StringBuilder builder) {
                        builder.append("namespace ").append(renderName(namespaceDescriptor));
                        return super.visitNamespaceDescriptor(namespaceDescriptor, builder);
                    }

                    @Override
                    public Void visitClassDescriptor(ClassDescriptor descriptor, StringBuilder builder) {
                        builder.append("class ").append(renderName(descriptor));
                        renderTypeParameters(descriptor.getTypeConstructor().getParameters(), builder);
                        return super.visitClassDescriptor(descriptor, builder);
                    }
                },
                stringBuilder);
        return stringBuilder.toString();
    }

    private static StringBuilder renderName(DeclarationDescriptor descriptor) {
        StringBuilder stringBuilder = new StringBuilder();
        renderName(descriptor, stringBuilder);
        return stringBuilder;
    }

    private static void renderName(DeclarationDescriptor descriptor, StringBuilder stringBuilder) {
        DeclarationDescriptor containingDeclaration = descriptor.getContainingDeclaration();
        if (containingDeclaration != null) {
            renderName(containingDeclaration, stringBuilder);
            stringBuilder.append("::");
        }
        stringBuilder.append(descriptor.getName());
    }

    private static void renderTypeParameter(TypeParameterDescriptor descriptor, StringBuilder builder) {
        builder.append(renderName(descriptor));
        if (!descriptor.getUpperBounds().isEmpty()) {
            JetType bound = descriptor.getUpperBounds().iterator().next();
            if (bound != JetStandardClasses.getAnyType()) {
                builder.append(" : ").append(bound);
                if (descriptor.getUpperBounds().size() > 1) {
                    builder.append(" (...)");
                }
            }
        }
    }

    public static String getFQName(DeclarationDescriptor descriptor) {
        DeclarationDescriptor container = descriptor.getContainingDeclaration();
        if (container != null && !(container instanceof ModuleDescriptor)) {
            return getFQName(container) + "." + descriptor.getName();
        }

        return descriptor.getName();
    }
}
