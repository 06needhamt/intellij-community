/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Mar 24, 2002
 * Time: 6:08:14 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.redundantCast;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RedundantCastUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.redundantCast.RedundantCastUtil");

  public static List<PsiTypeCastExpression> getRedundantCastsInside(PsiElement where) {
    final ArrayList<PsiTypeCastExpression> result = new ArrayList<PsiTypeCastExpression>();
    PsiElementProcessor<PsiTypeCastExpression> processor = new PsiElementProcessor<PsiTypeCastExpression>() {
      public boolean execute(PsiTypeCastExpression element) {
        result.add(element);
        return true;
      }
    };
    where.acceptChildren(new MyCollectingVisitor(processor));
    return result;
  }

  public static boolean isCastRedundant (PsiTypeCastExpression typeCast) {
    PsiElement parent = typeCast.getParent();
    while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();
    if (parent instanceof PsiExpressionList) parent = parent.getParent();
    if (parent instanceof PsiReferenceExpression) parent = parent.getParent();
    MyIsRedundantVisitor visitor = new MyIsRedundantVisitor();
    parent.accept(visitor);
    return visitor.isRedundant();
  }

  private static PsiExpression deparenthesizeExpression(PsiExpression arg) {
    while (arg instanceof PsiParenthesizedExpression) arg = ((PsiParenthesizedExpression) arg).getExpression();
    return arg;
  }

  private static class MyCollectingVisitor extends MyIsRedundantVisitor {
    private final PsiElementProcessor<PsiTypeCastExpression> myProcessor;
    private Set<PsiTypeCastExpression> myFoundCasts = new HashSet<PsiTypeCastExpression>();
    public MyCollectingVisitor(PsiElementProcessor<PsiTypeCastExpression> processor) {
      myProcessor = processor;
    }

    public void visitElement(PsiElement element) {
      element.acceptChildren(this);
    }

    public void visitClass(PsiClass aClass) {
      // avoid multiple visit
    }

    public void visitMethod(PsiMethod method) {
      // avoid multiple visit
    }

    public void visitField(PsiField field) {
      // avoid multiple visit
    }

    protected void addToResults(PsiTypeCastExpression typeCast){
      if (!isTypeCastSemantical(typeCast) && myFoundCasts.add(typeCast)) {
        myProcessor.execute(typeCast);
      }
    }

    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      expression.acceptChildren(this);
    }

    public void visitVariable(PsiVariable variable) {
      super.visitVariable(variable);
      variable.acceptChildren(this);
    }

    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      expression.acceptChildren(this);
    }

    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      expression.acceptChildren(this);
    }

    public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
      super.visitTypeCastExpression(typeCast);
      typeCast.acceptChildren(this);
    }
  }

  private static class MyIsRedundantVisitor extends PsiElementVisitor {
    public boolean isRedundant() {
      return myIsRedundant;
    }

    boolean myIsRedundant = false;
    protected void addToResults(PsiTypeCastExpression typeCast){
      if (!isTypeCastSemantical(typeCast)) {
        myIsRedundant = true;
      }
    }

    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      // Do not go inside conditional expression because branches are required to be exactly the same type, not assignable.
    }

    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
      processPossibleTypeCast(expression.getRExpression(), expression.getLExpression().getType());
    }

    public void visitVariable(PsiVariable variable) {
      processPossibleTypeCast(variable.getInitializer(), variable.getType());
    }

    public void visitBinaryExpression(PsiBinaryExpression expression) {
      PsiExpression rExpr = deparenthesizeExpression(expression.getLOperand());
      PsiExpression lExpr = deparenthesizeExpression(expression.getROperand());

      if (rExpr != null && lExpr != null) {
        final IElementType binaryToken = expression.getOperationSign().getTokenType();
        processBinaryExpressionOperand(lExpr, rExpr, binaryToken);
        processBinaryExpressionOperand(rExpr, lExpr, binaryToken);
      }
    }

    private void processBinaryExpressionOperand(final PsiExpression operand,
                                                final PsiExpression otherOperand,
                                                final IElementType binaryToken) {
      if (operand instanceof PsiTypeCastExpression) {
        PsiTypeCastExpression typeCast = (PsiTypeCastExpression)operand;
        PsiExpression castOperand = typeCast.getOperand();
        if (castOperand != null) {
          if (TypeConversionUtil.isBinaryOperatorApplicable(binaryToken, castOperand, otherOperand, false)) {
            addToResults(typeCast);
          }
        }
      }
    }

    private void processPossibleTypeCast(PsiExpression rExpr, PsiType lType) {
      rExpr = deparenthesizeExpression(rExpr);
      if (rExpr instanceof PsiTypeCastExpression) {
        PsiExpression castOperand = ((PsiTypeCastExpression)rExpr).getOperand();
        if (castOperand != null) {
          PsiType operandType = castOperand.getType();
          if (operandType != null) {
            if (lType != null && TypeConversionUtil.isAssignable(lType, operandType, false)) {
              addToResults((PsiTypeCastExpression)rExpr);
            }
          }
        }
      }
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      processCall(expression);

      checkForVirtual(expression);
    }

    private void checkForVirtual(PsiMethodCallExpression methodCall) {
      PsiReferenceExpression methodExpr = methodCall.getMethodExpression();
      PsiExpression qualifier = methodExpr.getQualifierExpression();
      try {
        if (!(qualifier instanceof PsiParenthesizedExpression)) return;
        PsiExpression operand = ((PsiParenthesizedExpression)qualifier).getExpression();
        if (!(operand instanceof PsiTypeCastExpression)) return;
        PsiTypeCastExpression typeCast = (PsiTypeCastExpression)operand;
        PsiExpression castOperand = typeCast.getOperand();
        if (castOperand == null) return;

        PsiType type = castOperand.getType();
        if (type == null) return;
        if (type instanceof PsiPrimitiveType) return;

        final JavaResolveResult resolveResult = methodExpr.advancedResolve(false);
        PsiMethod targetMethod = (PsiMethod)resolveResult.getElement();
        if (targetMethod == null) return;
        if (targetMethod.hasModifierProperty(PsiModifier.STATIC)) return;

        try {
          PsiManager manager = methodExpr.getManager();
          PsiElementFactory factory = manager.getElementFactory();

          PsiMethodCallExpression newCall = (PsiMethodCallExpression)factory.createExpressionFromText(methodCall.getText(), methodCall);
          PsiExpression newQualifier = newCall.getMethodExpression().getQualifierExpression();
          PsiExpression newOperand = ((PsiTypeCastExpression)((PsiParenthesizedExpression)newQualifier).getExpression()).getOperand();
          newQualifier.replace(newOperand);

          final JavaResolveResult newResult = newCall.getMethodExpression().advancedResolve(false);
          if (!newResult.isValidResult()) return;
          final PsiMethod newTargetMethod = (PsiMethod)newResult.getElement();
          final PsiType newReturnType = newResult.getSubstitutor().substitute(newTargetMethod.getReturnType());
          final PsiType oldReturnType = resolveResult.getSubstitutor().substitute(targetMethod.getReturnType());
          if (newReturnType.equals(oldReturnType)) {
            if (newTargetMethod.equals(targetMethod)) {
                addToResults(typeCast);
            } else if (newTargetMethod.getSignature(newResult.getSubstitutor()).equals(targetMethod.getSignature(resolveResult.getSubstitutor())) &&
                       !(newTargetMethod.isDeprecated() && !targetMethod.isDeprecated())) { // see SCR11555, SCR14559
              addToResults(typeCast);
            }
          }
          qualifier = ((PsiTypeCastExpression) ((PsiParenthesizedExpression) qualifier).getExpression()).getOperand();
        }
        catch (IncorrectOperationException e) {
        }
      }
      finally {
        if (qualifier != null) {
          qualifier.accept(this);
        }
      }
    }

    public void visitNewExpression(PsiNewExpression expression) {
      processCall(expression);
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) { }

    private void processCall(PsiCallExpression expression){
      PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) return;
      PsiExpression[] args = argumentList.getExpressions();
      PsiMethod oldMethod = expression.resolveMethod();
      if (oldMethod == null) return;
      PsiParameter[] parameters = oldMethod.getParameterList().getParameters();

      try {
        for (int i = 0; i < args.length; i++) {
          final PsiExpression arg = deparenthesizeExpression(args[i]);
          if (arg instanceof PsiTypeCastExpression) {
            PsiTypeCastExpression cast = ((PsiTypeCastExpression) arg);
            if (i == args.length - 1 && args.length == parameters.length && parameters[i].isVarArgs() &&
                PsiType.NULL.equals(cast.getOperand().getType())) {
              //do not mark cast from null to resolve ambiguity for calling varargs method with incomplete argument list
              continue;
            }
            PsiCallExpression newCall = (PsiCallExpression) expression.copy();
            final PsiExpressionList argList = newCall.getArgumentList();
            LOG.assertTrue(argList != null);
            PsiExpression[] newArgs = argList.getExpressions();
            PsiTypeCastExpression castExpression = (PsiTypeCastExpression) deparenthesizeExpression(newArgs[i]);
            PsiExpression castOperand = castExpression.getOperand();
            if (castOperand == null) return;
            castExpression.replace(castOperand);
            final JavaResolveResult newResult = newCall.resolveMethodGenerics();
            if (oldMethod.equals(newResult.getElement()) && newResult.isValidResult() &&
                Comparing.equal(newCall.getType(), expression.getType())) {
              addToResults(cast);
            }
          }
        }
      }
      catch (IncorrectOperationException e) {
        return;
      }

      for (PsiExpression arg : args) {
        if (arg instanceof PsiTypeCastExpression) {
          PsiExpression castOperand = ((PsiTypeCastExpression)arg).getOperand();
          castOperand.accept(this);
        }
        else {
          arg.accept(this);
        }
      }
    }

    public void visitTypeCastExpression(PsiTypeCastExpression typeCast) {
      PsiExpression operand = typeCast.getOperand();
      if (operand == null) return;

      PsiElement expr = deparenthesizeExpression(operand);

      if (expr instanceof PsiTypeCastExpression) {
        PsiTypeElement typeElement = ((PsiTypeCastExpression)expr).getCastType();
        if (typeElement == null) return;
        PsiType castType = typeElement.getType();
        if (!(castType instanceof PsiPrimitiveType)) {
          addToResults((PsiTypeCastExpression)expr);
        }
      }
      else {
        PsiElement parent = typeCast.getParent();
        if (parent instanceof PsiConditionalExpression) {
          if (PsiUtil.getLanguageLevel(typeCast).compareTo(LanguageLevel.JDK_1_5) < 0) {
            //branches need to be of the same type
            if (!Comparing.equal(operand.getType(),((PsiConditionalExpression)parent).getType())) return;
          }
        }
        processAlreadyHasTypeCast(typeCast);
      }
    }

    private void processAlreadyHasTypeCast(PsiTypeCastExpression typeCast){
      PsiElement parent = typeCast.getParent();
      while(parent instanceof PsiParenthesizedExpression) parent = parent.getParent();
      if (parent instanceof PsiExpressionList) return; // do not replace in arg lists - should be handled by parent

      if (isTypeCastSemantical(typeCast)) return;

      PsiTypeElement typeElement = typeCast.getCastType();
      if (typeElement == null) return;
      PsiType toType = typeElement.getType();
      PsiType fromType = typeCast.getOperand().getType();
      if (fromType == null) return;
      if (parent instanceof PsiReferenceExpression) {
        if (toType instanceof PsiClassType && fromType instanceof PsiPrimitiveType) return; //explicit boxing
        //Check accessibility
        if (fromType instanceof PsiClassType) {
          final PsiReferenceExpression refExpression = (PsiReferenceExpression)parent;
          PsiElement element = refExpression.resolve();
          if (!(element instanceof PsiMember)) return;
          PsiClass accessClass = ((PsiClassType)fromType).resolve();
          if (accessClass == null) return;
          if (!parent.getManager().getResolveHelper().isAccessible((PsiMember)element, typeCast, accessClass)) return;
          if (!isCastRedundantInRefExpression(refExpression, typeCast.getOperand())) return;
        }
      }

      if (TypeConversionUtil.isAssignable(toType, fromType, false)) {
        addToResults(typeCast);
      }
    }
  }

  private static boolean isCastRedundantInRefExpression (final PsiReferenceExpression refExpression, final PsiExpression castOperand) {
    final PsiElement resolved = refExpression.resolve();
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.FALSE);
    refExpression.getManager().performActionWithFormatterDisabled(new Runnable() {
      public void run() {
        try {
          final PsiElementFactory elementFactory = refExpression.getManager().getElementFactory();
          final PsiExpression copyExpression = elementFactory.createExpressionFromText(refExpression.getText(), refExpression);
          if (copyExpression instanceof PsiReferenceExpression) {
            final PsiReferenceExpression copy = (PsiReferenceExpression)copyExpression;
            final PsiExpression qualifier = copy.getQualifierExpression();
            if (qualifier != null) {
              qualifier.replace(castOperand);
              result.set(Boolean.valueOf(copy.resolve() == resolved));
            }
          }
        }
        catch (IncorrectOperationException e) {
        }
      }
    });
    return result.get().booleanValue();
  }

  public static boolean isTypeCastSemantical(PsiTypeCastExpression typeCast) {
    PsiExpression operand = typeCast.getOperand();
    if (operand != null) {
      PsiType opType = operand.getType();
      PsiTypeElement typeElement = typeCast.getCastType();
      if (typeElement == null) return false;
      PsiType castType = typeElement.getType();
      if (castType instanceof PsiPrimitiveType) {
        if (opType instanceof PsiPrimitiveType) {
          return !opType.equals(castType); // let's suppose all not equal primitive casts are necessary
        }
      }
      else if (castType instanceof PsiClassType && ((PsiClassType)castType).hasParameters()) {
        if (opType instanceof PsiClassType && ((PsiClassType)opType).isRaw()) return true;
      }
    }

    return false;
  }
}