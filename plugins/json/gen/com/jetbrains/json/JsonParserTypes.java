// This is a generated file. Not intended for manual editing.
package com.jetbrains.json;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.jetbrains.json.psi.impl.*;

public interface JsonParserTypes {

  IElementType ARRAY = new JsonElementType("ARRAY");
  IElementType BOOLEAN_LITERAL = new JsonElementType("BOOLEAN_LITERAL");
  IElementType LITERAL = new JsonElementType("LITERAL");
  IElementType NULL_LITERAL = new JsonElementType("NULL_LITERAL");
  IElementType NUMBER_LITERAL = new JsonElementType("NUMBER_LITERAL");
  IElementType OBJECT = new JsonElementType("OBJECT");
  IElementType PROPERTY = new JsonElementType("PROPERTY");
  IElementType PROPERTY_NAME = new JsonElementType("PROPERTY_NAME");
  IElementType PROPERTY_VALUE = new JsonElementType("PROPERTY_VALUE");
  IElementType STRING_LITERAL = new JsonElementType("STRING_LITERAL");

  IElementType COLON = new JsonTokenType(":");
  IElementType COMMA = new JsonTokenType(",");
  IElementType FALSE = new JsonTokenType("false");
  IElementType L_BRAKET = new JsonTokenType("[");
  IElementType L_CURLY = new JsonTokenType("{");
  IElementType NULL = new JsonTokenType("null");
  IElementType NUMBER = new JsonTokenType("NUMBER");
  IElementType R_BRAKET = new JsonTokenType("]");
  IElementType R_CURLY = new JsonTokenType("}");
  IElementType STRING = new JsonTokenType("STRING");
  IElementType TRUE = new JsonTokenType("true");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == ARRAY) {
        return new JsonArrayImpl(node);
      }
      else if (type == BOOLEAN_LITERAL) {
        return new JsonBooleanLiteralImpl(node);
      }
      else if (type == LITERAL) {
        return new JsonLiteralImpl(node);
      }
      else if (type == NULL_LITERAL) {
        return new JsonNullLiteralImpl(node);
      }
      else if (type == NUMBER_LITERAL) {
        return new JsonNumberLiteralImpl(node);
      }
      else if (type == OBJECT) {
        return new JsonObjectImpl(node);
      }
      else if (type == PROPERTY) {
        return new JsonPropertyImpl(node);
      }
      else if (type == PROPERTY_NAME) {
        return new JsonPropertyNameImpl(node);
      }
      else if (type == PROPERTY_VALUE) {
        return new JsonPropertyValueImpl(node);
      }
      else if (type == STRING_LITERAL) {
        return new JsonStringLiteralImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
