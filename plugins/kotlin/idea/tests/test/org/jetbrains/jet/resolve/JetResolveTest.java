package org.jetbrains.jet.resolve;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.*;
import org.jetbrains.jet.lang.types.*;
import org.jetbrains.jet.parsing.JetParsingTest;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;

/**
 * @author abreslav
 */
public class JetResolveTest extends LightDaemonAnalyzerTestCase {

    @Override
    protected String getTestDataPath() {
        return getHomeDirectory() + "/idea/testData";
    }

    private static String getHomeDirectory() {
       return new File(PathManager.getResourceRoot(JetParsingTest.class, "/org/jetbrains/jet/parsing/JetParsingTest.class")).getParentFile().getParentFile().getParent();
    }

    public void testBasic() throws Exception {
        JetFile jetFile = JetChangeUtil.createFile(getProject(), FileUtil.loadTextAndClose(new FileReader(getTestDataPath() + "/resolve/Basic.jet")));
        List<JetDeclaration> declarations = jetFile.getRootNamespace().getDeclarations();
        BindingContext bindingContext = new TopDownAnalyzer().process(JetStandardClasses.STANDARD_CLASSES, declarations);

        JetClass classADecl = (JetClass) declarations.get(0);
        ClassDescriptor classA = bindingContext.getClassDescriptor(classADecl);
        assertNotNull(classA);

        JetScope membersOfA = classA.getMemberScope(Collections.<TypeProjection>emptyList());
        ClassDescriptor classB = membersOfA.getClass("B");
        assertNotNull(classB);

        FunctionGroup fooFG = membersOfA.getFunctionGroup("foo");
        assertFalse(fooFG.isEmpty());

        assertReturnType(membersOfA, "foo", JetStandardClasses.getIntType());
        assertReturnType(membersOfA, "foo1", new TypeImpl(classB));
        assertReturnType(membersOfA, "fooB", JetStandardClasses.getIntType());

        JetFunction fooDecl = (JetFunction) classADecl.getDeclarations().get(1);
        Type expressionType = bindingContext.getExpressionType(fooDecl.getBodyExpression());
        assertEquals(JetStandardClasses.getIntType(), expressionType);

        DeclarationDescriptor resolve = bindingContext.resolve((JetReferenceExpression) fooDecl.getBodyExpression());
        assertSame(bindingContext.getFunctionDescriptor(fooDecl).getUnsubstitutedValueParameters().get(0), resolve);

        JetFunction fooBDecl = (JetFunction) classADecl.getDeclarations().get(2);
        JetCallExpression fooBBody = (JetCallExpression) fooBDecl.getBodyExpression();
        JetReferenceExpression refToFoo = (JetReferenceExpression) fooBBody.getCalleeExpression();
        FunctionDescriptor mustBeFoo = (FunctionDescriptor) bindingContext.resolve(refToFoo);
        assertSame(bindingContext.getFunctionDescriptor(fooDecl), mustBeFoo.getOriginal());

        JetClass classCDecl = (JetClass) declarations.get(1);
        ClassDescriptor classC = bindingContext.getClassDescriptor(classCDecl);
        assertNotNull(classC);
        assertEquals(1, classC.getTypeConstructor().getSupertypes().size());
        assertEquals(classA.getTypeConstructor(), classC.getTypeConstructor().getSupertypes().iterator().next().getConstructor());

        JetScope cScope = classC.getMemberScope(Collections.<TypeProjection>emptyList());
        ClassDescriptor classC_B = cScope.getClass("B");
        assertNotNull(classC_B);
        assertNotSame(classC_B, classB);
        assertEquals(classC.getTypeConstructor(), classC_B.getTypeConstructor().getSupertypes().iterator().next().getConstructor());
    }

    private void assertReturnType(JetScope membersOfA, String foo, Type returnType) {
        OverloadDomain overloadsForFoo = OverloadResolver.INSTANCE.getOverloadDomain(null, membersOfA, foo);
        FunctionDescriptor descriptorForFoo = overloadsForFoo.getFunctionDescriptorForPositionedArguments(Collections.<Type>emptyList(), Collections.<Type>emptyList());
        assertNotNull(descriptorForFoo);
        Type fooType = descriptorForFoo.getUnsubstitutedReturnType();
        assertEquals(returnType, fooType);
    }
}