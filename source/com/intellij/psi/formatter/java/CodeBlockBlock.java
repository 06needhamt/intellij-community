package com.intellij.psi.formatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.formatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.codeFormatting.general.FormatterUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CodeBlockBlock extends AbstractJavaBlock {
  private final static int BEFORE_FIRST = 0;
  private final static int BEFORE_LBRACE = 1;
  private final static int INSIDE_BODY = 2;

  private final int myChildrenIndent;

  public CodeBlockBlock(final ASTNode node,
                        final Wrap wrap,
                        final Alignment alignment,
                        final Indent indent,
                        final CodeStyleSettings settings) {
    super(node, wrap, alignment, indent, settings);
    if (isSwitchCodeBlock() && !settings.INDENT_CASE_FROM_SWITCH) {
      myChildrenIndent = 0;
    }else {
      myChildrenIndent = 1;
    }
  }

  private boolean isSwitchCodeBlock() {
    return myNode.getTreeParent().getElementType() == ElementType.SWITCH_STATEMENT;
  }

  protected List<Block> buildChildren() {
    final ArrayList<Block> result = new ArrayList<Block>();
    Alignment childAlignment = createChildAlignment();
    Wrap childWrap = createChildWrap();

    buildChildren(result, childAlignment, childWrap);

    return result;

  }

  private void buildChildren(final ArrayList<Block> result, final Alignment childAlignment, final Wrap childWrap) {
    ChameleonTransforming.transformChildren(myNode);
    ASTNode child = myNode.getFirstChildNode();

    int state = BEFORE_FIRST;

    if (myNode.getPsi() instanceof JspClass) {
      state = INSIDE_BODY;
    }

    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child) && child.getTextLength() > 0) {
        final Indent indent = calcCurrentIndent(child, state);
        state = calcNewState(child, state);

        if (child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT) {
          child = processCaseAndStatementAfter(result, child, childAlignment, childWrap, indent);
        }
        else if (myNode.getElementType() == ElementType.CLASS && child.getElementType() == ElementType.LBRACE) {
          child = composeCodeBlock(result, child, getCodeBlockExternalIndent());
        }
        else {
          child = processChild(result, child, childAlignment, childWrap, indent);
        }
      }
      if (child != null) {
        child = child.getTreeNext();
      }
    }
  }

  private ASTNode composeCodeBlock(final ArrayList<Block> result, ASTNode child, final Indent indent) {
    final ArrayList<Block> localResult = new ArrayList<Block>();
    processChild(localResult, child, null, null, Indent.getNoneIndent());
    child = child.getTreeNext();
    while (child != null) {
      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        final boolean rBrace = isRBrace(child);
        final Indent childIndent = rBrace ? Indent.getNoneIndent() : getCodeBlockInternalIndent(myChildrenIndent);
        child = processChild(localResult, child, null, null, childIndent);
        if (rBrace) {
          result.add(createCodeBlockBlock(localResult, indent));
          return child;
        }
      }
      child = child.getTreeNext();
    }
    result.add(createCodeBlockBlock(localResult, indent));
    return null;
  }

  private SyntheticCodeBlock createCodeBlockBlock(final ArrayList<Block> localResult, final Indent indent) {
    final SyntheticCodeBlock result = new SyntheticCodeBlock(localResult, null, getSettings(), indent, null);
    result.setChildAttributes(new ChildAttributes(getCodeBlockInternalIndent(myChildrenIndent), null));
    return result;
  }

  private ASTNode processCaseAndStatementAfter(final ArrayList<Block> result,
                                               ASTNode child,
                                               final Alignment childAlignment,
                                               final Wrap childWrap, final Indent indent) {
    final ArrayList<Block> localResult = new ArrayList<Block>();
    processChild(localResult, child, null, null, Indent.getNoneIndent());
    child = child.getTreeNext();
    while (child != null) {
      if (child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT || isRBrace(child)) {
        result.add(createCaseSectionBlock(localResult, childAlignment, indent, childWrap));
        return child.getTreePrev();
      }

      if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
        Indent childIndent;
        if (child.getElementType() == ElementType.BLOCK_STATEMENT) {
          childIndent = Indent.getNoneIndent();
        } else {
          childIndent = Indent.createNormalIndent();
        }
        processChild(localResult, child, null, null, childIndent);
      }
      child = child.getTreeNext();
    }
    result.add(createCaseSectionBlock(localResult, childAlignment, indent, childWrap));
    return null;
  }

  private SyntheticCodeBlock createCaseSectionBlock(final ArrayList<Block> localResult, final Alignment childAlignment, final Indent indent,
                                                   final Wrap childWrap) {
    final SyntheticCodeBlock result = new SyntheticCodeBlock(localResult, childAlignment, getSettings(), indent, childWrap);
    result.setChildAttributes(new ChildAttributes(Indent.createNormalIndent(), null));
    return result;
  }

  private int calcNewState(final ASTNode child, int state) {
    switch (state) {
      case BEFORE_FIRST:
      {
        if (ElementType.COMMENT_BIT_SET.isInSet(child.getElementType())) {
          return BEFORE_FIRST;
        }
        else if (isLBrace(child)) {
          return INSIDE_BODY;
        }
        else {
          return BEFORE_LBRACE;
        }
      }
      case BEFORE_LBRACE:
      {
        if (isLBrace(child)) {
          return INSIDE_BODY;
        }
        else {
          return BEFORE_LBRACE;
        }
      }
    }
    return INSIDE_BODY;
  }

  private boolean isLBrace(final ASTNode child) {
    return child.getElementType() == ElementType.LBRACE;
  }

  private Indent calcCurrentIndent(final ASTNode child, final int state) {
    if (isRBrace(child)) {
      return Indent.getNoneIndent();
    }

    if (state == BEFORE_FIRST) return Indent.getNoneIndent();

    if (child.getElementType() == ElementType.SWITCH_LABEL_STATEMENT) {
      return getCodeBlockInternalIndent(myChildrenIndent);
    }
    if (state == BEFORE_LBRACE) {
      if (isLBrace(child)) {
        return Indent.getNoneIndent();
      }
      else {
        return Indent.createContinuationIndent();
      }
    }
    else {
      if (isRBrace(child)) {
        return Indent.getNoneIndent();
      }
      else {
        return getCodeBlockInternalIndent(myChildrenIndent);
      }
    }
  }

  private boolean isRBrace(final ASTNode child) {
    return child.getElementType() == ElementType.RBRACE;
  }

  @Override
  @NotNull
  public ChildAttributes getChildAttributes(final int newChildIndex) {
    if (isAfterJavaDoc(newChildIndex)) {
      return new ChildAttributes(Indent.getNoneIndent(), null);
    } else {
      return new ChildAttributes(getCodeBlockInternalIndent(myChildrenIndent), null);
    }
  }

  protected Wrap getReservedWrap() {
    return null;
  }

  protected void setReservedWrap(final Wrap reservedWrap) {
  }
}
