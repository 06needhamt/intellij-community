package com.jetbrains.python;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;

import java.util.*;

/**
 * @author yole
 */
public class PyGotoSymbolContributor implements ChooseByNameContributor {
  public String[] getNames(final Project project, final boolean includeNonProjectItems) {
    Set<String> symbols = new HashSet<String>();
    symbols.addAll(PyClassNameIndex.allKeys(project));
    symbols.addAll(StubIndex.getInstance().getAllKeys(PyFunctionNameIndex.KEY, project));
    return ArrayUtil.toStringArray(symbols);
  }

  public NavigationItem[] getItemsByName(final String name, final String pattern, final Project project, final boolean includeNonProjectItems) {
    final GlobalSearchScope scope = includeNonProjectItems
                                    ? ProjectScope.getAllScope(project) 
                                    : GlobalSearchScope.projectScope(project);

    List<NavigationItem> symbols = new ArrayList<NavigationItem>();
    symbols.addAll(PyClassNameIndex.find(name, project, scope));
    symbols.addAll(PyFunctionNameIndex.find(name, project, scope));

    return symbols.toArray(new NavigationItem[symbols.size()]);
  }

}
