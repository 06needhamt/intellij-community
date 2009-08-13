package com.jetbrains.python.hierarchy;

import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Jul 31, 2009
 * Time: 6:00:21 PM
 */
public class PyTypeHierachyProvider implements HierarchyProvider {
  @Nullable
  public PsiElement getTarget(DataContext dataContext) {
    PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null) {
      final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
      final PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
      if (editor != null && file != null) {
        element = file.findElementAt(editor.getCaretModel().getOffset());
      }
    }
    while (element != null && !(element instanceof PyClass)) {
      element = element.getParent();
    }
    return element;
  }

  @NotNull
  public HierarchyBrowser createHierarchyBrowser(PsiElement target) {
    return new PyTypeHierarchyBrowser((PyClass)target);
  }

  public void browserActivated(HierarchyBrowser hierarchyBrowser) {
    final PyTypeHierarchyBrowser browser = (PyTypeHierarchyBrowser)hierarchyBrowser;
    final String typeName =
      browser.isInterface() ? TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE : TypeHierarchyBrowserBase.TYPE_HIERARCHY_TYPE;
    browser.changeView(typeName);
  }
}
