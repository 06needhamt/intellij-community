/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.reflect.DomFixedChildDescription;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;

/**
 * @author peter
 */
public class ChildGenericValueColumnInfo<T extends DomElement> extends DomColumnInfo<T,String> {
  private final TableCellEditor myEditor;
  private final DomFixedChildDescription myChildDescription;

  public ChildGenericValueColumnInfo(final String name, final DomFixedChildDescription description, final TableCellRenderer renderer, final TableCellEditor editor) {
    super(name, renderer);
    myEditor = editor;
    myChildDescription = description;
  }

  public ChildGenericValueColumnInfo(final String name, final DomFixedChildDescription description, final TableCellEditor editor) {
    this(name, description, new DefaultTableCellRenderer(), editor);
  }

  public final TableCellEditor getEditor(T value) {
    return myEditor;
  }

  public final Class<T> getColumnClass() {
    return (Class<T>)DomUtil.getRawType(myChildDescription.getType());
  }

  public void setValue(final T o, final String aValue) {
    getGenericValue(o).setStringValue(aValue);
  }

  protected final GenericDomValue getGenericValue(final T o) {
    return (GenericDomValue)myChildDescription.getValues(o).get(0);
  }

  public final String valueOf(T object) {
    final String stringValue = getGenericValue(object).getStringValue();
    return StringUtil.isEmpty(stringValue) ? getEmptyValuePresentation(object): stringValue;
  }

  protected final DomFixedChildDescription getChildDescription() {
    return myChildDescription;
  }

  protected String getEmptyValuePresentation(T object) {
    return "";
  }
}
