/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.editor;

import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashSet;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author ven
 */
public class GroovyImportOptimizer implements ImportOptimizer {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.editor.GroovyImportOptimizer");
  private static final Comparator<GrImportStatement> IMPORT_STATEMENT_COMPARATOR = new Comparator<GrImportStatement>() {
    public int compare(GrImportStatement statement1, GrImportStatement statement2) {
      final GrCodeReferenceElement ref1 = statement1.getImportReference();
      final GrCodeReferenceElement ref2 = statement2.getImportReference();
      String name1 = ref1 != null ? PsiUtil.getQualifiedReferenceText(ref1) : null;
      String name2 = ref2 != null ? PsiUtil.getQualifiedReferenceText(ref2) : null;
      if (name1 == null) return name2 == null ? 0 : -1;
      if (name2 == null) return 1;
      return name1.compareTo(name2);
    }
  };

  @NotNull
  public Runnable processFile(PsiFile file) {
    return new MyProcessor(file);
  }

  public boolean supports(PsiFile file) {
    return true;
  }

  private class MyProcessor implements Runnable {
    private final GroovyFile myFile;

    public MyProcessor(PsiFile file) {
      myFile = (GroovyFile) file;
    }

    public void run() {
      if (!ProjectRootManager.getInstance(myFile.getProject()).getFileIndex().isInSource(myFile.getVirtualFile())) {
        return;
      }

      final Set<String> importedClasses = new LinkedHashSet<String>();
      final Set<String> staticallyImportedMembers = new LinkedHashSet<String>();
      final Set<GrImportStatement> usedImports = new HashSet<GrImportStatement>();
      myFile.accept(new GroovyRecursiveElementVisitor() {
        public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
          visitRefElement(refElement);
          super.visitCodeReferenceElement(refElement);
        }

        public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
          visitRefElement(referenceExpression);
          super.visitReferenceExpression(referenceExpression);
        }

        private void visitRefElement(GrReferenceElement refElement) {
          final GroovyResolveResult[] resolveResults = refElement.multiResolve(false);
          for (GroovyResolveResult resolveResult : resolveResults) {
            final GroovyPsiElement context = resolveResult.getCurrentFileResolveContext();
            final PsiElement element = resolveResult.getElement();
            if (element == null) return;

            if (context instanceof GrImportStatement) {
              final GrImportStatement importStatement = (GrImportStatement) context;

              usedImports.add(importStatement);
              if (!importStatement.isAliasedImport()) {
                String importedName = null;
                if (importStatement.isOnDemand()) {

                  if (importStatement.isStatic()) {
                    if (element instanceof PsiMember) {
                      final PsiMember member = (PsiMember) element;
                      final PsiClass clazz = member.getContainingClass();
                      if (clazz != null) {
                        final String classQName = clazz.getQualifiedName();
                        if (classQName != null) {
                          final String name = member.getName();
                          if (name != null) {
                            importedName = classQName + "." + name;
                          }
                        }
                      }
                    }
                  } else {
                    if (element instanceof PsiClass) {
                      importedName = ((PsiClass) element).getQualifiedName();
                    } else if (element instanceof PsiMethod && ((PsiMethod) element).isConstructor()) {
                      importedName = ((PsiMethod) element).getContainingClass().getQualifiedName();
                    }
                  }
                } else {
                  final GrCodeReferenceElement importReference = importStatement.getImportReference();
                  if (importReference != null) {
                    importedName = PsiUtil.getQualifiedReferenceText(importReference);
                  }
                }

                if (importedName == null) return;

                if (importStatement.isStatic()) {
                  staticallyImportedMembers.add(importedName);
                } else {
                  importedClasses.add(importedName);
                }
              }
            } else if (context == null && !(refElement.getParent() instanceof GrImportStatement) && element instanceof PsiClass) {
              importedClasses.add(((PsiClass)element).getQualifiedName());
            }
          }
        }
      });

      for (final PsiClass psiClass : myFile.getClasses()) {
        importedClasses.remove(psiClass.getQualifiedName());
      }

      // remove ALL imports from file
      final GrImportStatement[] oldImports = myFile.getImportStatements();

      // Getting aliased imports
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myFile.getProject());
      ArrayList<GrImportStatement> aliased = new ArrayList<GrImportStatement>();
      for (GrImportStatement oldImport : oldImports) {
        if (oldImport.isAliasedImport() && usedImports.contains(oldImport)) {
          aliased.add(factory.createImportStatementFromText(oldImport.getText()));
        }
      }
      for (GrImportStatement importStatement : oldImports) {
        try {
          myFile.removeImport(importStatement);
        } catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      // Add new import statements
      GrImportStatement[] newImports = prepare(importedClasses, staticallyImportedMembers);
      try {
        for (GrImportStatement aliasedImport : aliased) {
          myFile.addImport(aliasedImport);
        }
        for (GrImportStatement newImport : newImports) {
          myFile.addImport(newImport);
        }
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }

    }

    private GrImportStatement[] prepare(Set<String> importedClasses, Set<String> staticallyImportedMembers) {
      final Project project = myFile.getProject();
      final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      TObjectIntHashMap<String> packageCountMap = new TObjectIntHashMap<String>();
      TObjectIntHashMap<String> classCountMap = new TObjectIntHashMap<String>();

      for (String importedClass : importedClasses) {
        final String packageName = getParentName(importedClass);

        if (isPackageImplicitlyImported(packageName)) continue;
        if (!packageCountMap.containsKey(packageName)) packageCountMap.put(packageName, 0);
        packageCountMap.increment(packageName);
      }

      for (String importedMember : staticallyImportedMembers) {
        final String className = getParentName(importedMember);
        if (!classCountMap.containsKey(className)) classCountMap.put(className, 0);
        classCountMap.increment(className);
      }

      final Set<String> onDemandImportedSimpleClassNames = new HashSet<String>();
      final List<GrImportStatement> result = new ArrayList<GrImportStatement>();
      packageCountMap.forEachEntry(new TObjectIntProcedure<String>() {
        public boolean execute(String s, int i) {
          if (i >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            result.add(factory.createImportStatementFromText(s, false, true, null));
            final PsiPackage aPackage = JavaPsiFacade.getInstance(myFile.getProject()).findPackage(s);
            if (aPackage != null) {
              for (PsiClass clazz : aPackage.getClasses(myFile.getResolveScope())) {
                onDemandImportedSimpleClassNames.add(clazz.getName());
              }
            }
          }
          return true;
        }
      });

      classCountMap.forEachEntry(new TObjectIntProcedure<String>() {
        public boolean execute(String s, int i) {
          if (i >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) {
            result.add(factory.createImportStatementFromText(s, true, true, null));
          }
          return true;
        }
      });

      List<GrImportStatement> explicated = CollectionFactory.arrayList();
      for (String importedClass : importedClasses) {
        final String parentName = getParentName(importedClass);
        if (packageCountMap.get(parentName) >= settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND) continue;
        if (isClassImplicitlyImported(importedClass) && !onDemandImportedSimpleClassNames.contains(StringUtil.getShortName(importedClass)))
          continue;

        explicated.add(factory.createImportStatementFromText(importedClass, false, false, null));
      }

      for (String importedMember : staticallyImportedMembers) {
        final String parentName = getParentName(importedMember);
        if (classCountMap.get(parentName) >= settings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND) continue;
        result.add(factory.createImportStatementFromText(importedMember, true, false, null));
      }

      Collections.sort(result, IMPORT_STATEMENT_COMPARATOR);
      Collections.sort(explicated, IMPORT_STATEMENT_COMPARATOR);

      explicated.addAll(result);
      return explicated.toArray(new GrImportStatement[explicated.size()]);
    }

    private boolean isClassImplicitlyImported(String importedClass) {
      for (String implicitlyImportedClass : GroovyFileBase.IMPLICITLY_IMPORTED_CLASSES) {
        if (importedClass.equals(implicitlyImportedClass)) return true;
      }

      return isPackageImplicitlyImported(getParentName(importedClass));
    }

    private boolean isPackageImplicitlyImported(String packageName) {
      for (String implicitlyImportedPackage : GroovyFileBase.IMPLICITLY_IMPORTED_PACKAGES) {
        if (packageName.equals(implicitlyImportedPackage)) return true;
      }
      return false;
    }

    private String getParentName(String qname) {
      return StringUtil.getPackageName(qname);
    }

  }
}
