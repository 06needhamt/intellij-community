package org.jetbrains.jet.codegen;

/**
 * @author max
 */
public class ClosuresGenTest extends CodegenTestCase {
    public void testSimplestClosure() throws Exception {
        blackBoxFile("classes/simplestClosure.jet");
        System.out.println(generateToText());
    }

    public void testSimplestClosureAndBoxing() throws Exception {
        blackBoxFile("classes/simplestClosureAndBoxing.jet");
    }

    public void testClosureWithParameter() throws Exception {
        blackBoxFile("classes/closureWithParameter.jet");
    }

    public void testClosureWithParameterAndBoxing() throws Exception {
        blackBoxFile("classes/closureWithParameterAndBoxing.jet");
    }

    public void testExtensionClosure() throws Exception {
        blackBoxFile("classes/extensionClosure.jet");
    }

    public void testEnclosingLocalVariable() throws Exception {
        blackBoxFile("classes/enclosingLocalVariable.jet");
        System.out.println(generateToText());
    }

    public void testDoubleEnclosedLocalVariable() throws Exception {
        blackBoxFile("classes/doubleEnclosedLocalVariable.jet");
    }

    public void testEnclosingThis() throws Exception {
        blackBoxFile("classes/enclosingThis.jet");
    }
}
