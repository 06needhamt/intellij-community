package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;

/**
 *
 */
public class ConstantNode extends Expression {
  private final Result myValue;

  public ConstantNode(String value) {
    myValue = new TextResult(value);
  }

  public Result calculateResult(ExpressionContext context) {
    return myValue;
  }

  public Result calculateQuickResult(ExpressionContext context) {
    return myValue;
  }

  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    return LookupItem.EMPTY_ARRAY;
  }

}
