package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.FoldingModelEx;

public class FoldingActionGroup extends DefaultActionGroup {
  public FoldingActionGroup() {
    super();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();

    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null){
      presentation.setVisible(false);
      return;
    }

    FoldingModelEx foldingModel = (FoldingModelEx)editor.getFoldingModel();
    presentation.setVisible(foldingModel.isFoldingEnabled());
  }
}