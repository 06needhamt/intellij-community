package com.intellij.ide.util;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileType;

/**
 * User: anna
 * Date: Jan 25, 2005
 */
public class TreeClassChooserFactoryImpl extends TreeClassChooserFactory {
  private Project myProject;

  public TreeClassChooserFactoryImpl(final Project project) {
    myProject = project;
  }

  public TreeClassChooser createWithInnerClassesScopeChooser(String title,
                                                             GlobalSearchScope scope,
                                                             final TreeClassChooser.ClassFilter classFilter,
                                                             PsiClass initialClass) {
    return TreeClassChooserDialog.withInnerClasses(title, myProject, scope, classFilter, initialClass);
  }

  public TreeClassChooser createNoInnerClassesScopeChooser(String title,
                                                           GlobalSearchScope scope,
                                                           TreeClassChooser.ClassFilter classFilter,
                                                           PsiClass initialClass) {
    return new TreeClassChooserDialog(title, myProject, scope, classFilter, initialClass);
  }

  public TreeClassChooser createProjectScopeChooser(String title, PsiClass initialClass) {
    return new TreeClassChooserDialog(title, myProject, initialClass);
  }

  public TreeClassChooser createProjectScopeChooser(String title) {
    return new TreeClassChooserDialog(title, myProject);
  }

  public TreeClassChooser createAllProjectScopeChooser(String title) {
    return new TreeClassChooserDialog(title, myProject, GlobalSearchScope.allScope(myProject), null, null);
  }

  public TreeClassChooser createInheritanceClassChooser(String title,
                                                        GlobalSearchScope scope,
                                                        PsiClass base,
                                                        boolean acceptsSelf,
                                                        boolean acceptInner,
                                                        Condition<PsiClass> addtionalCondition) {
    return new TreeClassChooserDialog(title, myProject, scope, new TreeClassChooserDialog.InheritanceClassFilterImpl(base, acceptsSelf, acceptInner, addtionalCondition), null);
  }

  public TreeFileChooser createFileChooser(String title,
                                           final PsiFile initialFile,
                                           FileType fileType,
                                           TreeFileChooser.PsiFileFilter filter) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter);
  }

  public void projectOpened() {
  }

  public void projectClosed() {

  }

  public String getComponentName() {
    return "com.intellij.ide.util.TreeClassFactoryImpl";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
