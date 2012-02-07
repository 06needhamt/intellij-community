package org.jetbrains.jet.completion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Extract a number of statements about completion from the given text. Those statements
 * should be asserted during test execution.
 *
 * @author Nikolay Krasko
 */
public class ExpectedCompletionUtils {
    
    public static final String EXIST_LINE_PREFIX = "// EXIST:";
    public static final String ABSENT_LINE_PREFIX = "// ABSENT:";
    public static final String NUMBER_LINE_PREFIX = "// NUMBER:";
    public static final String EXECUTION_TIME_PREFIX = "// TIME:";

    private final String existLinePrefix;
    private final String absentLinePrefix;
    private final String numberLinePrefix;
    private final String executionTimePrefix;

    public ExpectedCompletionUtils() {
        this(EXIST_LINE_PREFIX, ABSENT_LINE_PREFIX, NUMBER_LINE_PREFIX, EXECUTION_TIME_PREFIX);
    }
    
    public ExpectedCompletionUtils(String existLinePrefix, String absentLinePrefix, String numberLinePrefix, String execitionTimePrefix) {
        this.existLinePrefix = existLinePrefix;
        this.absentLinePrefix = absentLinePrefix;
        this.numberLinePrefix = numberLinePrefix;
        this.executionTimePrefix = execitionTimePrefix;
    }

    @NotNull
    public String[] itemsShouldExist(String fileText) {
        return findListWithPrefix(existLinePrefix, fileText);
    }

    @NotNull
    public String[] itemsShouldAbsent(String fileText) {
        return findListWithPrefix(absentLinePrefix, fileText);
    }

    @Nullable
    public Integer getExpectedNumber(String fileText) {
        final String[] numberStrings = findListWithPrefix(numberLinePrefix, fileText);
        if (numberStrings.length > 0) {
            return Integer.parseInt(numberStrings[0]);
        }

        return null;
    }

    @Nullable
    public Integer getExecutionTime(String fileText) {
        final String[] numberStrings = findListWithPrefix(executionTimePrefix, fileText);
        if (numberStrings.length > 0) {
            return Integer.parseInt(numberStrings[0]);
        }

        return null;
    }

    @NotNull
    private static String[] findListWithPrefix(String prefix, String fileText) {
        ArrayList<String> result = new ArrayList<String>();

        for (String line : findLinesWithPrefixRemoved(prefix, fileText)) {
            String[] completions = line.split(",");

            for (String completion : completions) {
                result.add(completion.trim());
            }
        }

        return result.toArray(new String[result.size()]);
    }

    @NotNull
    private static List<String> findLinesWithPrefixRemoved(String prefix, String fileText) {
        ArrayList<String> result = new ArrayList<String>();

        for (String line : fileNonEmptyLines(fileText)) {
            if (line.startsWith(prefix)) {
                result.add(line.substring(prefix.length()).trim());
            }
        }

        return result;
    }

    @NotNull
    private static List<String> fileNonEmptyLines(String fileText) {
        ArrayList<String> result = new ArrayList<String>();

        try {
            BufferedReader reader = new BufferedReader(new StringReader(fileText));
            try {
                String line;

                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        result.add(line.trim());
                    }
                }
            } finally {
                reader.close();
            }
        } catch(IOException e) {
            throw new AssertionError(e);
        }

        return result;
    }
}
