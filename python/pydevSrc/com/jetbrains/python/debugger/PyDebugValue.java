package com.jetbrains.python.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

// todo: trim long values
// todo: load long lists by parts
// todo: null modifier for modify modules, class objects etc.
public class PyDebugValue extends XValue {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.PyDebugValue");

  private final String myName;
  private String myTempName = null;
  private final String myType;
  private String myValue;
  private final boolean myContainer;
  private final PyDebugValue myParent;
  private final IPyDebugProcess myDebugProcess;

  public PyDebugValue(final String name, final String type, final String value, final boolean container) {
    this(name, type, value, container, null, null);
  }

  public PyDebugValue(final String name, final String type, final String value, final boolean container,
                      final PyDebugValue parent, final IPyDebugProcess debugProcess) {
    myName = name;
    myType = type;
    myValue = value;
    myContainer = container;
    myParent = parent;
    myDebugProcess = debugProcess;
  }

  public String getName() {
    return myName;
  }

  public String getTempName() {
    return myTempName != null ? myTempName : myName;
  }

  public void setTempName(String tempName) {
    myTempName = tempName;
  }

  public String getType() {
    return myType;
  }

  public String getValue() {
    return myValue;
  }

  public void setValue(String value) {
    myValue = value;
  }

  public boolean isContainer() {
    return myContainer;
  }

  public PyDebugValue getParent() {
    return myParent;
  }

  public PyDebugValue getTopParent() {
    return myParent == null ? this : myParent.getTopParent();
  }

  // todo: pass StringBuilder to recursive calls
  public String getEvaluationExpression() {
    if (myParent == null) {
      return getTempName();
    }
    else if ("list".equals(myParent.getType()) || "tuple".equals(myParent.getType())) {
      return new StringBuilder().append(myParent.getEvaluationExpression()).append('[').append(myName).append(']').toString();
    }
    else if ("dict".equals(myParent.getType())) {
      return new StringBuilder().append(myParent.getEvaluationExpression()).append("['").append(myName).append("']").toString();
    }
    else {
      return new StringBuilder().append(myParent.getEvaluationExpression()).append('.').append(myName).toString();
    }
  }

  /*
  private void buildQualifiedExpression(final StringBuilder sb, final PyDebugValue child) {
    if ()
  }
  */

  @Override
  public void computePresentation(@NotNull final XValueNode node) {
    node.setPresentation(myName, getValueIcon(), myType, PyTypeHandler.format(this), myContainer);
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        if (myDebugProcess == null) return;

        try {
          final List<PyDebugValue> values = myDebugProcess.loadVariable(PyDebugValue.this);
          if (!node.isObsolete()) {
            node.addChildren(values, true);
          }
        }
        catch (PyDebuggerException e) {
          if (!node.isObsolete()) {
            node.setErrorMessage("Unable to display children");
          }
          LOG.warn(e);
        }
      }
    });
  }

  @Override
  public XValueModifier getModifier() {
    return new PyValueModifier(myDebugProcess, this);
  }

  private Icon getValueIcon() {
    if (!myContainer) {
      return DebuggerIcons.PRIMITIVE_VALUE_ICON;
    }
    else if ("list".equals(myType) || "tuple".equals(myType)) {
      return DebuggerIcons.ARRAY_VALUE_ICON;
    }
    else {
      return DebuggerIcons.VALUE_ICON;
    }
  }

}
