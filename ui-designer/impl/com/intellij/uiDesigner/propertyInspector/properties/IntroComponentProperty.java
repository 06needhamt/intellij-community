package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.inspections.FormInspectionUtil;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ComponentEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.ComponentRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.Filter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Method;

/**
 * The value of the property is the string ID of the referenced component.
 * @author yole
 */
public class IntroComponentProperty extends IntrospectedProperty<String> {
  private ComponentRenderer myRenderer = new ComponentRenderer();
  private ComponentEditor myEditor;
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroComponentProperty_";

  public IntroComponentProperty(String name,
                                Method readMethod,
                                Method writeMethod,
                                Class propertyType,
                                Filter<RadComponent> filter,
                                final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
    myEditor = new ComponentEditor(propertyType, filter);
  }

  @NotNull public PropertyRenderer<String> getRenderer() {
    return myRenderer;
  }

  public PropertyEditor<String> getEditor() {
    return myEditor;
  }

  public void write(@NotNull String value, XmlWriter writer) {
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, value);
  }

  @Override public String getValue(final RadComponent component) {
    return (String) component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
  }

  @Override protected void setValueImpl(final RadComponent component, final String value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    if (getName().equals(SwingProperties.LABEL_FOR)) {
      String text = FormInspectionUtil.getText(component.getModule(), component);
      if (text != null && value != null) {
        RadComponent valueComponent = FormEditingUtil.findComponentAnywhere(component, value);
        if (valueComponent != null) {
          BindingProperty.checkCreateBindingFromText(valueComponent, text);
        }
      }
    }
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    setValue(component, null);
    markTopmostModified(component, false);
  }

  @Override public void importSnapshotValue(final JComponent component, final RadComponent radComponent) {
    // do nothing for now
  }
}
