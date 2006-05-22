/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.ui.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;

import javax.swing.*;

/**
 * User: Sergey.Vasiliev
 * Date: Mar 1, 2006
 */
public abstract class DefaultAddAction<T extends DomElement> extends AnAction {

  public DefaultAddAction() {
  }

  public DefaultAddAction(String text) {
    super(text);
  }

  public DefaultAddAction(String text, String description, Icon icon) {
    super(text, description, icon);
  }


  protected Class<? extends T> getElementClass() {
    return (Class<? extends T>)DomUtil.getRawType(getDomCollectionChildDescription().getType());
  }

  protected T doAdd() {
    return (T)getDomCollectionChildDescription().addValue(getParentDomElement(), getElementClass());
  }

  protected abstract DomCollectionChildDescription getDomCollectionChildDescription();

  protected abstract DomElement getParentDomElement();

  protected boolean beforeAddition() {
    return true;
  }

  protected void afterAddition(final AnActionEvent e, DomElement newElement) {
  }

  public void actionPerformed(final AnActionEvent e) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (beforeAddition()) {
          final DomElement parent = getParentDomElement();
          final DomManager domManager = parent.getManager();
          final ClassChooser[] oldChooser = new ClassChooser[]{null};
          final Class[] aClass = new Class[]{null};
          final T result = new WriteCommandAction<T>(domManager.getProject()) {
            protected void run(Result<T> result) throws Throwable {
              final T t = doAdd();
              aClass[0] = DomUtil.getRawType(parent.getGenericInfo().getCollectionChildDescription(t.getXmlElementName()).getType());
              oldChooser[0] = ClassChooserManager.getClassChooser(aClass[0]);
              final SmartPsiElementPointer pointer =
                SmartPointerManager.getInstance(getProject()).createSmartPsiElementPointer(t.getXmlTag());
              ClassChooserManager.registerClassChooser(aClass[0], new ClassChooser() {
                public Class<? extends T> chooseClass(final XmlTag tag) {
                  if (tag == pointer.getElement()) {
                    return getElementClass();
                  }
                  return oldChooser[0].chooseClass(tag);
                }

                public void distinguishTag(final XmlTag tag, final Class aClass) throws IncorrectOperationException {
                  oldChooser[0].distinguishTag(tag, aClass);
                }

                public Class[] getChooserClasses() {
                  return oldChooser[0].getChooserClasses();
                }
              });
              result.setResult((T)t.createStableCopy());
            }
          }.execute().getResultObject();
          ClassChooserManager.registerClassChooser(aClass[0], oldChooser[0]);
          afterAddition(e, ((StableElement)result).getWrappedElement());
        }
      }
    });
  }
}