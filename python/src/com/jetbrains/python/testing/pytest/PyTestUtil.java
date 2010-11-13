package com.jetbrains.python.testing.pytest;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;

import java.util.HashSet;
import java.util.List;

/**
 * User: catherine
 */
public class PyTestUtil {
  private static final HashSet<String> PYTHON_TEST_QUALIFIED_CLASSES = Sets.newHashSet("unittest.TestCase", "unittest.case.TestCase");
  
  public static List<PyStatement> getPyTestCasesFromFile(PyFile file) {
    List<PyStatement> result = Lists.newArrayList();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (isPyTestClass(cls)) {
        result.add(cls);
      }
    }
    for (PyFunction cls : file.getTopLevelFunctions()) {
      if (isPyTestFunction(cls)) {
        result.add(cls);
      }
    }
    return result;
  }

  public static boolean isPyTestFunction(PyFunction pyFunction) {
    String name = pyFunction.getName();
    if (name != null && name.startsWith("test")) {
      return true;
    }
    return false;
  }

  public static boolean isPyTestClass(PyClass pyClass) {
    for (PyClass ancestor : pyClass.iterateAncestors()) {
      if (ancestor == null) continue;

      String qName = ancestor.getQualifiedName();
      if (PYTHON_TEST_QUALIFIED_CLASSES.contains(qName)) {
        return true;
      }
      
      String name = pyClass.getName().toLowerCase();
      if (name != null && name.startsWith("test")) {
        List<PyStatement> result = Lists.newArrayList();
        for (PyFunction cls : pyClass.getMethods()) {
          if (isPyTestFunction(cls)) {
            result.add(cls);
          }
        }
        if (!result.isEmpty()) return true;
      }
    }
    return false;
  }

}
