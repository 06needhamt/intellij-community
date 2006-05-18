/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.Function;
import com.intellij.util.containers.*;
import com.intellij.util.xml.NamedEnumUtil;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.HashSet;

/**
 * @author peter
 */
public class ComboControl extends BaseControl<JComboBox, String> {
  private static final Pair<String,Icon> EMPTY = Pair.create(" ", null);
  private final Factory<List<Pair<String, Icon>>> myDataFactory;
  private boolean myNullable;
  private Map<String,Icon> myIcons = new com.intellij.util.containers.HashMap<String, Icon>();
  private final ActionListener myCommitListener = new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      commit();
    }
  };

  public ComboControl(final GenericDomValue genericDomValue, final Factory<List<Pair<String, Icon>>> dataFactory) {
    this(new DomStringWrapper(genericDomValue), dataFactory);
  }

  public ComboControl(final DomWrapper<String> domWrapper, final Factory<List<Pair<String, Icon>>> dataFactory) {
    super(domWrapper);
    myDataFactory = dataFactory;
  }

  public ComboControl(final DomWrapper<String> domWrapper, final Class<? extends Enum> aClass) {
    super(domWrapper);
    myDataFactory = createEnumFactory(aClass);
  }

  public final boolean isNullable() {
    return myNullable;
  }

  public final void setNullable(final boolean nullable) {
    myNullable = nullable;
  }

  public ComboControl(final GenericDomValue<? extends DomElement> reference) {
    this(reference, new Factory<List<Pair<String, Icon>>>() {
      public List<Pair<String, Icon>> create() {
        return ContainerUtil.map(reference.getManager().getPossibleTargets(reference), new Function<DomElement, Pair<String, Icon>>() {
          public Pair<String, Icon> fun(final DomElement s) {
            return Pair.create(ElementPresentationManager.getElementName(s), ElementPresentationManager.getIcon(s));
          }
        });
      }
    });
  }

  static Factory<List<Pair<String, Icon>>> createEnumFactory(final Class<? extends Enum> aClass) {
    return new Factory<List<Pair<String, Icon>>>() {
      public List<Pair<String, Icon>> create() {
        return ContainerUtil.map2List(aClass.getEnumConstants(), new Function<Enum, Pair<String, Icon>>() {
          public Pair<String, Icon> fun(final Enum s) {
            return Pair.create(NamedEnumUtil.getEnumValueByElement(s), null);
          }
        });
      }
    };
  }

  public static <T extends Enum> JComboBox createEnumComboBox(final Class<T> type) {
    return tuneUpComboBox(new JComboBox(), createEnumFactory(type));
  }

  private static JComboBox tuneUpComboBox(final JComboBox comboBox, Factory<List<Pair<String, Icon>>> dataFactory) {
    final List<Pair<String, Icon>> list = dataFactory.create();
    final Set<String> standardValues = new HashSet<String>();
    for (final Pair<String, Icon> s : list) {
      comboBox.addItem(s);
      standardValues.add(s.first);
    }
    return initComboBox(comboBox, new Condition<String>() {
      public boolean value(final String object) {
        return standardValues.contains(object);
      }
    });
  }

  static JComboBox initComboBox(final JComboBox comboBox, final Condition<String> validity) {
    comboBox.setEditable(false);
    comboBox.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        final Pair<String, Icon> pair = (Pair<String, Icon>)value;
        final String text = pair.first;
        setText(text);
        final Dimension dimension = getPreferredSize();
        if (!validity.value(text)) {
          setFont(getFont().deriveFont(Font.ITALIC));
          setForeground(Color.RED);
        }
        setIcon(pair.second);
        setPreferredSize(new Dimension(-1, dimension.height));
        return this;
      }
    });
    return comboBox;
  }

  protected JComboBox createMainComponent(final JComboBox boundedComponent) {
    return tuneUpComboBox(boundedComponent == null ? new JComboBox() : boundedComponent, myDataFactory);
  }

  public boolean isValidValue(final String object) {
    return myNullable && object == EMPTY.first || myIcons.containsKey(object);
  }

  protected void doReset() {
    final JComboBox comboBox = getComponent();
    comboBox.removeActionListener(myCommitListener);
    final String oldValue = getValue();
    final List<Pair<String, Icon>> data = myDataFactory.create();
    myIcons.clear();
    comboBox.removeAllItems();
    if (myNullable) {
      comboBox.addItem(EMPTY);
    }
    for (final Pair<String, Icon> s : data) {
      comboBox.addItem(s);
      myIcons.put(s.first, s.second);
    }
    setValue(oldValue);
    super.doReset();
    comboBox.addActionListener(myCommitListener);
  }

  protected final String getValue() {
    final Pair<String, Icon> pair = (Pair<String, Icon>)getComponent().getSelectedItem();
    return pair == null || pair == EMPTY ? null : pair.first;
  }

  protected final void setValue(final String value) {
    final JComboBox component = getComponent();
    if (!isValidValue(value)) {
      component.setEditable(true);
    }
    component.setSelectedItem(Pair.create(value, myIcons.get(value)));
    component.setEditable(false);
  }
}
