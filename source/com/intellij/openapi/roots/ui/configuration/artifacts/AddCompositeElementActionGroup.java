package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.elements.PackagingElementFactory;

import java.util.List;

/**
 * @author nik
 */
public class AddCompositeElementActionGroup extends AnAction {
  private final ArtifactEditor myArtifactEditor;
  private final CompositePackagingElementType<?> myElementType;

  public AddCompositeElementActionGroup(ArtifactEditor artifactEditor, CompositePackagingElementType elementType) {
    super(ProjectBundle.message("artifacts.create.action", elementType.getPresentableName()));
    myArtifactEditor = artifactEditor;
    myElementType = elementType;
    getTemplatePresentation().setIcon(elementType.getCreateElementIcon());
  }

  public void actionPerformed(AnActionEvent e) {
    myArtifactEditor.addNewPackagingElement(myElementType);
  }

  public static void addCompositeCreateActions(List<AnAction> actions, final ArtifactEditor artifactEditor) {
    for (CompositePackagingElementType packagingElementType : PackagingElementFactory.getInstance().getCompositeElementTypes()) {
      actions.add(new AddCompositeElementActionGroup(artifactEditor, packagingElementType));
    }
  }
}