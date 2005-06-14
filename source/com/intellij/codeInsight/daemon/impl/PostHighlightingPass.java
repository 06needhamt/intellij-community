package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.CodeInsightColors;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfile;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorMarkupModel;
import com.intellij.openapi.editor.markup.ErrorStripeRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerEx;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Collections;

public class PostHighlightingPass extends TextEditorHighlightingPass {
  private static final String SYMBOL_IS_NOT_USED = " ''{0}'' is never used";
  private static final String LOCAL_VARIABLE_IS_NOT_USED = "Variable ''{0}'' is never used";
  private static final String LOCAL_VARIABLE_IS_NOT_USED_FOR_READING = "Variable ''{0}'' is assigned but never accessed";
  private static final String LOCAL_VARIABLE_IS_NOT_ASSIGNED = "Variable ''{0}'' is never assigned";
  private static final String PRIVATE_FIELD_IS_NOT_USED = "Private field ''{0}'' is never used";
  private static final String PRIVATE_FIELD_IS_NOT_USED_FOR_READING = "Private field ''{0}'' is assigned but never accessed";
  private static final String PRIVATE_FIELD_IS_NOT_ASSIGNED = "Private field ''{0}'' is never assigned";
  private static final String PARAMETER_IS_NOT_USED = "Parameter ''{0}'' is never used";
  private static final String PRIVATE_METHOD_IS_NOT_USED = "Private method ''{0}'' is never used";
  private static final String PRIVATE_CONSTRUCTOR_IS_NOT_USED = "Private constructor ''{0}'' is never used";
  private static final String PRIVATE_INNER_CLASS_IS_NOT_USED = "Private inner class ''{0}'' is never used";
  private static final String PRIVATE_INNER_INTERFACE_IS_NOT_USED = "Private inner interface ''{0}'' is never used";
  private static final String TYPE_PARAMETER_IS_NOT_USED = "Type parameter ''{0}'' is never used";
  private static final String LOCAL_CLASS_IS_NOT_USED = "Local class ''{0}'' is never used";
  private static final String FIELD_IS_OVERWRITTEN = "Field ''{0}'' is overwritten by generated code";

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.PostHighlightingPass");
  private final Project myProject;
  private final RefCountHolder myRefCountHolder;
  private final PsiFile myFile;
  private final Editor myEditor;
  private final Document myDocument;
  private final int myStartOffset;
  private final int myEndOffset;
  private final boolean myCompiled;

  private Collection<HighlightInfo> myHighlights;
  private DaemonCodeAnalyzerSettings mySettings = DaemonCodeAnalyzerSettings.getInstance();
  private boolean myHasRedundantImports;
  private final CodeStyleManagerEx myStyleManager;
  private int myCurentEntryIndex;
  private boolean myHasMissortedImports;

  public PostHighlightingPass(Project project,
                              PsiFile file,
                              Editor editor,
                              int startOffset,
                              int endOffset,
                              boolean isCompiled) {
    super(editor.getDocument());
    myProject = project;
    myFile = file;
    myEditor = editor;
    myDocument = editor.getDocument();
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myCompiled = isCompiled;

    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    myRefCountHolder = daemonCodeAnalyzer.getFileStatusMap().getRefCountHolder(editor.getDocument(), myFile);
    myStyleManager = (CodeStyleManagerEx)CodeStyleManagerEx.getInstance(myProject);
    myCurentEntryIndex = -1;
  }

  public void doCollectInformation(ProgressIndicator progress) {
    if (myCompiled) {
      myHighlights = Collections.EMPTY_LIST;
      return;
    }

    PsiElement[] psiRoots = myFile.getPsiRoots();
    List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();

    for (final PsiElement psiRoot : psiRoots) {
      PsiElement[] elements = CodeInsightUtil.getElementsInRange(psiRoot, myStartOffset, myEndOffset);
      collectHighlights(elements, highlights);
    }

    PsiNamedElement[] unusedDcls = myRefCountHolder.getUnusedDcls();
    for (PsiNamedElement unusedDcl : unusedDcls) {
      String message = MessageFormat.format(SYMBOL_IS_NOT_USED, new Object[]{unusedDcl.getName()});
      String dclType = UsageViewUtil.capitalize(UsageViewUtil.getType(unusedDcl));
      if (dclType == null || dclType.length() == 0) dclType = "Symbol";

      HighlightInfo highlightInfo = createUnusedSymbolInfo(unusedDcl.getNavigationElement(), dclType + message);
      highlights.add(highlightInfo);
    }

    myHighlights = highlights;
  }

  public void doApplyInformationToEditor() {
    if (myHighlights == null) return;
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, myStartOffset, myEndOffset,
                                                   myHighlights, UpdateHighlightersUtil.POST_HIGHLIGHTERS_GROUP);

    DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    daemonCodeAnalyzer.getFileStatusMap().markFileUpToDate(myDocument, FileStatusMap.NORMAL_HIGHLIGHTERS);

    if (timeToOptimizeImports()) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  OptimizeImportsFix optimizeImportsFix = new OptimizeImportsFix();
                  PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                  if ((myHasRedundantImports || myHasMissortedImports)
                      && optimizeImportsFix.isAvailable(myProject, myEditor, myFile)
                      && myFile.isWritable()) {
                    optimizeImportsFix.invoke(myProject, myEditor, myFile);
                  }
                }
              });
            }
          });
        }
      });
    }
    //Q: here?
    ErrorStripeRenderer renderer = new RefreshStatusRenderer(myProject, daemonCodeAnalyzer, myDocument, myFile);
    Editor[] editors = EditorFactory.getInstance().getEditors(myDocument, myProject);
    for (Editor editor : editors) {
      ((EditorMarkupModel)editor.getMarkupModel()).setErrorStripeRenderer(renderer);
    }
  }

  public int getPassId() {
    return Pass.POST_UPDATE_ALL;
  }

  // for tests only
  public Collection<HighlightInfo> getHighlights() {
    return myHighlights;
  }

  private void collectHighlights(PsiElement[] elements, List<HighlightInfo> array) throws ProcessCanceledException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    for (PsiElement element : elements) {
      ProgressManager.getInstance().checkCanceled();

      HighlightInfo info = getHighlight(element);
      if (info != null) {
        array.add(info);
      }
    }
  }

  private HighlightInfo getHighlight(PsiElement element) {
    if (element instanceof PsiIdentifier) {
      return processIdentifier((PsiIdentifier)element);
    }
    else if (element instanceof PsiImportStatementBase) {
      return processImport((PsiImportStatementBase)element);
    }
    else if (element instanceof XmlAttributeValue) {
      return XmlHighlightVisitor.checkIdRefAttrValue((XmlAttributeValue)element,myRefCountHolder);
    } else {
      return null;
    }
  }

  private HighlightInfo processIdentifier(PsiIdentifier identifier) {
    InspectionProfileImpl profile = mySettings.getInspectionProfile();
    if (!profile.isToolEnabled(HighlightDisplayKey.UNUSED_SYMBOL)) return null;
    if (InspectionManagerEx.inspectionResultSuppressed(identifier, HighlightDisplayKey.UNUSED_SYMBOL.toString())) return null;
    HighlightInfo info;
    PsiElement parent = identifier.getParent();
    if (PsiUtil.hasErrorElementChild(parent)) return null;
    List<IntentionAction> options = new ArrayList<IntentionAction>();
    options.add(new AddNoInspectionCommentAction(HighlightDisplayKey.UNUSED_SYMBOL, identifier));
    options.add(new AddNoInspectionDocTagAction(HighlightDisplayKey.UNUSED_SYMBOL, identifier));
    options.add(new AddSuppressWarningsAnnotationAction(HighlightDisplayKey.UNUSED_SYMBOL, identifier));
    options.add(new AddNoInspectionAllForClassAction(identifier));
    options.add(new AddSuppressWarningsAnnotationForAllAction(identifier));
    options.add(new SwitchOffToolAction(HighlightDisplayKey.UNUSED_SYMBOL));
    InspectionProfile.UnusedSymbolSettings unusedSymbolSettings = profile.getUnusedSymbolSettings();
    if (parent instanceof PsiLocalVariable && unusedSymbolSettings.LOCAL_VARIABLE) {
      info = processLocalVariable((PsiLocalVariable)parent, options);
    }
    else if (parent instanceof PsiField && unusedSymbolSettings.FIELD) {
      info = processField((PsiField)parent, options);
    }
    else if (parent instanceof PsiParameter && unusedSymbolSettings.PARAMETER) {
      info = processParameter((PsiParameter)parent, options);
    }
    else if (parent instanceof PsiMethod && unusedSymbolSettings.METHOD) {
      info = processMethod((PsiMethod)parent, options);
    }
    else if (parent instanceof PsiClass && identifier.equals(((PsiClass)parent).getNameIdentifier()) && unusedSymbolSettings.CLASS) {
      info = processClass((PsiClass)parent, options);
    }
    else {
      return null;
    }
    return info;
  }

  private HighlightInfo processLocalVariable(PsiLocalVariable variable, final List<IntentionAction> options) {
    PsiIdentifier identifier = variable.getNameIdentifier();

    int count = myRefCountHolder.getRefCount(variable);
    if (count == 0) {
      String message = MessageFormat.format(LOCAL_VARIABLE_IS_NOT_USED, new Object[]{identifier.getText()});
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), options);
      return highlightInfo;
    }

    count = myRefCountHolder.getReadRefCount(variable);
    if (count == 0) {
      String message = MessageFormat.format(LOCAL_VARIABLE_IS_NOT_USED_FOR_READING, new Object[]{identifier.getText()});
      HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
      QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(variable), options);
      return highlightInfo;
    }

    if (!variable.hasInitializer()) {
      count = myRefCountHolder.getWriteRefCount(variable);
      if (count == 0) {
        String message = MessageFormat.format(LOCAL_VARIABLE_IS_NOT_ASSIGNED, new Object[]{identifier.getText()});
        final HighlightInfo unusedSymbolInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(HighlightDisplayKey.getDisplayNameByKey(HighlightDisplayKey.UNUSED_SYMBOL), options), options);
        return unusedSymbolInfo;
      }
    }

    return null;
  }

  private static HighlightInfo createUnusedSymbolInfo(PsiElement element, String message) {
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
    return HighlightInfo.createHighlightInfo(HighlightInfoType.UNUSED_SYMBOL, element.getTextRange(), message, attributes);
  }

  private HighlightInfo processField(PsiField field, final List<IntentionAction> options) {
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
      PsiIdentifier identifier = field.getNameIdentifier();

      int count = myRefCountHolder.getRefCount(field);
      if (count == 0) {
        if (isSerializationImplicitlyUsedField(field)) return null;
        String message = MessageFormat.format(PRIVATE_FIELD_IS_NOT_USED, new Object[]{identifier.getText()});
        HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(field), options);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(true, false, field), options);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(false, true, field), options);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(true, true, field), options);
        return highlightInfo;
      }

      count = myRefCountHolder.getReadRefCount(field);
      if (count == 0) {
        String message = MessageFormat.format(PRIVATE_FIELD_IS_NOT_USED_FOR_READING, new Object[]{identifier.getText()});
        HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedVariableFix(field), options);
        QuickFixAction.registerQuickFixAction(highlightInfo, new CreateGetterOrSetterAction(true, false, field), options);
        return highlightInfo;
      }

      if (!field.hasInitializer()) {
        count = myRefCountHolder.getWriteRefCount(field);
        if (count == 0) {
          if (!assignedByUIForm(field)) {
            String message = MessageFormat.format(PRIVATE_FIELD_IS_NOT_ASSIGNED, new Object[]{identifier.getText()});
            HighlightInfo info = createUnusedSymbolInfo(identifier, message);
            QuickFixAction.registerQuickFixAction(info, new CreateGetterOrSetterAction(false, true, field), options);
            return info;
          }
        }
      }
      else {
        if (assignedByUIForm(field)) {
          String message = MessageFormat.format(FIELD_IS_OVERWRITTEN, new Object[]{identifier.getText()});
          final HighlightInfo highlightInfo = HighlightInfo.createHighlightInfo(HighlightInfoType.WARNING, field.getInitializer(), message);
          QuickFixAction.registerQuickFixAction(highlightInfo, new EmptyIntentionAction(HighlightDisplayKey.getDisplayNameByKey(HighlightDisplayKey.UNUSED_SYMBOL), options), options);
          return highlightInfo;
        }
      }
    }

    return null;
  }

  private static boolean assignedByUIForm(PsiField field) {
    PsiSearchHelper searchHelper = field.getManager().getSearchHelper();
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null || containingClass.getQualifiedName() == null) return false;
    PsiFile[] forms = searchHelper.findFormsBoundToClass(containingClass.getQualifiedName());

    for (PsiFile formFile : forms) {
      PsiReference[] refs = formFile.getReferences();
      for (final PsiReference ref : refs) {
        if (ref.isReferenceTo(field)) return true;
      }
    }

    return false;
  }

  private HighlightInfo processParameter(PsiParameter parameter, final List<IntentionAction> options) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)declarationScope;
      if (PsiUtil.hasErrorElementChild(method)) return null;
      if ((method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE) ||
           method.hasModifierProperty(PsiModifier.STATIC))
          && !method.hasModifierProperty(PsiModifier.NATIVE)
      ) {
        if (isMainMethod(method)) return null;
        int count = myRefCountHolder.getRefCount(parameter);
        if (count == 0) {
          PsiIdentifier identifier = parameter.getNameIdentifier();
          String message = MessageFormat.format(PARAMETER_IS_NOT_USED, new Object[]{identifier.getText()});
          HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
          QuickFixAction.registerQuickFixAction(highlightInfo, new RemoveUnusedParameterFix(parameter), options);
          return highlightInfo;
        }
      }
    }
    else if (declarationScope instanceof PsiForeachStatement) {
      if (myRefCountHolder.getRefCount(parameter) == 0) {
        PsiIdentifier identifier = parameter.getNameIdentifier();
        String message = MessageFormat.format(PARAMETER_IS_NOT_USED, new Object[]{identifier.getText()});
        final HighlightInfo unusedSymbolInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(unusedSymbolInfo, new EmptyIntentionAction(HighlightDisplayKey.getDisplayNameByKey(HighlightDisplayKey.UNUSED_SYMBOL), options), options);
        return unusedSymbolInfo;
      }
    }

    return null;
  }

  private HighlightInfo processMethod(PsiMethod method, final List<IntentionAction> options) {
    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
      int count = myRefCountHolder.getRefCount(method);
      if (count == 0) {
        if (isWriteObjectMethod(method) ||
            isWriteReplaceMethod(method) ||
            isReadObjectMethod(method) ||
            isReadObjectNoDataMethod(method) ||
            isReadResolveMethod(method) ||
            isIntentionalPrivateConstructor(method)
        ) {
          return null;
        }
        String pattern = method.isConstructor() ? PRIVATE_CONSTRUCTOR_IS_NOT_USED : PRIVATE_METHOD_IS_NOT_USED;
        String symbolName = HighlightMessageUtil.getSymbolName(method, PsiSubstitutor.EMPTY);
        String message = MessageFormat.format(pattern, new Object[]{symbolName});
        PsiIdentifier identifier = method.getNameIdentifier();
        HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
        QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(method), options);
        return highlightInfo;
      }
    }
    return null;
  }

  private HighlightInfo processClass(PsiClass aClass, final List<IntentionAction> options) {
    if (aClass.getContainingClass() != null && aClass.hasModifierProperty(PsiModifier.PRIVATE)) {
      int count = myRefCountHolder.getRefCount(aClass);
      if (count == 0) {
        String pattern = aClass.isInterface()
                         ? PRIVATE_INNER_INTERFACE_IS_NOT_USED
                         : PRIVATE_INNER_CLASS_IS_NOT_USED;
        return formatUnusedSymbolHighlightInfo(aClass, pattern, options);
      }
    }
    else if (aClass.getParent() instanceof PsiDeclarationStatement) { // local class
      int count = myRefCountHolder.getRefCount(aClass);
      if (count == 0) {
        return formatUnusedSymbolHighlightInfo(aClass, LOCAL_CLASS_IS_NOT_USED, options);
      }
    }
    else if (aClass instanceof PsiTypeParameter) {
      int count = myRefCountHolder.getRefCount(aClass);
      if (count == 0) {
        return formatUnusedSymbolHighlightInfo(aClass, TYPE_PARAMETER_IS_NOT_USED, options);
      }
    }
    return null;
  }

  private static HighlightInfo formatUnusedSymbolHighlightInfo(PsiClass aClass, String pattern, final List<IntentionAction> options) {
    String symbolName = aClass.getName();
    String message = MessageFormat.format(pattern, new Object[]{symbolName});
    PsiIdentifier identifier = aClass.getNameIdentifier();
    HighlightInfo highlightInfo = createUnusedSymbolInfo(identifier, message);
    QuickFixAction.registerQuickFixAction(highlightInfo, new SafeDeleteFix(aClass), options);
    return highlightInfo;
  }

  private HighlightInfo processImport(PsiImportStatementBase importStatement) {
    if (!mySettings.getInspectionProfile().isToolEnabled(HighlightDisplayKey.UNUSED_IMPORT)) return null;

    if (PsiUtil.hasErrorElementChild(importStatement)) return null;

    boolean isRedundant = myRefCountHolder.isRedundant(importStatement);
    if (!isRedundant && !(importStatement instanceof PsiImportStaticStatement)) {
      //check import from same package
      String packageName = ((PsiJavaFile)importStatement.getContainingFile()).getPackageName();
      PsiElement resolved = importStatement.getImportReference().resolve();
      if (resolved instanceof PsiPackage) {
        isRedundant = packageName.equals(((PsiPackage)resolved).getQualifiedName());
      }
      else if (resolved instanceof PsiClass) {
        String qName = ((PsiClass)resolved).getQualifiedName();
        if (qName != null) {
          String name = ((PsiClass)resolved).getName();
          isRedundant = qName.equals(packageName + '.' + name);
        }
      }
    }

    if (isRedundant) {
      return registerRedundantImport(importStatement);
    }

    int entryIndex = myStyleManager.findEntryIndex(importStatement);
    if (entryIndex < myCurentEntryIndex) {
      myHasMissortedImports = true;
    }
    myCurentEntryIndex = entryIndex;

    return null;
  }

  private HighlightInfo registerRedundantImport(PsiImportStatementBase importStatement) {
    HighlightInfo info = HighlightInfo.createHighlightInfo(HighlightInfoType.UNUSED_IMPORT, importStatement, "Unused import statement");
    List<IntentionAction> options = new ArrayList<IntentionAction>();
    options.add(new SwitchOffToolAction(HighlightDisplayKey.UNUSED_IMPORT));
    QuickFixAction.registerQuickFixAction(info, new OptimizeImportsFix(), options);
    QuickFixAction.registerQuickFixAction(info, new EnableOptimizeImportsOnTheFlyFix(), options);
    myHasRedundantImports = true;
    return info;
  }

  private boolean timeToOptimizeImports() {
    if (!CodeInsightSettings.getInstance().OPTIMIZE_IMPORTS_ON_THE_FLY) return false;

    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(myProject);
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myDocument);
    if (file == null || !codeAnalyzer.isHighlightingAvailable(file)) return false;

    if (!codeAnalyzer.isErrorAnalyzingFinished(file)) return false;
    HighlightInfo[] errors = DaemonCodeAnalyzerImpl.getHighlights(myDocument, HighlightSeverity.ERROR, myProject);
    if (errors.length != 0) return false;

    if (fileHasUnchangedStatus()) return false;
    return true;
  }

  private boolean fileHasUnchangedStatus() {
    VirtualFile virtualFile = myFile.getVirtualFile();
    AbstractVcs activeVcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(virtualFile);
    if (activeVcs == null) return false;
    FileStatus status = FileStatusManager.getInstance(myProject).getStatus(virtualFile);

    return status == FileStatus.NOT_CHANGED;
  }

  private static boolean isSerializable(PsiClass aClass) {
    PsiManager manager = aClass.getManager();
    PsiClass serializableClass = manager.findClass("java.io.Serializable", aClass.getResolveScope());
    if (serializableClass == null) return false;
    return aClass.isInheritor(serializableClass, true);
  }

  private static boolean isSerializationImplicitlyUsedField(PsiField field) {
    final String name = field.getName();
    if (!name.equals("serialVersionUID") && !name.equals("serialPersistentFields")) return false;
    if (!field.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = field.getContainingClass();
    return aClass == null || isSerializable(aClass);
  }

  private static boolean isWriteObjectMethod(PsiMethod method) {
    if (!method.getName().equals("writeObject")) return false;
    PsiType returnType = method.getReturnType();
    if (!TypeConversionUtil.isVoidType(returnType)) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;
    if (!parameters[0].getType().equalsToText("java.io.ObjectOutputStream")) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isWriteReplaceMethod(PsiMethod method) {
    if (!method.getName().equals("writeReplace")) return false;
    PsiType returnType = method.getReturnType();
    if (!returnType.equalsToText("java.lang.Object")) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isReadResolveMethod(PsiMethod method) {
    if (!method.getName().equals("readResolve")) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiType returnType = method.getReturnType();
    if (!returnType.equalsToText("java.lang.Object")) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isReadObjectMethod(PsiMethod method) {
    if (!method.getName().equals("readObject")) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 1) return false;

    if (!parameters[0].getType().equalsToText("java.io.ObjectInputStream")) return false;

    PsiType returnType = method.getReturnType();
    if (!TypeConversionUtil.isVoidType(returnType)) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isReadObjectNoDataMethod(PsiMethod method) {
    if (!method.getName().equals("readObjectNoData")) return false;
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != 0) return false;
    PsiType returnType = method.getReturnType();
    if (!TypeConversionUtil.isVoidType(returnType)) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass != null && !isSerializable(aClass)) return false;
    return true;
  }

  private static boolean isMainMethod(PsiMethod method) {
    if (!PsiType.VOID.equals(method.getReturnType())) return false;
    PsiElementFactory factory = method.getManager().getElementFactory();
    try {
      PsiMethod appMain = factory.createMethodFromText("void main(String[] args);", null);
      if (MethodSignatureUtil.areSignaturesEqual(method, appMain)) return true;
      PsiMethod appPremain = factory.createMethodFromText("void premain(String[] args, java.lang.instrument.Instrumentation i);", null);
      if (MethodSignatureUtil.areSignaturesEqual(method, appPremain)) return true;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return false;
  }

  private static boolean isIntentionalPrivateConstructor(PsiMethod method) {
    if (!method.isConstructor()) return false;
    if (!method.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    if (method.getParameterList().getParameters().length > 0) return false;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return false;
    return aClass.getConstructors().length == 1;
  }
}
