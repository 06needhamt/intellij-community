package org.jetbrains.jet.codegen;

/**
 * @author yole
 */
public class ObjectGenTest extends CodegenTestCase {
    public void testSimpleObject() throws Exception {
        blackBoxFile("objects/simpleObject.jet");
    }

    public void testObjectLiteral() throws Exception {
        blackBoxFile("objects/objectLiteral.jet");
        System.out.println(generateToText());
    }

    public void testMethodOnObject() throws Exception {
        blackBoxFile("objects/methodOnObject.jet");
    }
}
