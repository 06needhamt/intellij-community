package org.jetbrains.jet.checkers;

import com.google.common.collect.Lists;
import com.intellij.openapi.util.TextRange;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.JetLiteFixture;
import org.jetbrains.jet.JetTestCaseBase;
import org.jetbrains.jet.lang.diagnostics.DiagnosticUtils;
import org.jetbrains.jet.lang.psi.JetFile;
import org.jetbrains.jet.lang.resolve.AnalyzingUtils;
import org.jetbrains.jet.lang.resolve.BindingContext;
import org.jetbrains.jet.lang.resolve.ImportingStrategy;

import java.util.List;

/**
 * @author abreslav
 */
public class QuickJetPsiCheckerTest extends JetLiteFixture {
    private String name;
    
    public QuickJetPsiCheckerTest(@NonNls String dataPath, String name) {
        super(dataPath);
        this.name = name;
    }

    @Override
    public String getName() {
        return "test" + name;
    }

    @Override
    public void runTest() throws Exception {
        String expectedText = loadFile(name + ".jet");
        List<CheckerTestUtil.DiagnosedRange> diagnosedRanges = Lists.newArrayList();
        String clearText = CheckerTestUtil.parseDiagnosedRanges(expectedText, diagnosedRanges);

        createAndCheckPsiFile(name, clearText);
        JetFile jetFile = (JetFile) myFile;
        BindingContext bindingContext = AnalyzingUtils.getInstance(ImportingStrategy.NONE).analyzeFileWithCache(jetFile);

        CheckerTestUtil.diagnosticsDiff(diagnosedRanges, bindingContext.getDiagnostics(), new CheckerTestUtil.DiagnosticDiffCallbacks() {
            @Override
            public void missingDiagnostic(String type, int expectedStart, int expectedEnd) {
                String message = "Missing " + type + DiagnosticUtils.atLocation(myFile, new TextRange(expectedStart, expectedEnd));
                System.err.println(message);
            }

            @Override
            public void unexpectedDiagnostic(String type, int actualStart, int actualEnd) {
                String message = "Unexpected " + type + DiagnosticUtils.atLocation(myFile, new TextRange(actualStart, actualEnd));
                System.err.println(message);
            }
        });

        String actualText = CheckerTestUtil.addDiagnosticMarkersToText(jetFile, bindingContext).toString();

        assertEquals(expectedText, actualText);
    }

    public static Test suite() {
        return JetTestCaseBase.suiteForDirectory(JetTestCaseBase.getTestDataPathBase(), "/checkerWithErrorTypes/quick", true, new JetTestCaseBase.NamedTestFactory() {
            @NotNull
            @Override
            public Test createTest(@NotNull String dataPath, @NotNull String name) {
                return new QuickJetPsiCheckerTest(dataPath, name);
            }
        });
    }
}
