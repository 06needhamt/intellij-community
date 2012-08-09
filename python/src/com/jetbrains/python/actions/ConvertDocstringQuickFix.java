package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.impl.PyStringLiteralExpressionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * User: catherine
 *
 * QuickFix to convert docstrings to the common form according to PEP-257
 * For consistency, always use """triple double quotes""" around docstrings.
 */
public class ConvertDocstringQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.convert.single.quoted.docstring");
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression instanceof PyStringLiteralExpression) {
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

      String stringText = expression.getText();
      int prefixLength = PyStringLiteralExpressionImpl
        .getPrefixLength(stringText);
      String prefix = stringText.substring(0, prefixLength);
      String content = expression.getText().substring(prefixLength);
      if (content.startsWith("'''") ) {
        content = content.substring(3, content.length()-3);
      } else if (content.startsWith("\"\"\""))
        return;
      else {
        content = content.length() == 1 ? "" : content.substring(1, content.length()-1);
      }

      PyExpression newString = elementGenerator.createDocstring(prefix+"\"\"\"" + content + "\"\"\"").getExpression();
      expression.replace(newString);
    }
  }

}
