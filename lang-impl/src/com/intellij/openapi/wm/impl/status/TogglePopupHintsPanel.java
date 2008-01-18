package com.intellij.openapi.wm.impl.status;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.HectorComponent;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightLevelUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorOptionsConfigurable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.UIBundle;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TogglePopupHintsPanel extends JPanel implements StatusBarPatch{
  private static final Icon INSPECTIONS_ICON = IconLoader.getIcon("/objectBrowser/showGlobalInspections.png");
  private static final Icon INSPECTIONS_OFF_ICON = IconLoader.getIcon("/general/inspectionsOff.png");
  private static final Icon EMPTY_ICON = new EmptyIcon(INSPECTIONS_ICON.getIconWidth(), INSPECTIONS_ICON.getIconHeight());

  private ProjectManagerListener myProjectManagerListener = new MyProjectManagerListener();
  private MyFileEditorManagerListener myFileEditorManagerListener = new MyFileEditorManagerListener();

  private JLabel myHectorLabel = new JLabel(EMPTY_ICON);
  private JLabel myInspectionProfileLabel = new JLabel();
  private int myMinLength;

  public TogglePopupHintsPanel(final StatusBar statusBar) {
    super(new GridBagLayout());
    myHectorLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        Point point = new Point(0, 0);
        final PsiFile file = getCurrentFile();
        if (file != null) {
          if (!DaemonCodeAnalyzer.getInstance(file.getProject()).isHighlightingAvailable(file)) return;
          final HectorComponent component = new HectorComponent(file);
          final Dimension dimension = component.getPreferredSize();
          point = new Point(point.x - dimension.width, point.y - dimension.height);
          component.showComponent(new RelativePoint(TogglePopupHintsPanel.this, point));
        }
      }
    });
    myHectorLabel.setIconTextGap(0);
    myInspectionProfileLabel.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        final PsiFile file = getCurrentFile();
        if (file != null) {
          if (!DaemonCodeAnalyzer.getInstance(file.getProject()).isHighlightingAvailable(file)) return;
          final Project project = file.getProject();
          final ErrorOptionsConfigurable profileConfigurable = ErrorOptionsConfigurable.getInstance(project);
          ShowSettingsUtil.getInstance().editConfigurable(project, profileConfigurable, new Runnable() {
            public void run() {
              profileConfigurable.selectScopeFor(file);
            }
          });
        }
      }
    });
    ProjectManager.getInstance().addProjectManagerListener(myProjectManagerListener);
    //not to miss already opened projects in first frame if any
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      FileEditorManager.getInstance(project).addFileEditorManagerListener(myFileEditorManagerListener);
    }
    add(myHectorLabel, new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    add(myInspectionProfileLabel, new GridBagConstraints(1, 0, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 3, 0, 3), 0, 0));

    StatusBarTooltipper.install(this, myHectorLabel, statusBar);
    StatusBarTooltipper.install(this, myInspectionProfileLabel, statusBar);
  }

  public JComponent getComponent() {
    return this;
  }

  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    updateStatus();
    String text = componentSelected == null ? null : componentSelected.getToolTipText();
    setCursor(Cursor.getPredefinedCursor(text == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
    return text;
  }

  public void clear() {

  }

  public void updateStatus() {
    updateStatus(getCurrentFile());
  }

  void updateStatus(PsiFile file) {
    if (isStateChangeable(file)) {
      if (HighlightLevelUtil.shouldInspect(file)) {
        myHectorLabel.setIcon(INSPECTIONS_ICON);
        String text = InspectionProjectProfileManager.getInstance(file.getProject()).getProfileName(file);
        if (text != null){
          final Font font = getFont();
          if (font != null) {
            final int width = getFontMetrics(font).stringWidth(text);
            if (width > 60 && text.length() > 30){
              text = text.substring(0, 27) + "...";
            }
            if (myMinLength < width){
              myMinLength = width;
              Dimension dim = getMinimumSize();
              dim = new Dimension(myMinLength, dim.height);
              myInspectionProfileLabel.setPreferredSize(dim);             
            }
          }
        }
        myInspectionProfileLabel.setText(text);
      }
      else {
        myHectorLabel.setIcon(INSPECTIONS_OFF_ICON);
        myInspectionProfileLabel.setText("");
      }
      myHectorLabel.setToolTipText(UIBundle.message("popup.hints.panel.click.to.configure.highlighting.tooltip.text"));
      myInspectionProfileLabel.setToolTipText(UIBundle.message("popup.hints.panel.click.to.configure.profile.text"));
    }
    else {
      myHectorLabel.setIcon(EMPTY_ICON);
      myHectorLabel.setToolTipText(null);
      myInspectionProfileLabel.setText("");
      myInspectionProfileLabel.setToolTipText(null);
    }
  }

  private static boolean isStateChangeable(PsiFile file) {
    return file != null && DaemonCodeAnalyzer.getInstance(file.getProject()).isHighlightingAvailable(file);
  }

  @Nullable
  private PsiFile getCurrentFile() {
    final Project project = getCurrentProject();
    if (project == null) {
      return null;
    }

    final VirtualFile virtualFile = ((FileEditorManagerEx)FileEditorManager.getInstance(project)).getCurrentFile();
    if (virtualFile != null){
      return PsiManager.getInstance(project).findFile(virtualFile);
    }
    return null;
  }

  @Nullable
  private Project getCurrentProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(this));
  }

  public Point getToolTipLocation(MouseEvent event) {
    return new Point(0, -20);
  }

  public void removeNotify() {
    super.removeNotify();
    ProjectManager.getInstance().removeProjectManagerListener(myProjectManagerListener);
  }

  private final class MyFileEditorManagerListener extends FileEditorManagerAdapter {
    public void selectionChanged(FileEditorManagerEvent e) {
      final Project project = getCurrentProject();
      if (project != null) {
        final VirtualFile vFile = e.getNewFile();
        if (vFile != null) {
          updateStatus(PsiManager.getInstance(project).findFile(vFile));
        }
        else {
          updateStatus(null);
        }
      }
    }
  }

  private final class MyProjectManagerListener implements ProjectManagerListener {

    public void projectOpened(Project project) {
      FileEditorManager.getInstance(project).addFileEditorManagerListener(myFileEditorManagerListener);
    }

    public boolean canCloseProject(Project project) {
      return true;
    }

    public void projectClosed(Project project) {
      FileEditorManager.getInstance(project).removeFileEditorManagerListener(myFileEditorManagerListener);
    }

    public void projectClosing(Project project) {
    }
  }
}
