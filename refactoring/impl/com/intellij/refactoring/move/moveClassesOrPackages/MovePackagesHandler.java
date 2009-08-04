package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.intellij.refactoring.util.RadioUpDownListener;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.containers.HashSet;

import javax.swing.*;
import java.awt.*;

public class MovePackagesHandler extends MoveClassesOrPackagesHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MovePackagesHandler");

  public boolean canMove(final PsiElement[] elements, final PsiElement targetContainer) {
    for(PsiElement element: elements) {
      if (!isPackageOrDirectory(element)) return false;
    }
    return super.canMove(elements, targetContainer);
  }

  public boolean isValidTarget(final PsiElement psiElement) {
    return isPackageOrDirectory(psiElement);
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    if (targetContainer == null && canMoveOrRearrangePackages(elements) ) {
      PsiDirectory[] directories = new PsiDirectory[elements.length];
      System.arraycopy(elements, 0, directories, 0, directories.length);
      SelectMoveOrRearrangePackageDialog dialog = new SelectMoveOrRearrangePackageDialog(project, directories);
      dialog.show();
      if (!dialog.isOK()) return;

      if (dialog.isPackageRearrageSelected()) {
        MoveClassesOrPackagesImpl.doRearrangePackage(project, directories);
        return;
      }

      if (dialog.isMoveDirectory()) {
        MoveFilesOrDirectoriesUtil.doMove(project, elements, targetContainer, null);
        return;
      }
    }
    super.doMove(project, elements, targetContainer, callback);
  }

  private static boolean canMoveOrRearrangePackages(PsiElement[] elements) {
    if (elements.length == 0) return false;
    final Project project = elements[0].getProject();
    if (ProjectRootManager.getInstance(project).getContentSourceRoots().length == 1) {
      return false;
    }
    for (PsiElement element : elements) {
      if (!(element instanceof PsiDirectory)) return false;
      final PsiDirectory directory = ((PsiDirectory)element);
      if (RefactoringUtil.isSourceRoot(directory)) {
        return false;
      }
      final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (aPackage == null) return false;
      if ("".equals(aPackage.getQualifiedName())) return false;
      final VirtualFile sourceRootForFile = ProjectRootManager.getInstance(element.getProject()).getFileIndex()
        .getSourceRootForFile(directory.getVirtualFile());
      if (sourceRootForFile == null) return false;
    }
    return true;
  }

  private static class SelectMoveOrRearrangePackageDialog extends DialogWrapper {
    private JRadioButton myRbMovePackage;
    private JRadioButton myRbRearrangePackage;
    private JRadioButton myRbMoveDirectory;

    private final PsiDirectory[] myDirectories;

    public SelectMoveOrRearrangePackageDialog(Project project, PsiDirectory[] directories) {
      super(project, true);
      myDirectories = directories;
      setTitle(RefactoringBundle.message("select.refactoring.title"));
      init();
    }

    protected JComponent createNorthPanel() {
      return new JLabel(RefactoringBundle.message("what.would.you.like.to.do"));
    }

    public JComponent getPreferredFocusedComponent() {
      return myRbMovePackage;
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.refactoring.move.MoveHandler.SelectRefactoringDialog";
    }


    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());


      final HashSet<String> packages = new HashSet<String>();
      for (PsiDirectory directory : myDirectories) {
        packages.add(JavaDirectoryService.getInstance().getPackage(directory).getQualifiedName());
      }
      final String moveDescription;
      LOG.assertTrue(myDirectories.length > 0);
      LOG.assertTrue(packages.size() > 0);
      if (packages.size() > 1) {
        moveDescription = RefactoringBundle.message("move.packages.to.another.package", packages.size());
      }
      else {
        final String qName = packages.iterator().next();
        moveDescription = RefactoringBundle.message("move.package.to.another.package", qName);
      }

      myRbMovePackage = new JRadioButton();
      myRbMovePackage.setText(moveDescription);
      myRbMovePackage.setSelected(true);

      final String rearrangeDescription;
      if (myDirectories.length > 1) {
        rearrangeDescription = RefactoringBundle.message("move.directories.to.another.source.root", myDirectories.length);
      }
      else {
        rearrangeDescription = RefactoringBundle.message("move.directory.to.another.source.root", myDirectories[0].getVirtualFile().getPresentableUrl());
      }
      myRbRearrangePackage = new JRadioButton();
      myRbRearrangePackage.setText(rearrangeDescription);

      final String moveDirectoryDescription;
      if (myDirectories.length > 1) {
        moveDirectoryDescription = "Move " + myDirectories.length + " directories to another directory";
      }
      else {
        moveDirectoryDescription = "Move directory " + myDirectories[0].getVirtualFile().getPresentableUrl() + " to another directory";
      }
      myRbMoveDirectory = new JRadioButton();
      myRbMoveDirectory.setMnemonic('d');
      myRbMoveDirectory.setText(moveDirectoryDescription);

      ButtonGroup gr = new ButtonGroup();
      gr.add(myRbMovePackage);
      gr.add(myRbRearrangePackage);
      gr.add(myRbMoveDirectory);

      new RadioUpDownListener(myRbMovePackage, myRbRearrangePackage, myRbMoveDirectory);

      Box box = Box.createVerticalBox();
      box.add(Box.createVerticalStrut(5));
      box.add(myRbMovePackage);
      box.add(myRbRearrangePackage);
      box.add(myRbMoveDirectory);
      panel.add(box, BorderLayout.CENTER);
      return panel;
    }

    public boolean isPackageRearrageSelected() {
      return myRbRearrangePackage.isSelected();
    }

    public boolean isMoveDirectory() {
      return myRbMoveDirectory.isSelected();
    }
  }
}
