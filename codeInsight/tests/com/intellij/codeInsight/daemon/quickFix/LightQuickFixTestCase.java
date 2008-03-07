package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class LightQuickFixTestCase extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BEFORE_PREFIX = "before";

  protected boolean shouldBeAvailableAfterExecution() {
    return false;
  }

  protected void doTestFor(final String testName) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final String relativePath = getBasePath() + "/" + BEFORE_PREFIX + testName;
            final String testFullPath = getTestDataPath().replace(File.separatorChar, '/') + relativePath;
            final File ioFile = new File(testFullPath);
            try {
              String contents = StringUtil.convertLineSeparators(new String(FileUtil.loadFileText(ioFile, CharsetToolkit.UTF8)), "\n");
              configureFromFileText(ioFile.getName(), contents);
              final Pair<String, Boolean> pair = parseActionHint(getFile(), contents);
              final String text = pair.getFirst();
              final boolean actionShouldBeAvailable = pair.getSecond().booleanValue();

              beforeActionStarted(testName, contents);

              try {
                doAction(text, actionShouldBeAvailable, testFullPath, testName);
              }
              finally {
                afterActionCompleted(testName, contents);
              }
            }
            catch (Exception e) {
              e.printStackTrace();
              fail();
            }
          }
        });
      }
    }, "", "");
    System.out.print(testName + " ");
  }

  protected void afterActionCompleted(final String testName, final String contents) {
  }

  protected void beforeActionStarted(final String testName, final String contents) {
  }

  public static Pair<String, Boolean> parseActionHint(final PsiFile file, String contents) {
    PsiFile hostFile = InjectedLanguageUtil.getTopLevelFile(file);

    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(hostFile.getLanguage());
    String comment = commenter.getLineCommentPrefix();
    if (comment == null) {
      comment = commenter.getBlockCommentPrefix();
    }
    // "quick fix action text to perform" "should be available"
    Pattern pattern = Pattern.compile("^" + comment + " \"([^\"]*)\" \"(\\S*)\".*", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(contents);
    assertTrue(matcher.matches());
    final String text = matcher.group(1);
    final Boolean actionShouldBeAvailable = Boolean.valueOf(matcher.group(2));
    return Pair.create(text, actionShouldBeAvailable);
  }

  protected void doAction(final String text, final boolean actionShouldBeAvailable, final String testFullPath, final String testName)
    throws Exception {
    IntentionAction action = findActionWithText(text);
    if (action == null) {
      if (actionShouldBeAvailable) {
        List<IntentionAction> actions = getAvailableActions();
        List<String> texts = new ArrayList<String>();
        for (IntentionAction intentionAction : actions) {
          texts.add(intentionAction.getText());
        }
        Collection<HighlightInfo> infos = doHighlighting();
        fail("Action with text '" + text + "' is not available in test " + testFullPath+"\nAvailable actions: "+texts+"\n"+actions+"\nErrors:"+infos);
      }
    }
    else {
      if (!actionShouldBeAvailable) {
        fail("Action '" + text + "' is available in test " + testFullPath);
      }
      action.invoke(getProject(), getEditor(), getFile());
      if (!shouldBeAvailableAfterExecution()) {
        final IntentionAction afterAction = findActionWithText(text);
        if (afterAction != null) {
          fail("Action '" + text + "' is still available after its invocation in test " + testFullPath);
        }
      }
      final String expectedFilePath = getBasePath() + "/after" + testName;
      checkResultByFile("In file :" + expectedFilePath, expectedFilePath, false);
    }
  }

  protected IntentionAction findActionWithText(final String text) {
    final List<IntentionAction> actions = getAvailableActions();
    return findActionWithText(actions, text);
  }

  public static IntentionAction findActionWithText(final List<IntentionAction> actions, final String text) {
    for (IntentionAction action : actions) {
      if (text.equals(action.getText())) {
        return action;
      }
    }
    return null;
  }

  protected void doAllTests() throws Exception {
    final String testDirPath = getTestDataPath().replace(File.separatorChar, '/') + getBasePath();
    File testDir = new File(testDirPath);
    final File[] files = testDir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, @NonNls String name) {
        return name.startsWith(BEFORE_PREFIX);
      }
    });
    for (File file : files) {
      final String testName = file.getName().substring(BEFORE_PREFIX.length());
      doTestFor(testName);
    }
    assertTrue("Test files not found in "+testDirPath,files.length != 0);
  }

  protected List<IntentionAction> getAvailableActions() {
    final Collection<HighlightInfo> infos = doHighlighting();
    return getAvailableActions(infos, getEditor(), getFile());
  }

  public static List<IntentionAction> getAvailableActions(final Collection<HighlightInfo> infos, final Editor editor, final PsiFile file) {
    final int offset = editor.getCaretModel().getOffset();
    final List<IntentionAction> availableActions = new ArrayList<IntentionAction>();
    if (infos != null) {
      for (HighlightInfo info :infos) {
        final int startOffset = info.fixStartOffset;
        final int endOffset = info.fixEndOffset;
        if (startOffset <= offset && offset <= endOffset
            && info.quickFixActionRanges != null
        ) {
          for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
            final HighlightInfo.IntentionActionDescriptor actionDescriptor = pair.first;
            final TextRange range = pair.second;
            final IntentionAction action = actionDescriptor.getAction();
            final Project project = editor.getProject();
            if (range.getStartOffset() <= offset && offset <= range.getEndOffset() && action.isAvailable(project, editor, file)) {
              availableActions.add(action);
              final List<IntentionAction> actions = actionDescriptor.getOptions(file.findElementAt(editor.getCaretModel().getOffset()));
              if (actions != null) {
                for (IntentionAction intentionAction : actions) {
                  if (intentionAction.isAvailable(project, editor, file)) {
                    availableActions.add(intentionAction);
                  }
                }
              }
            }
          }
        }
      }
    }
    return availableActions;
  }

  @NonNls protected String getBasePath() {return null;}
}
