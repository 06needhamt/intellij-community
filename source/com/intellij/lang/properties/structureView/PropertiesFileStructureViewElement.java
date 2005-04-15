package com.intellij.lang.properties.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 10, 2005
 * Time: 3:26:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesFileStructureViewElement extends PsiTreeElementBase<PropertiesFile> {

  protected PropertiesFileStructureViewElement(PropertiesFile propertiesFile) {
    super(propertiesFile);
  }

  public StructureViewTreeElement[] getChildrenBase() {
    Property[] properties = getElement().getProperties();

    StructureViewTreeElement[] elements = new StructureViewTreeElement[properties.length];
    for (int i = 0; i < properties.length; i++) {
      Property property = properties[i];
      elements[i] = new PropertiesStructureViewElement(property);
    }
    return elements;
  }

  public String getPresentableText() {
    return getElement().getName();
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return PropertiesFileStructureViewElement.this.getPresentableText();
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return getElement().getIcon(Iconable.ICON_FLAG_OPEN);
      }
    };
  }
}
