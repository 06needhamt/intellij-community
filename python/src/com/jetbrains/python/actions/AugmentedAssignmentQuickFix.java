package com.jetbrains.python.actions;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyAugAssignmentStatementImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: catherine
 *
 * QuickFix to replace assignment that can be replaced with augmented assignment.
 * for instance, i = i + 1   --> i +=1
 */
public class AugmentedAssignmentQuickFix implements LocalQuickFix {

  public AugmentedAssignmentQuickFix() {
  }

  @NotNull
  public String getName() {
    return PyBundle.message("QFIX.augment.assignment");
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();

    if (element instanceof PyAssignmentStatement && element.isWritable()) {
      PyAssignmentStatement statement = (PyAssignmentStatement)element;

      PyExpression target = statement.getLeftHandSideExpression();
      PyBinaryExpression expression = (PyBinaryExpression)statement.getAssignedValue();
      PyExpression leftExpression = expression.getLeftExpression();
      PyExpression rightExpression = expression.getRightExpression();
      if (rightExpression.getText().equals(target.getText())) {
        PyExpression tmp = rightExpression;
        rightExpression = leftExpression;
        leftExpression = tmp;
      }
      List<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(statement, PsiComment.class);
      
      if (leftExpression != null
          && (leftExpression instanceof PyReferenceExpression || leftExpression instanceof PySubscriptionExpression)) {
        if (leftExpression.getText().equals(target.getText())) {
          if (rightExpression instanceof PyNumericLiteralExpression || rightExpression instanceof PyStringLiteralExpression
            || rightExpression instanceof PyReferenceExpression || isPercentage(rightExpression) || isCompound(rightExpression)
            || isMathOperation(rightExpression, expression.getOperator())) {

            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(target.getText()).append(" ").
                append(expression.getPsiOperator().getText()).append("= ").append(rightExpression.getText());
            PyAugAssignmentStatementImpl augAssignment = elementGenerator.createFromText(LanguageLevel.forElement(element),
                                                          PyAugAssignmentStatementImpl.class, stringBuilder.toString());
            for (PsiComment comment : comments)
              augAssignment.add(comment);
            statement.replace(augAssignment);
          }
        }
      }
    }
  }

  private boolean isCompound(PyExpression rightExpression) {
    if (rightExpression instanceof PyCallExpression) {
      PyType type = rightExpression.getType(TypeEvalContext.fast());
      if (type != null && type.isBuiltin(TypeEvalContext.fast()) &&
          ("int".equals(type.getName()) || "str".equals(type.getName()))) return true;
    }
    return false;
  }

  private boolean isPercentage(PyExpression rightExpression) {
    return (rightExpression instanceof PyBinaryExpression &&
              ((PyBinaryExpression)rightExpression).getLeftExpression() instanceof PyStringLiteralExpression &&
              ((PyBinaryExpression)rightExpression).getOperator() == PyTokenTypes.PERC);
  }

  private boolean isMathOperation(PyExpression rightExpression, PyElementType mainOperator) {
    TokenSet first = TokenSet.create(PyTokenTypes.EXP, PyTokenTypes.FLOORDIV);
    TokenSet second = TokenSet.create(PyTokenTypes.MULT, PyTokenTypes.DIV, PyTokenTypes.PERC);
    TokenSet third = TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MINUS);
    if (rightExpression instanceof PyBinaryExpression){
      PyElementType operator = ((PyBinaryExpression)rightExpression).getOperator();
      if (third.contains(mainOperator) && (second.contains(operator) || first.contains(operator)))
        return true;
      else if (second.contains(mainOperator) && first.contains(operator))
        return true;
    }
    return false;
  }
}
