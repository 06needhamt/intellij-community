package org.jetbrains.jet.completion;

import junit.framework.TestSuite;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.JetTestCaseBuilder;
import org.jetbrains.jet.plugin.PluginTestCaseBase;

import java.io.File;

/**
 * Test auto completion messages
 *
 * @author Nikolay.Krasko
 */
public class KeywordsCompletionTest extends JetCompletionTestBase {

    private final String myPath;
    private final String myName;

    public KeywordsCompletionTest(@NotNull String path, @NotNull String name) {
        myPath = path;
        myName = name;

        // Set name explicitly because otherwise there will be "TestCase.fName cannot be null"
        setName("testComletionExecute");
    }

    public void testComletionExecute() {
        doTest();
    }

    @Override
    protected String getTestDataPath() {
        return new File(PluginTestCaseBase.getTestDataPathBase(), myPath).getPath() +
               File.separator;
    }

    @NotNull
    @Override
    public String getName() {
        return "test" + myName;
    }

    @NotNull
    public static TestSuite suite() {
        TestSuite suite = new TestSuite();

        JetTestCaseBuilder.appendTestsInDirectory(
                PluginTestCaseBase.getTestDataPathBase(), "/completion/keywords/", false,
                JetTestCaseBuilder.emptyFilter, new JetTestCaseBuilder.NamedTestFactory() {

            @NotNull
            @Override
            public junit.framework.Test createTest(@NotNull String dataPath, @NotNull String name, @NotNull File file) {
                return new KeywordsCompletionTest(dataPath, name);
            }
        }, suite);

        return suite;
    }
}