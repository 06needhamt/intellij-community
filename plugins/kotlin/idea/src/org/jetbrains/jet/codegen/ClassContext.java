/*
 * @author max
 */
package org.jetbrains.jet.codegen;

import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.types.JetType;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.HashMap;

public class ClassContext {
    public static final ClassContext STATIC = new ClassContext(null, OwnerKind.NAMESPACE, null, null, null);
    private final DeclarationDescriptor contextType;
    private final OwnerKind contextKind;
    private final StackValue thisExpression;
    private final ClassContext parentContext;
    private final ClosureCodegen closure;
    private boolean thisWasUsed = false;
    
    HashMap<JetType,Integer> typeInfoConstants;

    public ClassContext(DeclarationDescriptor contextType, OwnerKind contextKind, StackValue thisExpression, ClassContext parentContext, ClosureCodegen closureCodegen) {
        this.contextType = contextType;
        this.contextKind = contextKind;
        this.thisExpression = thisExpression;
        this.parentContext = parentContext;
        closure = closureCodegen;
    }

    public DeclarationDescriptor getContextDescriptor() {
        return contextType;
    }

    public String getNamespaceClassName() {
        if(parentContext != STATIC)
            return parentContext.getNamespaceClassName();

        return NamespaceCodegen.getJVMClassName(contextType.getName());
    }

    public OwnerKind getContextKind() {
        return contextKind;
    }

    public StackValue getThisExpression() {
        if (parentContext == null) return StackValue.none();

        thisWasUsed = true;
        if (thisExpression != null) return thisExpression;
        return parentContext.getThisExpression();
    }

    public ClassContext intoNamespace(NamespaceDescriptor descriptor) {
        return new ClassContext(descriptor, OwnerKind.NAMESPACE, null, this, null);
    }

    public ClassContext intoClass(ClassDescriptor descriptor, OwnerKind kind) {
        final StackValue thisValue;
        thisValue = StackValue.local(0, JetTypeMapper.TYPE_OBJECT);

        return new ClassContext(descriptor, kind, thisValue, this, null);
    }

    public ClassContext intoFunction(FunctionDescriptor descriptor) {
        int thisIdx = -1;
        if (getContextKind() != OwnerKind.NAMESPACE) {
            thisIdx++;
        }

        final boolean hasReceiver = descriptor.getReceiverType() != null;
        if (hasReceiver) {
            thisIdx++;
        }

        return new ClassContext(descriptor, getContextKind(), hasReceiver ? StackValue.local(thisIdx, JetTypeMapper.TYPE_OBJECT) : null, this, null);
    }

    public ClassContext intoClosure(String internalClassName, ClosureCodegen closureCodegen) {
        final Type type = enclosingClassType(closureCodegen.state.getTypeMapper());
        StackValue outerClass = type != null
                                ? StackValue.instanceField(type, internalClassName, "this$0")
                                : StackValue.local(0, JetTypeMapper.TYPE_OBJECT);
        return new ClassContext(null, OwnerKind.IMPLEMENTATION, outerClass, this, closureCodegen);
    }

    public FrameMap prepareFrame() {
        FrameMap frameMap = new FrameMap();

        if (getContextKind() != OwnerKind.NAMESPACE) {
            frameMap.enterTemp();  // 0 slot for this
        }

        if (hasReceiver()) {
            frameMap.enterTemp();  // Next slot for fake this
        }

        return frameMap;
    }

    private JetType receiverType() {
        return contextType instanceof FunctionDescriptor ? ((FunctionDescriptor) contextType).getReceiverType() : null;
    }

    private boolean hasReceiver() {
        return receiverType() != null;
    }

    public ClassContext getParentContext() {
        return parentContext;
    }

    public Type jvmType(JetTypeMapper mapper) {
        if (contextType instanceof ClassDescriptor) {
            return mapper.jvmType((ClassDescriptor) contextType, contextKind);
        }
        else if (closure != null) {
            return Type.getObjectType(closure.name);
        }
        else {
            return parentContext != null ? parentContext.jvmType(mapper) : JetTypeMapper.TYPE_OBJECT;
        }
    }

    public DeclarationDescriptor getContextClass() {
        DeclarationDescriptor descriptor = getContextDescriptor();
        if (descriptor == null || descriptor instanceof ClassDescriptor || descriptor instanceof NamespaceDescriptor) return descriptor;

        final ClassContext parent = getParentContext();
        return parent != null ? parent.getContextClass() : null;
    }

    public boolean isThisWasUsed() {
        return thisWasUsed;
    }

    public StackValue lookupInContext(DeclarationDescriptor d, InstructionAdapter v) {
        final ClosureCodegen top = closure;
        if (top != null) {
            final StackValue answer = top.lookupInContext(d);
            if (answer != null) return answer;

            final StackValue thisContext = getThisExpression();
            thisContext.put(thisContext.type, v);
        }

        return parentContext != null ? parentContext.lookupInContext(d, v) : null;
    }

    public Type enclosingClassType(JetTypeMapper mapper) {
        DeclarationDescriptor descriptor = getContextDescriptor();
        if (descriptor instanceof ClassDescriptor) {
            return Type.getObjectType(mapper.jvmName((ClassDescriptor) descriptor, OwnerKind.INTERFACE));
        }

        if (descriptor instanceof NamespaceDescriptor) {
            return null;
        }

        if (closure != null) {
            return Type.getObjectType(closure.name);
        }

        final ClassContext parent = getParentContext();
        return parent != null ? parent.enclosingClassType(mapper) : null;
    }
    
    public int getTypeInfoConstantIndex(JetType type) {
        if(parentContext != STATIC)
            return parentContext.getTypeInfoConstantIndex(type);
        
        if(typeInfoConstants == null)
            typeInfoConstants = new HashMap<JetType, Integer>();

        Integer index = typeInfoConstants.get(type);
        if(index == null) {
            index = typeInfoConstants.size();
            typeInfoConstants.put(type, index);
        }
        return index;
    }
}
