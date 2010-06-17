package com.jetbrains.python.psi.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.jetbrains.django.util.DjangoStringUtil;
import com.jetbrains.python.psi.PyElementGenerator;

/**
 * @author traff
 */
public class PyStringLiteralExpressionManipulator extends AbstractElementManipulator<PyStringLiteralExpressionImpl> {
  public PyStringLiteralExpressionImpl handleContentChange(PyStringLiteralExpressionImpl element, TextRange range, String newContent) {
    String newName = range.replace(element.getText(), newContent);
    if (!DjangoStringUtil.isQuoted(newName)) {
      newName = '\'' + newName + '\'';
    }
    return (PyStringLiteralExpressionImpl)element
      .replace(PyElementGenerator.getInstance(element.getProject()).createStringLiteralAlreadyEscaped(newName));
  }
}
