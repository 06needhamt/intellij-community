package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.CodeInsightTestCase;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ModifiableModel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.mock.MockProgressIndicator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class DaemonAnalyzerTestCase extends CodeInsightTestCase {
  private Map<String, LocalInspectionTool> myAvailableTools = new THashMap<String, LocalInspectionTool>();
  private Map<String, LocalInspectionToolWrapper> myAvailableLocalTools = new THashMap<String, LocalInspectionToolWrapper>();

  protected void setUp() throws Exception {
    super.setUp();
    final LocalInspectionTool[] tools = configureLocalInspectionTools();
    for (LocalInspectionTool tool : tools) {
      enableInspectionTool(tool);
    }

    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE) {
      public ModifiableModel getModifiableModel() {
        mySource = this;
        return this;
      }

      public InspectionProfileEntry[] getInspectionTools() {
        final Collection<LocalInspectionToolWrapper> tools = myAvailableLocalTools.values();
        return tools.toArray(new LocalInspectionToolWrapper[tools.size()]);
      }

      public boolean isToolEnabled(HighlightDisplayKey key) {
        return key != null && myAvailableTools.containsKey(key.toString());
      }

      public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey key) {
        final LocalInspectionTool localInspectionTool = myAvailableTools.get(key.toString());
        return localInspectionTool != null ? localInspectionTool.getDefaultLevel() : HighlightDisplayLevel.WARNING;
      }

      public InspectionTool getInspectionTool(String shortName) {
        return myAvailableLocalTools.get(shortName);
      }
    };
    final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
    inspectionProfileManager.addProfile(profile);
    inspectionProfileManager.setRootProfile(profile.getName());
    InspectionProjectProfileManager.getInstance(getProject()).updateProfile(profile);
  }

  protected void tearDown() throws Exception {
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(getProject());
    codeAnalyzer.projectClosed();
    codeAnalyzer.disposeComponent();
    super.tearDown();
  }

  protected void enableInspectionTool(LocalInspectionTool tool){
    final String shortName = tool.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    if (key == null){
      HighlightDisplayKey.register(shortName, tool.getDisplayName(), tool.getID());
    }
    myAvailableTools.put(shortName, tool);
    myAvailableLocalTools.put(shortName, new LocalInspectionToolWrapper(tool));
  }

  protected void disableInspectionTool(String shortName){
    myAvailableTools.remove(shortName);
    myAvailableLocalTools.remove(shortName);
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[0];
  }

  protected void doTest(String filePath, boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) throws Exception {
    configureByFile(filePath);
    doDoTest(checkWarnings, checkInfos, checkWeakWarnings);
  }

  protected void doTest(String filePath, boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(filePath, checkWarnings, checkInfos, false);
  }

  protected void doTest(@NonNls String filePath, @NonNls String projectRoot, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFile(filePath, projectRoot);
    doDoTest(checkWarnings, checkInfos);
  }

  protected void doTest(VirtualFile vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    doTest(new VirtualFile[] { vFile }, checkWarnings, checkInfos );
  }

  protected void doTest(VirtualFile[] vFile, boolean checkWarnings, boolean checkInfos) throws Exception {
    configureByFiles(vFile,null);
    doDoTest(checkWarnings, checkInfos);
  }

  protected Collection<HighlightInfo> doDoTest(boolean checkWarnings, boolean checkInfos) {
    return doDoTest(checkWarnings, checkInfos, false);
  }

  protected Collection<HighlightInfo> doDoTest(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) {
    ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(),checkWarnings, checkWeakWarnings, checkInfos, myFile);

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    ((PsiFileImpl)myFile).calcTreeElement(); //to load text

    //to initialize caches
    myPsiManager.getCacheManager().getFilesWithWord("XXX", UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(myProject), true);
    VirtualFileFilter javaFilesFilter = new VirtualFileFilter() {
      public boolean accept(VirtualFile file) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
        return fileType == StdFileTypes.JAVA || fileType == StdFileTypes.CLASS;
      }
    };
    myPsiManager.setAssertOnFileLoadingFilter(javaFilesFilter); // check repository work

    Collection<HighlightInfo> infos = doHighlighting();

    myPsiManager.setAssertOnFileLoadingFilter(VirtualFileFilter.NONE);

    data.checkResult(infos, myEditor.getDocument().getText());

    return infos;
  }

  protected Collection<HighlightInfo> doHighlighting() {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    List<HighlightInfo> result = new ArrayList<HighlightInfo>();

    Document document = myEditor.getDocument();
    GeneralHighlightingPass action1 = new GeneralHighlightingPass(myProject, myFile, document, 0, myFile.getTextLength(), true);
    action1.doCollectInformation(new MockProgressIndicator());
    result.addAll(action1.getHighlights());

    PostHighlightingPass action2 = new PostHighlightingPass(myProject, myFile, myEditor, 0, myFile.getTextLength());
    action2.doCollectInformation(new MockProgressIndicator());
    result.addAll(action2.getHighlights());

    if (!myAvailableTools.isEmpty()) {
      LocalInspectionsPass inspectionsPass = new LocalInspectionsPass(myFile, myEditor.getDocument(), 0, myFile.getTextLength(),null);
      inspectionsPass.doCollectInformation(new MockProgressIndicator());
      result.addAll(inspectionsPass.getHighlights());
    }

    boolean isToLaunchExternal = true;
    for (HighlightInfo info : result) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        isToLaunchExternal = false;
        break;
      }
    }

    if (isToLaunchExternal && doExternalValidation()) {
      ExternalToolPass action3 = new ExternalToolPass(myFile, myEditor, 0, myEditor.getDocument().getTextLength());
      action3.doCollectInformation(new MockProgressIndicator());
      result.addAll(action3.getHighlights());
    }

    return result;
  }

  protected boolean doExternalValidation() {
    return true;
  }

  protected void findAndInvokeIntentionAction(final Collection<HighlightInfo> infos, String intentionActionName, final Editor editor,
                                              final PsiFile file) throws IncorrectOperationException {
    final List<IntentionAction> availableActionsAtCaret = LightQuickFixTestCase.getAvailableActions(infos, editor, file);
    IntentionAction intentionAction = LightQuickFixTestCase.findActionWithText(
      availableActionsAtCaret,
      intentionActionName
    );

    if (intentionAction != null) {
      intentionAction.invoke(myProject, editor, file);
      return;
    }

    final List<IntentionAction> availableActions = new ArrayList<IntentionAction>();

    for (HighlightInfo info :infos) {
      if (info.quickFixActionRanges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
          IntentionAction action = pair.first.getAction();
          if (action.isAvailable(getProject(), editor, file)) availableActions.add(action);
        }
      }
    }

    intentionAction = LightQuickFixTestCase.findActionWithText(
      availableActions,
      intentionActionName
    );

    assertNotNull(intentionAction);
    intentionAction.invoke(myProject, myEditor, myFile);
  }

  public void checkHighlighting(Editor editor, boolean checkWarnings, boolean checkInfos) {
    setActiveEditor(editor);
    doDoTest(checkWarnings, checkInfos);
  }
}