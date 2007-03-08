/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.jsp;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.jsp.IJspElementType;

/**
 * @author peter
 */
public interface JspTokenType {
  IElementType JSP_COMMENT = new IJspElementType("JSP_COMMENT");
  IElementType JSP_SCRIPTLET_START = new IJspElementType("JSP_SCRIPTLET_START");
  IElementType JSP_SCRIPTLET_END = new IJspElementType("JSP_SCRIPTLET_END");
  IElementType JSP_DECLARATION_START = new IJspElementType("JSP_DECLARATION_START");
  IElementType JSP_DECLARATION_END = new IJspElementType("JSP_DECLARATION_END");
  IElementType JSP_EXPRESSION_START = new IJspElementType("JSP_EXPRESSION_START");
  IElementType JSP_EXPRESSION_END = new IJspElementType("JSP_EXPRESSION_END");
  IElementType JSP_DIRECTIVE_START = new IJspElementType("JSP_DIRECTIVE_START");
  IElementType JSP_DIRECTIVE_END = new IJspElementType("JSP_DIRECTIVE_END");
  IElementType JSP_BAD_CHARACTER = new IJspElementType("JSP_BAD_CHARACTER");
  IElementType JSP_WHITE_SPACE = new IJspElementType("JSP_WHITE_SPACE"); // for highlighting purposes
  IElementType JAVA_CODE = new IJspElementType("JAVA_CODE");
  IElementType JSP_FRAGMENT = new IJspElementType("JSP_FRAGEMENT"); // passed to template parser for all of jsp code
  IElementType JSPX_ROOT_TAG_HEADER = new IJspElementType("JSPX_ROOT_TAG_HEADER"); // These two only produced by JspxJavaLexer
  IElementType JSPX_ROOT_TAG_FOOTER = new IJspElementType("JSPX_ROOT_TAG_FOOTER");
  IElementType JSPX_JAVA_IN_ATTR_START = new IJspElementType("JSPX_JAVA_IN_ATTR_START");
  IElementType JSPX_JAVA_IN_ATTR_END = new IJspElementType("JSPX_JAVA_IN_ATTR_END");
  IElementType JSPX_JAVA_IN_ATTR = new IJspElementType("JSPX_JAVA_IN_ATTR");
}
