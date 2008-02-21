package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class TestsUIUtil {
  public static final Color PASSED_COLOR = new Color(0, 128, 0);
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.testframework.TestsUIUtil");

  @NonNls private static final String ICONS_ROOT = "/runConfigurations/";

  @Nullable
  public static Object getData(final AbstractTestProxy testProxy, final String dataId, final TestFrameworkRunningModel model) {
    final Project project = model.getProperties().getProject();
    if (testProxy == null) return null;
    if (AbstractTestProxy.DATA_CONSTANT.equals(dataId)) return testProxy;
    if (DataConstants.NAVIGATABLE.equals(dataId)) return getOpenFileDescriptor(testProxy, model);
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      final Location location = testProxy.getLocation(project);
      if (location != null) {
        final PsiElement element = location.getPsiElement();
        return element.isValid() ? element : null;
      }
      else {
        return null;
      }
    }
    if (Location.LOCATION.equals(dataId)) return testProxy.getLocation(project);
    if (DataConstantsEx.RUNTIME_CONFIGURATION.equals(dataId)) return model.getProperties().getConfiguration();
    return null;
  }

  public static Navigatable getOpenFileDescriptor(final AbstractTestProxy testProxy, final TestFrameworkRunningModel model) {
    return getOpenFileDescriptor(testProxy, model.getProperties().getProject(), TestConsoleProperties.OPEN_FAILURE_LINE.value(model.getProperties()));
  }
  
  private static Navigatable getOpenFileDescriptor(final AbstractTestProxy proxy, final Project project, final boolean openFailureLine) {
    if (proxy != null) {
      final Location location = proxy.getLocation(project);
      if (openFailureLine) {
        return proxy.getDescriptor(location);
      }
      final OpenFileDescriptor openFileDescriptor = location == null ? null : location.getOpenFileDescriptor();
      if (openFileDescriptor != null && openFileDescriptor.getFile().isValid()) {
        return openFileDescriptor;
      }
    }
    return null;
  }

  public static Icon loadIcon(@NonNls final String iconName) {
    final Application application = ApplicationManager.getApplication();
    if (application == null || application.isUnitTestMode()) return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR));
    @NonNls final String fullIconName = ICONS_ROOT + iconName +".png";
    final Icon icon = IconLoader.getIcon(fullIconName);
    LOG.assertTrue(icon != null, fullIconName);
    return icon;
  }
}
