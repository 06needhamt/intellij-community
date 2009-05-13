package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.artifacts.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.impl.elements.ArtifactPackagingElement;

import java.util.Collection;

/**
 * @author nik
 */
public class ExtractArtifactAction extends AnAction {
  private ArtifactsEditor myEditor;

  public ExtractArtifactAction(ArtifactsEditor editor) {
    super(ProjectBundle.message("action.name.extract.artifact"));
    myEditor = editor;
  }

  @Override
  public void update(AnActionEvent e) {
    final LayoutTreeSelection selection = myEditor.getPackagingElementsTree().getSelection();
    e.getPresentation().setEnabled(selection.getCommonParentElement() != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final LayoutTreeComponent treeComponent = myEditor.getPackagingElementsTree();
    final LayoutTreeSelection selection = treeComponent.getSelection();
    final CompositePackagingElement<?> parent = selection.getCommonParentElement();
    if (parent == null) return;

    if (!treeComponent.checkCanRemove(selection.getNodes())) {
      return;
    }

    final Collection<? extends PackagingElement> selectedElements = selection.getElements();
    final String name = Messages.showInputDialog(myEditor.getMainComponent(), ProjectBundle.message("label.text.specify.artifact.name"),
                                                 ProjectBundle.message("dialog.title.extract.artifact"), null);
    if (name != null) {
      treeComponent.ensureRootIsWritable();
      //todo[nik] select type?
      final ModifiableArtifact artifact = myEditor.getContext().getModifiableArtifactModel().addArtifact(name, PlainArtifactType.getInstance());
      for (PackagingElement<?> element : selectedElements) {
        artifact.getRootElement().addChild(ArtifactUtil.copyWithChildren(element));
      }
      for (PackagingElement element : selectedElements) {
        parent.removeChild(element);
      }
      parent.addChild(new ArtifactPackagingElement(name));
      treeComponent.rebuildTree();
    }
  }
}
