package org.jetbrains.jet.lang.types;

/**
 * @author abreslav
 */
public class DeclarationDescriptorVisitor<R, D> {
    public R visitVariableDescriptor(VariableDescriptor descriptor, D data) {
        return null;
    }

    public R visitFunctionDescriptor(FunctionDescriptor descriptor, D data) {
        return null;
    }

    public R visitTypeParameterDescriptor(TypeParameterDescriptor descriptor, D data) {
        return null;
    }

    public R visitNamespaceDescriptor(NamespaceDescriptor namespaceDescriptor, D data) {
        return null;
    }

    public R visitClassDescriptor(ClassDescriptor descriptor, D data) {
        return null;
    }

    public R visitModuleDeclaration(ModuleDescriptor descriptor, D data) {
        return null;
    }

    public R visitConstructorDescriptor(ConstructorDescriptor constructorDescriptor, D data) {
        return visitFunctionDescriptor(constructorDescriptor, data);
    }

    public R visitLocalVariableDescriptor(LocalVariableDescriptor descriptor, D data) {
        return visitVariableDescriptor(descriptor, data);
    }

    public R visitPropertyDescriptor(PropertyDescriptor descriptor, D data) {
        return visitVariableDescriptor(descriptor, data);
    }

    public R visitValueParameterDescriptor(ValueParameterDescriptor descriptor, D data) {
        return visitVariableDescriptor(descriptor, data);
    }
}
