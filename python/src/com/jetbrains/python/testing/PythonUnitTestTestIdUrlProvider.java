package com.jetbrains.python.testing;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testIntegration.TestLocationProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PythonUnitTestTestIdUrlProvider implements TestLocationProvider {
  @NonNls
  private static final String PROTOCOL_ID = "python_uttestid";

  @NotNull
  public List<Location> getLocation(@NotNull final String protocolId, @NotNull final String path,
                                    final Project project) {
    if (!PROTOCOL_ID.equals(protocolId)) {
      return Collections.emptyList();
    }

    final List<String> list = StringUtil.split(path, ".");
    if (list.isEmpty()) {
      return Collections.emptyList();
    }
    final int listSize = list.size();

    // parse path as [ns.]*fileName.className.methodName
    if (listSize < 3) {
      return Collections.emptyList();
    }

    final String className = list.get(listSize - 2);
    final String methodName = list.get(listSize - 1);

    String fileName = list.get(listSize - 3);
    if (fileName.indexOf("%") >= 0) {
      fileName = fileName.substring(0, fileName.lastIndexOf("%"));
    }

    final List<Location> locations = new ArrayList<Location>();
    for (PyClass cls : PyClassNameIndex.find(className, project, false)) {
      ProgressManager.checkCanceled();

      final PyFunction method = cls.findMethodByName(methodName, true);
      if (method == null) {
        continue;
      }

      final String clsFileName = FileUtil.getNameWithoutExtension(cls.getContainingFile().getName());
      if (!clsFileName.equalsIgnoreCase(fileName)) {
        continue;
      }

      locations.add(new PsiLocation<PyFunction>(project, method));
    }

    return locations;
  }
}
