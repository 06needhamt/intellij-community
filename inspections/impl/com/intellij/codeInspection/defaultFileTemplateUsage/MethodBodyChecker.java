package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInspection.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashMap;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author Alexey
 */
public class MethodBodyChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defaultFileTemplateUsage.MethodBodyChecker");

  // pair(return type canonical name, file template name) -> method template
  private static final Map<Pair<String,String>, PsiMethod> TEMPLATE_METHOD_BODIES = new THashMap<Pair<String, String>, PsiMethod>();
  private static Project DEFAULT_PROJECT;
  private static PsiClassType OBJECT_TYPE;

  private static PsiMethod getTemplateMethod(PsiType returnType, List<HierarchicalMethodSignature> superSignatures, final PsiClass aClass) {
    if (DEFAULT_PROJECT == null) {
      //DEFAULT_PROJECT = ProjectManager.getInstance().getDefaultProject();
      DEFAULT_PROJECT = ((FileDocumentManagerImpl)FileDocumentManager.getInstance()).getDummyProject();
      OBJECT_TYPE = PsiType.getJavaLangObject(PsiManager.getInstance(DEFAULT_PROJECT), GlobalSearchScope.allScope(DEFAULT_PROJECT));
    }
    if (!(returnType instanceof PsiPrimitiveType)) {
      returnType = OBJECT_TYPE;
    }
    try {
      final String fileTemplateName = getMethodFileTemplate(superSignatures, true).getName();
      Pair<String,String> key = Pair.create(returnType.getCanonicalText(), fileTemplateName);
      PsiMethod method = TEMPLATE_METHOD_BODIES.get(key);
      if (method == null) {
        method = PsiManager.getInstance(DEFAULT_PROJECT).getElementFactory().createMethod("x", returnType);
        setupMethodBody(superSignatures, method, aClass, true);
        TEMPLATE_METHOD_BODIES.put(key, method);
      }
      return method;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  static void checkMethodBody(final PsiMethod method,
                              final InspectionManager manager,
                              final Collection<ProblemDescriptor> problemDescriptors) {
    PsiType returnType = method.getReturnType();
    if (method.isConstructor() || returnType == null) return;
    PsiCodeBlock body = method.getBody();
    if (body == null) return;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null || aClass.isInterface()) return;
    List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    final PsiMethod superMethod = superSignatures.size() ==0 ? null : superSignatures.get(0).getMethod();
    final PsiMethod templateMethod = getTemplateMethod(returnType, superSignatures, aClass);
    if (PsiEquivalenceUtil.areElementsEquivalent(body, templateMethod.getBody(), new Comparator<PsiElement>(){
      public int compare(final PsiElement element1, final PsiElement element2) {
        // templates may be different on super method name                              
        if (element1 == superMethod && (element2 == templateMethod || element2 == null)) return 0;
        return 1;
      }
    }, true)) {
      Pair<? extends PsiElement, ? extends PsiElement> range = DefaultFileTemplateUsageInspection.getInteriorRange(body);
      final String description = InspectionsBundle.message("default.file.template.description");
      ProblemDescriptor problem = manager.createProblemDescriptor(range.first, range.second, description,
                                                                  ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                  createMethodBodyQuickFix(method));
      problemDescriptors.add(problem);
    }
  }

  private static FileTemplate getMethodFileTemplate(final List<HierarchicalMethodSignature> superSignatures,
                                                    final boolean useDefaultTemplate) {
    FileTemplateManager templateManager = FileTemplateManager.getInstance();
    FileTemplate template;
    if (superSignatures.size() == 0) {
      String name = FileTemplateManager.TEMPLATE_FROM_USAGE_METHOD_BODY;
      template = useDefaultTemplate ? templateManager.getDefaultTemplate(name) : templateManager.getCodeTemplate(name);
    }
    else {
      PsiMethod superMethod = superSignatures.get(0).getMethod();
      String name = superMethod.hasModifierProperty(PsiModifier.ABSTRACT) ?
                    FileTemplateManager.TEMPLATE_IMPLEMENTED_METHOD_BODY : FileTemplateManager.TEMPLATE_OVERRIDDEN_METHOD_BODY;
      template = useDefaultTemplate ? templateManager.getDefaultTemplate(name) : templateManager.getCodeTemplate(name);
    }
    return template;
  }

  private static final String NEW_METHOD_BODY_TEMPLATE_NAME = FileTemplateManager.getInstance().getDefaultTemplate(FileTemplateManager.TEMPLATE_FROM_USAGE_METHOD_BODY).getName();
  private static FileTemplate setupMethodBody(final List<HierarchicalMethodSignature> superSignatures,
                                              final PsiMethod templateMethod,
                                              final PsiClass aClass,
                                              final boolean useDefaultTemplate) throws IncorrectOperationException {
    FileTemplate template = getMethodFileTemplate(superSignatures, useDefaultTemplate);
    if (NEW_METHOD_BODY_TEMPLATE_NAME.equals(template.getName())) {
      CreateFromUsageUtils.setupMethodBody(templateMethod, aClass, template);
    }
    else {
      PsiMethod superMethod = superSignatures.get(0).getMethod();
      OverrideImplementUtil.setupMethodBody(templateMethod, superMethod, aClass,template);
    }
    return template;
  }

  private static LocalQuickFix[] createMethodBodyQuickFix(final PsiMethod method) {
    PsiType returnType = method.getReturnType();
    PsiClass aClass = method.getContainingClass();
    List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
    FileTemplate template;
    try {
      PsiMethod templateMethod = method.getManager().getElementFactory().createMethod("x", returnType);
      template = setupMethodBody(superSignatures, templateMethod, aClass, false);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
    final ReplaceWithFileTemplateFix replaceWithFileTemplateFix = new ReplaceWithFileTemplateFix() {
      public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiType returnType = method.getReturnType();
        if (method.isConstructor() || returnType == null) return;
        PsiCodeBlock body = method.getBody();
        if (body == null) return;
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) return;
        List<HierarchicalMethodSignature> superSignatures = method.getHierarchicalMethodSignature().getSuperSignatures();
        try {
          PsiMethod templateMethod = method.getManager().getElementFactory().createMethod("x", returnType);
          setupMethodBody(superSignatures, templateMethod, aClass, false);
          PsiElement newBody = method.getBody().replace(templateMethod.getBody());
          CodeStyleManager.getInstance(project).reformat(newBody);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    };
    LocalQuickFix editFileTemplateFix = DefaultFileTemplateUsageInspection.createEditFileTemplateFix(template, replaceWithFileTemplateFix);
    if (template.isDefault()) {
      return new LocalQuickFix[]{editFileTemplateFix};
    }
    return new LocalQuickFix[]{replaceWithFileTemplateFix, editFileTemplateFix};
  }
}
