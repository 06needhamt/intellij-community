package org.jetbrains.jet.codegen;

import org.jetbrains.jet.lang.descriptors.ClassDescriptor;
import org.jetbrains.jet.lang.descriptors.ValueParameterDescriptor;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.types.*;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.commons.Method;

import java.util.List;

/**
 * @author max
 */
public class FunctionCodegen {
    private final JetDeclaration owner;
    private final ClassVisitor v;
    private final BindingContext bindingContext;
    private final JetTypeMapper typeMapper;

    public FunctionCodegen(JetDeclaration owner, ClassVisitor v, JetStandardLibrary standardLibrary, BindingContext bindingContext) {
        this.owner = owner;
        this.v = v;
        this.bindingContext = bindingContext;
        typeMapper = new JetTypeMapper(standardLibrary, bindingContext);
    }

    public void gen(JetFunction f, OwnerKind kind) {
        Method method = typeMapper.mapSignature(f);
        List<ValueParameterDescriptor> paramDescrs = bindingContext.getFunctionDescriptor(f).getUnsubstitutedValueParameters();
        generateMethod(f, kind, method, paramDescrs);
    }

    public void generateMethod(JetDeclarationWithBody f, OwnerKind kind, Method jvmSignature, List<ValueParameterDescriptor> paramDescrs) {
        int flags = Opcodes.ACC_PUBLIC; // TODO.

        boolean isStatic = kind == OwnerKind.NAMESPACE;
        if (isStatic) flags |= Opcodes.ACC_STATIC;

        final JetExpression bodyExpression = f.getBodyExpression();
        boolean isAbstract = kind == OwnerKind.INTERFACE || bodyExpression == null;
        if (isAbstract) flags |= Opcodes.ACC_ABSTRACT;

        ClassDescriptor ownerClass = owner instanceof JetClass ? bindingContext.getClassDescriptor((JetClass) owner) : null;

        final MethodVisitor mv = v.visitMethod(flags, jvmSignature.getName(), jvmSignature.getDescriptor(), null, null);
        if (kind != OwnerKind.INTERFACE) {
            mv.visitCode();
            FrameMap frameMap = new FrameMap();

            if (kind != OwnerKind.NAMESPACE) {
                frameMap.enterTemp();  // 0 slot for this
            }

            Type[] argTypes = jvmSignature.getArgumentTypes();
            for (int i = 0; i < paramDescrs.size(); i++) {
                ValueParameterDescriptor parameter = paramDescrs.get(i);
                frameMap.enter(parameter, argTypes[i].getSize());
            }

            ExpressionCodegen codegen = new ExpressionCodegen(mv, bindingContext, frameMap, typeMapper, jvmSignature.getReturnType(), ownerClass, kind);
            if (kind instanceof OwnerKind.DelegateKind) {
                OwnerKind.DelegateKind dk = (OwnerKind.DelegateKind) kind;
                InstructionAdapter iv = new InstructionAdapter(mv);
                iv.load(0, JetTypeMapper.TYPE_OBJECT);
                dk.getDelegate().put(JetTypeMapper.TYPE_OBJECT, iv);
                for (int i = 0; i < argTypes.length; i++) {
                    Type argType = argTypes[i];
                    iv.load(i + 1, argType);
                }
                iv.invokeinterface(dk.getOwnerClass(), jvmSignature.getName(), jvmSignature.getDescriptor());
                iv.areturn(jvmSignature.getReturnType());
            }
            else {
                bodyExpression.accept(codegen);
                generateReturn(mv, bodyExpression, codegen);
            }
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private void generateReturn(MethodVisitor mv, JetExpression bodyExpression, ExpressionCodegen codegen) {
        if (!endsWithReturn(bodyExpression)) {
            final JetType expressionType = bindingContext.getExpressionType(bodyExpression);
            if (expressionType == null || expressionType.equals(JetStandardClasses.getUnitType())) {
                mv.visitInsn(Opcodes.RETURN);
            }
            else {
                codegen.returnTopOfStack();
            }
        }
    }

    private static boolean endsWithReturn(JetExpression bodyExpression) {
        if (bodyExpression instanceof JetBlockExpression) {
            final List<JetElement> statements = ((JetBlockExpression) bodyExpression).getStatements();
            return statements.size() > 0 && statements.get(statements.size()-1) instanceof JetReturnExpression;
        }
        return false;
    }
}
