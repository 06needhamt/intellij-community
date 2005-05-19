package com.intellij.psi.formatter.newXmlFormatter.java;

import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.Formatter;
import com.intellij.newCodeFormatting.SpaceProperty;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.codeFormatting.general.FormatterUtil;


public class JavaSpacePropertyProcessor extends PsiElementVisitor{

  private PsiElement myParent;
  private int myRole1;
  private int myRole2;
  private final CodeStyleSettings mySettings;

  private SpaceProperty myResult;
  private final Helper myHelper;
  private ASTNode myChild1;
  private ASTNode myChild2;
  private final ImportHelper myImportHelper;

  public JavaSpacePropertyProcessor(ASTNode child, final CodeStyleSettings settings) {
    init(child);
    mySettings = settings;
    myHelper = new Helper(StdFileTypes.JAVA, myParent.getProject());
    myImportHelper = new ImportHelper(mySettings);

    if (myChild2 != null && mySettings.KEEP_FIRST_COLUMN_COMMENT && ElementType.COMMENT_BIT_SET.isInSet(myChild2.getElementType())) {
      myResult = Formatter.getInstance().createKeepingFirstLineSpaceProperty(0, Integer.MAX_VALUE, mySettings.KEEP_LINE_BREAKS, getKeepBlankLines());
    } else {

      if (myParent != null) {
        myParent.accept(this);
      }
    }

  }

  private int getKeepBlankLines() {
    if (myChild2.getElementType() == ElementType.RBRACE) return mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE;
    if (SourceTreeToPsiMap.psiElementToTree(myParent).getElementType() == ElementType.CLASS) return mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS;
    return mySettings.KEEP_BLANK_LINES_IN_CODE;
  }

  private void init(final ASTNode child) {
    if (child == null) return;
    ASTNode treePrev = child.getTreePrev();
    while (isWhiteSpace(treePrev)) {
      treePrev = treePrev.getTreePrev();
    }
    if (treePrev == null) {
      init(child.getTreeParent());
    } else {
      myChild2 = child;
      myChild1 = treePrev;
      final CompositeElement parent = (CompositeElement)myChild1.getTreeParent();
      myParent = SourceTreeToPsiMap.treeElementToPsi(parent);
      myRole1 = parent.getChildRole(myChild1);
      myRole2 = parent.getChildRole(myChild2);
    }
  }

  private boolean isWhiteSpace(final ASTNode treePrev) {
    if (treePrev == null) return false;
    return treePrev.getElementType() == ElementType.WHITE_SPACE || treePrev.getTextLength() == 0;
  }

  public SpaceProperty getResult() {
    return myResult;
  }

  public void visitArrayAccessExpression(PsiArrayAccessExpression expression) {
    if (myRole1 == ChildRole.ARRAY && myRole2 == ChildRole.LBRACKET) {
      final boolean space = false;
      createSpaceInCode(space);
    }
    else if (myRole1 == ChildRole.LBRACKET || myRole2 == ChildRole.RBRACKET) {
      createSpaceInCode(mySettings.SPACE_WITHIN_BRACKETS);
    }
  }

  private void createSpaceInCode(final boolean space) {
    createSpaceProperty(space, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  public void visitNewExpression(PsiNewExpression expression) {
    if (myRole2 == ChildRole.ARRAY_INITIALIZER) {
      createSpaceInCode(mySettings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE);
    } else if (myRole1 == ChildRole.NEW_KEYWORD) {
      createSpaceInCode(true);
    }
  }

  public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
    if (myRole1 == ChildRole.LBRACE) {
      createSpaceInCode(mySettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES);
    }
    else if (myRole2 == ChildRole.LBRACE) {
      createSpaceInCode(mySettings.SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE);
    }
    else if (myRole2 == ChildRole.RBRACE) {
      createSpaceProperty(mySettings.SPACE_WITHIN_ARRAY_INITIALIZER_BRACES, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    }
    else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_AFTER_COMMA);
    }
    else if (myRole2 == ChildRole.COMMA) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COMMA);
    }
  }

  public void visitClass(PsiClass aClass) {
    if (myRole2 == ChildRole.LBRACE) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_CLASS_LBRACE, mySettings.CLASS_BRACE_STYLE);
    } else if (myRole2 == ChildRole.METHOD  || myRole2 == ChildRole.CLASS_INITIALIZER) {
      if (myRole1 == ChildRole.LBRACE) {
        myResult = Formatter.getInstance().createSpaceProperty(0,0, 1, mySettings.KEEP_LINE_BREAKS, 0);
      } else {
        final int blankLines = mySettings.BLANK_LINES_AROUND_METHOD + 1;
        myResult = Formatter.getInstance().createSpaceProperty(0,0,blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (myRole1 == ChildRole.METHOD || myRole1 == ChildRole.CLASS_INITIALIZER) {
      if (myRole2 == ChildRole.RBRACE) {
        myResult = Formatter.getInstance().createSpaceProperty(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      } else {
        final int blankLines = mySettings.BLANK_LINES_AROUND_METHOD + 1;
        myResult = Formatter.getInstance().createSpaceProperty(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (myRole1 == ChildRole.CLASS) {
      if (myRole2 == ChildRole.RBRACE) {
        myResult = Formatter.getInstance().createSpaceProperty(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      } else {
        final int blankLines = mySettings.BLANK_LINES_AROUND_CLASS + 1;
        myResult = Formatter.getInstance().createSpaceProperty(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }
    else if (myRole2 == ChildRole.CLASS) {
      if (myRole1 == ChildRole.LBRACE) {
        myResult = Formatter.getInstance().createSpaceProperty(0,0, 1, mySettings.KEEP_LINE_BREAKS, 0);
      } else {
        final int blankLines = mySettings.BLANK_LINES_AROUND_CLASS + 1;
        myResult = Formatter.getInstance().createSpaceProperty(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }

    else if (myRole2 == ChildRole.FIELD) {
      if (myRole1 == ChildRole.LBRACE) {
        myResult = Formatter.getInstance().createSpaceProperty(0,0, 1, mySettings.KEEP_LINE_BREAKS, 0);
      } else {
        final int blankLines = mySettings.BLANK_LINES_AROUND_FIELD + 1;
        myResult = Formatter.getInstance().createSpaceProperty(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }

    else if (myRole1 == ChildRole.FIELD) {
      if (myRole2 == ChildRole.RBRACE) {
        myResult = Formatter.getInstance().createSpaceProperty(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      } else {
        final int blankLines = mySettings.BLANK_LINES_AROUND_FIELD + 1;
        myResult = Formatter.getInstance().createSpaceProperty(0, Integer.MAX_VALUE, blankLines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
      }
    }

    else if (myRole1 == ChildRole.MODIFIER_LIST) {
      processModifierList();
    }

    else if(myRole1 == ChildRole.LBRACE && myRole2 == ChildRole.RBRACE) {
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    }
    
    else if (myRole2 == ChildRole.EXTENDS_LIST || myRole2 == ChildRole.IMPLEMENTS_LIST) {
      createSpaceInCode(true);
    }
    
    else if (myRole2 == ChildRole.TYPE_PARAMETER_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_TYPE_PARAMETER_LIST);
    }
  }

  public void visitImportList(PsiImportList list) {
    if (ElementType.IMPORT_STATEMENT_BASE_BIT_SET.isInSet(myChild1.getElementType()) &&
        ElementType.IMPORT_STATEMENT_BASE_BIT_SET.isInSet(myChild2.getElementType())) {
      int emptyLines = myImportHelper.getEmptyLinesBetween(
        (PsiImportStatementBase)SourceTreeToPsiMap.treeElementToPsi(myChild1),
        (PsiImportStatementBase)SourceTreeToPsiMap.treeElementToPsi(myChild2)
      ) + 1;
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, emptyLines,
                                                             mySettings.KEEP_LINE_BREAKS,
                                                             mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

  }

  public void visitFile(PsiFile file) {
    if (myRole1 == ChildRole.PACKAGE_STATEMENT) {
      int lf = mySettings.BLANK_LINES_AFTER_PACKAGE + 1;
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

    else if (myRole2 == ChildRole.PACKAGE_STATEMENT) {
      int lf = mySettings.BLANK_LINES_BEFORE_PACKAGE + 1;
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

    else if (myRole1 == ChildRole.IMPORT_LIST) {
      int lf = mySettings.BLANK_LINES_AFTER_IMPORTS + 1;
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

    else if (myRole2 == ChildRole.IMPORT_LIST) {
      int lf = mySettings.BLANK_LINES_BEFORE_IMPORTS + 1;
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    } else if (myRole2 == ChildRole.CLASS) {
      int lf = mySettings.BLANK_LINES_AROUND_CLASS + 1;
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, lf, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    }

  }

  public void visitWhileStatement(PsiWhileStatement statement) {
    if (myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_WHILE_PARENTHESES);
    } else if (myRole2 == ChildRole.LOOP_BODY) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_WHILE_LBRACE, mySettings.BRACE_STYLE);
    }

  }

  public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    if (myRole1 == ChildRole.WHILE_KEYWORD && myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_WHILE_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_WHILE_PARENTHESES);
    }
    else if (myRole2 == ChildRole.LOOP_BODY) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_DO_LBRACE, mySettings.BRACE_STYLE);
    }
    else if (myRole1 == ChildRole.LOOP_BODY) {
      processOnNewLineCondition(mySettings.WHILE_ON_NEW_LINE);
    }
  }

  private void processOnNewLineCondition(final boolean onNewLine) {
    if (onNewLine) {
      if (!mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE) {
        myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      } else {
        myResult = Formatter.getInstance().createDependentLFProperty(0, 1, myParent.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    } else {
      myResult = myResult = Formatter.getInstance().createSpaceProperty(1, 1, 0, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  public void visitTryStatement(PsiTryStatement statement) {
    if (myRole2 == ChildRole.FINALLY_KEYWORD) {
      processOnNewLineCondition(mySettings.FINALLY_ON_NEW_LINE);
    }
    else if (myRole2 == ChildRole.FINALLY_BLOCK || myRole2 == ChildRole.TRY_BLOCK) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_FINALLY_LBRACE, mySettings.BRACE_STYLE);
    }
    else if (myRole2 == ChildRole.CATCH_SECTION) {
      processOnNewLineCondition(mySettings.CATCH_ON_NEW_LINE);
    }
  }

  public void visitForeachStatement(PsiForeachStatement statement) {
    if (myRole1 == ChildRole.FOR_KEYWORD && myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_FOR_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
    }
    else if (myRole1 == ChildRole.FOR_ITERATION_PARAMETER && myRole2 == ChildRole.COLON) {
      createSpaceInCode(true);
    }
    else if (myRole1 == ChildRole.COLON && myRole2 == ChildRole.FOR_ITERATED_VALUE) {
      createSpaceInCode(true);
    } else if (myRole2 == ChildRole.LOOP_BODY) {
      createSpaceInCode(mySettings.SPACE_BEFORE_FOR_LBRACE);
    }

  }

  public void visitAssignmentExpression(PsiAssignmentExpression expression) {
    if (myRole1 == ChildRole.OPERATION_SIGN || myRole2 == ChildRole.OPERATION_SIGN) {
      createSpaceInCode(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    }
  }

  public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {

    if (myRole1 == ChildRole.LPARENTH) {
      createParenSpace(mySettings.PARENTHESES_EXPRESSION_LPAREN_WRAP, mySettings.SPACE_WITHIN_PARENTHESES);
    } else if (myRole2 == ChildRole.RPARENTH) {
      createParenSpace(mySettings.PARENTHESES_EXPRESSION_RPAREN_WRAP, mySettings.SPACE_WITHIN_PARENTHESES);
    }

  }

  public void visitCodeBlock(PsiCodeBlock block) {
    if (myParent instanceof JspCodeBlock) {
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    
    else if (myRole1 == ChildRole.NONE || myRole2 == ChildRole.NONE) {
      if (myChild1.getElementType() == ElementType.END_OF_LINE_COMMENT) {
        myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);        
      } else {
        myResult = null;
      }
    } else if (myRole1 == ChildRole.LBRACE) {
      if (!mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE) {
        myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      } else {
        myResult = Formatter.getInstance().createDependentLFProperty(0, 1, block.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
    else if (myRole2 == ChildRole.RBRACE) {
      if (!mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE) {
        myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      } else {
        myResult = Formatter.getInstance().createDependentLFProperty(0, 1, block.getTextRange(), mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
      }
    } else if (myRole1 == ChildRole.STATEMENT_IN_BLOCK && myRole2 == ChildRole.STATEMENT_IN_BLOCK) {
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  public void visitIfStatement(PsiIfStatement statement) {
    if (myRole2 == ChildRole.ELSE_KEYWORD) {
      if (myChild1.getElementType() != ElementType.BLOCK_STATEMENT) {
        myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
      } else {
        if (mySettings.ELSE_ON_NEW_LINE) {
          myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        } else {
          myResult = createNonLFSpace(1, false);
        }
      }
    } else if (myRole1 == ChildRole.ELSE_KEYWORD) {
      if (myChild2.getElementType() == ElementType.IF_STATEMENT) {
        if (mySettings.SPECIAL_ELSE_IF_TREATMENT) {
          myResult = createNonLFSpace(1, true);
        } else {
          myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
        }
      } else {
        if (myChild2.getElementType() == ElementType.BLOCK_STATEMENT) {
          myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_ELSE_LBRACE, mySettings.BRACE_STYLE);
        } else {
          createSpaceInCode(mySettings.SPACE_BEFORE_ELSE_LBRACE);
        }
      }
    }
    else if (myChild2.getElementType() == ElementType.BLOCK_STATEMENT) {
      boolean space = myRole2 == ChildRole.ELSE_BRANCH ? mySettings.SPACE_BEFORE_ELSE_LBRACE: mySettings.SPACE_BEFORE_IF_LBRACE;
      myResult = getSpaceBeforeLBrace(space, mySettings.BRACE_STYLE);
    }
    else if (myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_IF_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_IF_PARENTHESES);
    }
    else if (myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_IF_PARENTHESES);
    }
    else if (myRole2 == ChildRole.THEN_BRANCH) {
      createSpaceInCode(true);
    }
  }

  private SpaceProperty createNonLFSpace(int spaces, final boolean keepLineBreaks) {
    final ASTNode prev = getPrevElementType(myChild2);
    if (prev != null && prev.getElementType() == ElementType.END_OF_LINE_COMMENT) {
      return Formatter.getInstance().createSpaceProperty(0, Integer.MAX_VALUE, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      return Formatter.getInstance().createSpaceProperty(spaces, spaces, 0, keepLineBreaks, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  private ASTNode getPrevElementType(final ASTNode child) {
    return FormatterUtil.getLeafNonSpaceBefore(child);
  }

  private SpaceProperty getSpaceBeforeLBrace(final boolean spaceBeforeLbrace, int braceStyle) {
    if (braceStyle == CodeStyleSettings.END_OF_LINE) {
      int space = spaceBeforeLbrace ? 1 : 0;
      return createNonLFSpace(space, false);
    } else {
      return Formatter.getInstance().createSpaceProperty(0,0, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
  }

  public void visitBinaryExpression(PsiBinaryExpression expression) {
    PsiJavaToken sign = expression.getOperationSign();
    IElementType i = sign.getTokenType();
    if (myRole1 == ChildRole.OPERATION_SIGN || myRole2 == ChildRole.OPERATION_SIGN) {
      if (i == JavaTokenType.OROR || i == JavaTokenType.ANDAND) {
        createSpaceInCode(mySettings.SPACE_AROUND_LOGICAL_OPERATORS);
      }
      else if (i == JavaTokenType.OR || i == JavaTokenType.AND || i == JavaTokenType.XOR) {
        createSpaceInCode(mySettings.SPACE_AROUND_BITWISE_OPERATORS);
      }
      else if (i == JavaTokenType.EQEQ || i == JavaTokenType.NE) {
        createSpaceInCode(mySettings.SPACE_AROUND_EQUALITY_OPERATORS);
      }
      else if (i == JavaTokenType.GT || i == JavaTokenType.LT || i == JavaTokenType.GE || i == JavaTokenType.LE) {
        createSpaceInCode(mySettings.SPACE_AROUND_RELATIONAL_OPERATORS);
      }
      else if (i == JavaTokenType.PLUS || i == JavaTokenType.MINUS) {
        createSpaceInCode(mySettings.SPACE_AROUND_ADDITIVE_OPERATORS);
      }
      else if (i == JavaTokenType.ASTERISK || i == JavaTokenType.DIV || i == JavaTokenType.PERC) {
        createSpaceInCode(mySettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS);
      }
      else if (i == JavaTokenType.LTLT || i == JavaTokenType.GTGT || i == JavaTokenType.GTGTGT) {
        createSpaceInCode(mySettings.SPACE_AROUND_SHIFT_OPERATORS);
      } else {
        createSpaceInCode(false);
      }
    }
  }

  public void visitField(PsiField field) {
    if (myRole1 == ChildRole.INITIALIZER_EQ || myRole2 == ChildRole.INITIALIZER_EQ) {
      createSpaceInCode(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    } else if (myRole1 == ChildRole.TYPE || myRole2 == ChildRole.TYPE) {
      createSpaceInCode(true);
    } else if (myChild2.getElementType() == ElementType.SEMICOLON) {
      createSpaceProperty(false, false, 0);
    } else if (myRole1 == ChildRole.MODIFIER_LIST) {
      createSpaceProperty(true, false, 0);      
    }
  }

  public void visitLocalVariable(PsiLocalVariable variable) {
    if (myRole1 == ChildRole.INITIALIZER_EQ || myRole2 == ChildRole.INITIALIZER_EQ) {
      createSpaceInCode(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    } else if (myRole1 == ChildRole.MODIFIER_LIST) {
      createSpaceInCode(true);
    } else if (myRole2 == ChildRole.TYPE_REFERENCE) {
      createSpaceInCode(true);
    } else if (myChild2.getElementType() == ElementType.SEMICOLON) {
      createSpaceProperty(false, false, 0);
    } 
  }

  public void visitMethod(PsiMethod method) {
    if (myRole2 == ChildRole.PARAMETER_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_PARENTHESES);
    }
    else if (myRole1 == ChildRole.PARAMETER_LIST && myRole2 == ChildRole.THROWS_LIST) {
      createSpaceInCode(true);
    } else if (myRole2 == ChildRole.METHOD_BODY) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_METHOD_LBRACE, mySettings.METHOD_BRACE_STYLE);
    } else if (myRole1 == ChildRole.MODIFIER_LIST) {
      processModifierList();
    } else if (ElementType.COMMENT_BIT_SET.isInSet(myChild1.getElementType()) 
               && (myRole2 == ChildRole.MODIFIER_LIST || myRole2 == ChildRole.TYPE_REFERENCE)) {
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, 0);
    }

  }

  private void processModifierList() {
    if (mySettings.MODIFIER_LIST_WRAP) {
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      createSpaceProperty(true, false, 0);
    }
  }

  public void visitModifierList(PsiModifierList list) {
    createSpaceInCode(true);
  }

  public void visitParameterList(PsiParameterList list) {
    if (myRole1 == ChildRole.LPARENTH && myRole2 == ChildRole.RPARENTH) {
      createParenSpace(mySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE, false);      
    }
    else if (myRole2 == ChildRole.RPARENTH) {
      createParenSpace(mySettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_METHOD_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH) {
      createParenSpace(mySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_METHOD_PARENTHESES);
    } else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(true);
    }

  }

  private void createParenSpace(final boolean onNewLine, final boolean space) {
    createParenSpace(onNewLine, space, myParent.getTextRange());
  }

  private void createParenSpace(final boolean onNewLine, final boolean space, final TextRange dependance) {
    if (onNewLine) {
      myResult = Formatter.getInstance().createDependentLFProperty(0,0, dependance, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      createSpaceInCode(space);
    }
  }

  public void visitElement(PsiElement element) {
    if (myRole1 == ChildRole.MODIFIER_LIST) {
      processModifierList();
    }
  }

  public void visitExpressionList(PsiExpressionList list) {
    if (myRole1 == ChildRole.LPARENTH && myRole2 == ChildRole.RPARENTH) {
      createParenSpace(mySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE, false);      
    }
    else if (myRole2 == ChildRole.RPARENTH) {
      createParenSpace(mySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE, myRole1 == ChildRole.COMMA
                                                                       || mySettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES);
    } else if (myRole1 == ChildRole.LPARENTH) {
      createParenSpace(mySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES);
    } else if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(true);
    } else if (myRole2 == ChildRole.COMMA) {
      createSpaceInCode(false);
    }

  }

  public void visitSynchronizedStatement(PsiSynchronizedStatement statement) {
    if (myRole1 == ChildRole.SYNCHRONIZED_KEYWORD || myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SYNCHRONIZED_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SYNCHRONIZED_PARENTHESES);
    }
    else if (myRole2 == ChildRole.BLOCK) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SYNCHRONIZED_LBRACE);
    }

  }

  public void visitSwitchStatement(PsiSwitchStatement statement) {
    if (myRole1 == ChildRole.SWITCH_KEYWORD && myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SWITCH_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_SWITCH_PARENTHESES);
    }
    else if (myRole2 == ChildRole.SWITCH_BODY) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_SWITCH_LBRACE, mySettings.BRACE_STYLE);
    }

  }

  public void visitForStatement(PsiForStatement statement) {
    if (myRole2 == ChildRole.LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_FOR_PARENTHESES);
    }
    else if (myRole1 == ChildRole.LPARENTH) {
      ASTNode rparenth = findFrom(myChild2, ElementType.RPARENTH, true);
      if (rparenth == null) {
        createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
      } else {
        createParenSpace(mySettings.FOR_STATEMENT_LPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_FOR_PARENTHESES, new TextRange(myChild1.getTextRange().getStartOffset(), rparenth.getTextRange().getEndOffset()));
      }
    }
    else if (myRole2 == ChildRole.RPARENTH) {
      ASTNode lparenth = findFrom(myChild2, ElementType.LPARENTH, false);
      if (lparenth == null) {
        createSpaceInCode(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
      } else {
        createParenSpace(mySettings.FOR_STATEMENT_RPAREN_ON_NEXT_LINE, mySettings.SPACE_WITHIN_FOR_PARENTHESES, new TextRange(lparenth.getTextRange().getStartOffset(), myChild2.getTextRange().getEndOffset()));
      }

    }
    else if (myRole1 == ChildRole.FOR_INITIALIZATION) {
      createSpaceInCode(mySettings.SPACE_AFTER_SEMICOLON);
    }
    else if (myRole1 == ChildRole.CONDITION) {
      createSpaceInCode(mySettings.SPACE_BEFORE_SEMICOLON);
    }
    else if (myRole1 == ChildRole.FOR_SEMICOLON) {
      createSpaceInCode(mySettings.SPACE_AFTER_SEMICOLON);
    }
    else if (myRole2 == ChildRole.LOOP_BODY) {
      if (myChild2.getElementType() == ElementType.BLOCK_STATEMENT) {
        myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_FOR_LBRACE, mySettings.BRACE_STYLE);
      } else if (mySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE) {
        myResult = Formatter.getInstance().createDependentLFProperty(1, 1, myParent.getTextRange(), false, mySettings.KEEP_BLANK_LINES_IN_CODE);
      } else {
        myResult = Formatter.getInstance().createSpaceProperty(0 ,0, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
      }
    }
  }

  private ASTNode findFrom(ASTNode current, final IElementType expected, boolean forward) {
    while (current != null) {
      if (current.getElementType() == expected) return current;
      current = forward ? current.getTreeNext() : current.getTreePrev();
    }
    return null;
  }

  public void visitCatchSection(PsiCatchSection section) {
    if (myRole2 == ChildRole.CATCH_BLOCK) {
      myResult = getSpaceBeforeLBrace(mySettings.SPACE_BEFORE_CATCH_LBRACE, mySettings.BRACE_STYLE);
    }
    else if (myRole2 == ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH) {
      createSpaceInCode(mySettings.SPACE_BEFORE_CATCH_PARENTHESES);
    }
    else if (myRole1 == ChildRole.CATCH_BLOCK_PARAMETER_LPARENTH || myRole2 == ChildRole.CATCH_BLOCK_PARAMETER_RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_CATCH_PARENTHESES);
    }

  }

  public void visitReferenceParameterList(PsiReferenceParameterList list) {
    if (myRole1 == ChildRole.LT_IN_TYPE_LIST && myRole2 == ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST) {
      createSpaceInCode(false);
    } else if (myRole1 == ChildRole.LT_IN_TYPE_LIST && myRole2 == ChildRole.GT_IN_TYPE_LIST) {
      createSpaceInCode(true);
    } else if (myRole1 == ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST && myRole2 == ChildRole.COMMA) {
      createSpaceInCode(false);
    } else if (myRole1 == ChildRole.COMMA && myRole2 == ChildRole.TYPE_IN_REFERENCE_PARAMETER_LIST) {
      createSpaceInCode(true);
    } else if (myRole2 == ChildRole.GT_IN_TYPE_LIST) {
      createSpaceInCode(false);
    }


  }

  public void visitTypeCastExpression(PsiTypeCastExpression expression) {
    if (myRole1 == ChildRole.LPARENTH || myRole2 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_WITHIN_CAST_PARENTHESES);
    }
    else if (myRole1 == ChildRole.RPARENTH) {
      createSpaceInCode(mySettings.SPACE_AFTER_TYPE_CAST);
    }
  }

  private void createSpaceProperty(boolean space, int keepBlankLines) {
    createSpaceProperty(space, mySettings.KEEP_LINE_BREAKS, keepBlankLines);
  }

  private void createSpaceProperty(boolean space, boolean keepLineBreaks, final int keepBlankLines) {
    final ASTNode prev = getPrevElementType(myChild2);
    if (prev != null && prev.getElementType() == ElementType.END_OF_LINE_COMMENT) {
      myResult = Formatter.getInstance().createSpaceProperty(0, 0, 1, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    } else {
      if (!space && !myHelper.canStickChildrenTogether(myChild1, myChild2)) {
        space = true;
      }
      
      if (!keepLineBreaks && myRole2 == ChildRole.NONE) {
        keepLineBreaks = true;
      }
      myResult = Formatter.getInstance().createSpaceProperty(space ? 1 : 0, space ? 1 : 0, 0, keepLineBreaks, keepBlankLines);
    }
  }

  public static int getMaxLineInDeclarations(final CodeStyleSettings settings) {
    return settings.KEEP_BLANK_LINES_IN_DECLARATIONS + 1;
  }

  public static int getMaxLineInCode(final CodeStyleSettings settings) {
    return settings.KEEP_BLANK_LINES_IN_CODE + 1;
  }

  public void visitReferenceList(PsiReferenceList list) {
    if (myRole1 == ChildRole.COMMA) {
      createSpaceInCode(true);
    } else if (myRole2 == ChildRole.COMMA) {
      createSpaceInCode(false);
    } else if (myRole1 == ChildRole.EXTENDS_KEYWORD || myRole2 == ChildRole.EXTENDS_KEYWORD) {
      createSpaceInCode(true);      
    } else if (myRole1 == ChildRole.AMPERSAND_IN_BOUNDS_LIST | myRole2 == ChildRole.AMPERSAND_IN_BOUNDS_LIST) {
      createSpaceInCode(true);      
    } else if (myRole1 == ChildRole.IMPLEMENTS_KEYWORD || myRole2 == ChildRole.IMPLEMENTS_KEYWORD) {
      createSpaceInCode(true);      
    }    
    else if (myRole1 == ChildRole.THROWS_KEYWORD) {
      createSpaceInCode(true);      
    } 
 
  }

  public void visitReferenceExpression(PsiReferenceExpression expression) {
    visitReferenceElement(expression);
  }

  public void visitConditionalExpression(PsiConditionalExpression expression) {
    if (myRole2 == ChildRole.QUEST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_QUEST);
    } else if (myRole1 == ChildRole.QUEST) {
      createSpaceInCode(mySettings.SPACE_AFTER_QUEST);
    } else if (myRole2 == ChildRole.COLON) {
      createSpaceInCode(mySettings.SPACE_BEFORE_COLON);
    } else if (myRole1 == ChildRole.COLON) {
      createSpaceInCode(mySettings.SPACE_AFTER_COLON);
    }
  }

  public void visitStatement(PsiStatement statement) {
    if (myRole2 == ChildRole.CLOSING_SEMICOLON) {
      createSpaceInCode(false);
    }
  }

  public void visitReturnStatement(PsiReturnStatement statement) {
    if (myChild2.getElementType() == ElementType.SEMICOLON) {
      createSpaceInCode(false);
    }
    else if (myRole1 == ChildRole.RETURN_KEYWORD) {
      createSpaceInCode(true);
    } else {
      super.visitReturnStatement(statement);
    }
  }

  public void visitMethodCallExpression(PsiMethodCallExpression expression) {
    if (myRole2 == ChildRole.ARGUMENT_LIST) {
      createSpaceInCode(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
    }
  }

  public void visitTypeParameter(PsiTypeParameter classParameter) {
    createSpaceInCode(true);
  }
}
