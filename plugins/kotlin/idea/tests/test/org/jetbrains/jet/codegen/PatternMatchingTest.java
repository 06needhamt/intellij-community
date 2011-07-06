package org.jetbrains.jet.codegen;

import jet.NoPatternMatchedException;
import jet.Tuple2;

import java.lang.reflect.Method;

/**
 * @author yole
 */
public class PatternMatchingTest extends CodegenTestCase {
    @Override
    protected String getPrefix() {
        return "patternMatching";
    }

    public void testConstant() throws Exception {
        loadFile();
        Method foo = generateFunction();
        assertTrue((Boolean) foo.invoke(null, 0));
        assertFalse((Boolean) foo.invoke(null, 1));
    }

    public void testExceptionOnNoMatch() throws Exception {
        loadFile();
        Method foo = generateFunction();
        assertTrue((Boolean) foo.invoke(null, 0));
        assertThrows(foo, NoPatternMatchedException.class, null, 1);
    }

    public void testPattern() throws Exception {
        loadFile();
        Method foo = generateFunction();
        assertEquals("string", foo.invoke(null, ""));
        assertEquals("something", foo.invoke(null, new Object()));
    }

    public void testRange() throws Exception {
        loadFile();
        System.out.println(generateToText());
        Method foo = generateFunction();
        assertEquals("digit", foo.invoke(null, 9));
        assertEquals("something", foo.invoke(null, 19));
    }

    public void testRangeChar() throws Exception {
        loadFile();
        System.out.println(generateToText());
        Method foo = generateFunction();
        assertEquals("digit", foo.invoke(null, '0'));
        assertEquals("something", foo.invoke(null, 'A'));
    }

    public void testWildcardPattern() throws Exception {
        loadText("fun foo(x: String) = when(x) { is * => \"something\" }");
        Method foo = generateFunction();
        assertEquals("something", foo.invoke(null, ""));
    }

    public void testNoReturnType() throws Exception {
        loadText("fun foo(x: String) = when(x) { is * => \"x\" }");
        Method foo = generateFunction();
        assertEquals("x", foo.invoke(null, ""));
    }

    public void testTuplePattern() throws Exception {
        loadText("fun foo(x: Tuple2<Any, Any>) = when(x) { is (1,2) => \"one,two\"; else => \"something\" }");
        Method foo = generateFunction();
        assertEquals("one,two", foo.invoke(null, new Tuple2<Integer, Integer>(1, 2)));
        assertEquals("something", foo.invoke(null, new Tuple2<String, String>("not", "tuple")));
    }
}
