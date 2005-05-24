
package com.intellij.ide.actions;

import com.intellij.ant.impl.ui.AntFileStructureList;
import com.intellij.ant.impl.dom.impl.AntDOMProject;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.structureView.impl.java.InheritedMembersFilter;
import com.intellij.ide.structureView.impl.java.KindSorter;
import com.intellij.ide.structureView.newStructureView.TreeActionsOwner;
import com.intellij.ide.structureView.newStructureView.TreeModelWrapper;
import com.intellij.ide.util.FileStructureDialog;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.structureView.PropertiesFileStructureViewModel;

/**
 *
 */
public class ViewStructureAction extends AnAction implements TreeActionsOwner{
  public ViewStructureAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    final FileEditor fileEditor = (FileEditor)dataContext.getData(DataConstants.FILE_EDITOR);
    if (editor == null) return;
    if (fileEditor == null) return;



    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) return;

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.file.structure");

    final StructureViewBuilder structureViewBuilder = AntFileStructureList.canShowFor(psiFile) ? null : fileEditor.getStructureViewBuilder();

    DialogWrapper dialog = createDialog(psiFile, structureViewBuilder, editor, project, (Navigatable)dataContext.getData(DataConstants.NAVIGATABLE));
    if (dialog != null) {
      dialog.setTitle(psiFile.getVirtualFile().getPresentableUrl());
      dialog.show();
    }
  }

   private DialogWrapper createDialog(PsiFile psiFile,
                                      StructureViewBuilder structureViewBuilder,
                                      final Editor editor,
                                      Project project,
                                      Navigatable navigatable) {
     if (AntDOMProject.getOtCreate(psiFile) != null) {
       return AntFileStructureList.createDialog(editor, psiFile);
     }
     StructureViewModel structureViewModel;
     if (structureViewBuilder instanceof TreeBasedStructureViewBuilder) {
       structureViewModel = ((TreeBasedStructureViewBuilder)structureViewBuilder).createStructureViewModel();
     }
     else if (psiFile instanceof PropertiesFile){
       structureViewModel = new PropertiesFileStructureViewModel((PropertiesFile)psiFile);
     }
     else {
       return null;
     }
     return createStructureViewBasedDialog(structureViewModel, editor, project, navigatable);
   }

  public FileStructureDialog createStructureViewBasedDialog(final StructureViewModel structureViewModel,
                                                            final Editor editor,
                                                            final Project project,
                                                            final Navigatable navigatable) {
    return new FileStructureDialog(new TreeModelWrapper(structureViewModel, this), editor, project, navigatable);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      presentation.setEnabled(false);
      return;
    }
    final VirtualFile virtualFile = psiFile.getVirtualFile();

    if (virtualFile == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(virtualFile.getFileType().getStructureViewBuilder(virtualFile, project) != null || AntFileStructureList.canShowFor(psiFile));
  }

  public void setActionActive(String name, boolean state) {

  }

  public boolean isActionActive(String name) {
    return InheritedMembersFilter.ID.equals(name)
           || KindSorter.ID.equals(name)
           || Sorter.ALPHA_SORTER_ID.equals(name);
  }
}
