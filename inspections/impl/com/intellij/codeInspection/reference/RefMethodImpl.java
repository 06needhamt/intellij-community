/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:29:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public class RefMethodImpl extends RefElementImpl implements RefMethod {
  private static final ArrayList<RefMethod> EMPTY_METHOD_LIST = new ArrayList<RefMethod>(0);
  private static final RefParameter[] EMPTY_PARAMS_ARRAY = new RefParameter[0];

  private static final int IS_APPMAIN_MASK = 0x10000;
  private static final int IS_LIBRARY_OVERRIDE_MASK = 0x20000;
  private static final int IS_CONSTRUCTOR_MASK = 0x40000;
  private static final int IS_ABSTRACT_MASK = 0x80000;
  private static final int IS_BODY_EMPTY_MASK = 0x100000;
  private static final int IS_ONLY_CALLS_SUPER_MASK = 0x200000;
  private static final int IS_RETURN_VALUE_USED_MASK = 0x400000;

  private static final int IS_TEST_METHOD_MASK = 0x4000000;
  private static final int IS_CALLED_ON_SUBCLASS = 0x8000000;

  private static final String RETURN_VALUE_UNDEFINED = "#";

  private ArrayList<RefMethod> mySuperMethods;
  private ArrayList<RefMethod> myDerivedMethods;
  private ArrayList<PsiClass> myUnThrownExceptions;

  private RefParameter[] myParameters;
  private String myReturnValueTemplate;
  protected final RefClass myOwnerClass;

  RefMethodImpl(PsiMethod method, RefManager manager) {
      this((RefClass) manager.getReference(method.getContainingClass()), method,  manager);
  }

  RefMethodImpl(RefClass ownerClass, PsiMethod method, RefManager manager) {
    super(method, manager);

    ((RefClassImpl)ownerClass).add(this);

    myOwnerClass = ownerClass;
  }

  protected void initialize() {
    final PsiMethod method = (PsiMethod)getElement();
    LOG.assertTrue(method != null);
    setConstructor(method.isConstructor());
    setFlag(method.getReturnType() == null || PsiType.VOID == method.getReturnType(), IS_RETURN_VALUE_USED_MASK);

    if (!isReturnValueUsed()) {
      myReturnValueTemplate = RETURN_VALUE_UNDEFINED;
    }

    if (isConstructor()) {
      addReference(getOwnerClass(), getOwnerClass().getElement(), method, false, true, null);
    }

    if (getOwnerClass().isInterface()) {
      setAbstract(false);
    } else {
      setAbstract(method.hasModifierProperty(PsiModifier.ABSTRACT));
    }


    setAppMain(isAppMain(method, this));
    setLibraryOverride(method.hasModifierProperty(PsiModifier.NATIVE));

    initializeSuperMethods(method);
    if (isExternalOverride()) {
      ((RefClassImpl)getOwnerClass()).addLibraryOverrideMethod(this);
    }

    @NonNls final String name = method.getName();
    if (getOwnerClass().isTestCase() && name.startsWith("test")) {
      setTestMethod(true);
    }

    PsiParameter[] paramList = method.getParameterList().getParameters();
    if (paramList.length > 0){
      myParameters = new RefParameterImpl[paramList.length];
      for (int i = 0; i < paramList.length; i++) {
        PsiParameter parameter = paramList[i];
        myParameters[i] = getRefManager().getParameterReference(parameter, i);
      }
    }

    if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      updateReturnValueTemplate(null);
      updateThrowsList(null);
    }
    collectUncaughtExceptions(method);
    ((RefManagerImpl)getRefManager()).fireNodeInitialized(this);
  }

  private static boolean isAppMain(PsiMethod psiMethod, RefMethod refMethod) {
    if (!refMethod.isStatic()) return false;
    if (!PsiType.VOID.equals(psiMethod.getReturnType())) return false;

    PsiMethod appMainPattern = ((RefManagerImpl)refMethod.getRefManager()).getAppMainPattern();
    if (MethodSignatureUtil.areSignaturesEqual(psiMethod, appMainPattern)) return true;

    PsiMethod appPremainPattern = ((RefManagerImpl)refMethod.getRefManager()).getAppPremainPattern();
    return MethodSignatureUtil.areSignaturesEqual(psiMethod, appPremainPattern);
  }

  private void checkForSuperCall(PsiMethod method) {
    if (isConstructor()) {
      boolean isBaseExplicitlyCalled = false;
      PsiCodeBlock body = method.getBody();
      if (body == null) return;
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiStatement first = statements[0];
        if (first instanceof PsiExpressionStatement) {
          PsiExpression firstExpression = ((PsiExpressionStatement) first).getExpression();
          if (firstExpression instanceof PsiMethodCallExpression) {
            PsiExpression qualifierExpression = ((PsiMethodCallExpression)firstExpression).getMethodExpression().getQualifierExpression();
            if (qualifierExpression instanceof PsiReferenceExpression) {
              @NonNls String text = qualifierExpression.getText();
              if ("super".equals(text) || text.equals("this")) {
                isBaseExplicitlyCalled = true;
              }
            }
          }
        }
      }

      if (!isBaseExplicitlyCalled) {
        for (RefClass superClass : getOwnerClass().getBaseClasses()) {
          RefMethodImpl superDefaultConstructor = (RefMethodImpl)superClass.getDefaultConstructor();

          if (superDefaultConstructor != null) {
            superDefaultConstructor.addInReference(this);
            addOutReference(superDefaultConstructor);
          }
        }
      }
    }
  }

  // To be used only from RefImplicitConstructor.
  protected RefMethodImpl(String name, RefClass ownerClass) {
    super(name, ownerClass);
    myOwnerClass = ownerClass;
    ((RefClassImpl)ownerClass).add(this);

    addOutReference(getOwnerClass());
    ((RefClassImpl)getOwnerClass()).addInReference(this);

    setConstructor(true);

  }

  @NotNull
  public Collection<RefMethod> getSuperMethods() {
    if (mySuperMethods == null) return EMPTY_METHOD_LIST;
    return mySuperMethods;
  }

  @NotNull
  public Collection<RefMethod> getDerivedMethods() {
    if (myDerivedMethods == null) return EMPTY_METHOD_LIST;
    return myDerivedMethods;
  }

  public boolean isBodyEmpty() {
    return checkFlag(IS_BODY_EMPTY_MASK);
  }

  public boolean isOnlyCallsSuper() {
    return checkFlag(IS_ONLY_CALLS_SUPER_MASK);
  }

  public boolean hasBody() {
    return !isAbstract() && !getOwnerClass().isInterface() || !isBodyEmpty();
  }

  private void initializeSuperMethods(PsiMethod method) {
    for (PsiMethod psiSuperMethod : method.findSuperMethods()) {
      if (RefUtil.getInstance().belongsToScope(psiSuperMethod, getRefManager())) {
        RefMethodImpl refSuperMethod = (RefMethodImpl)getRefManager().getReference(psiSuperMethod);
        if (refSuperMethod != null) {
          addSuperMethod(refSuperMethod);
          refSuperMethod.markExtended(this);
        }
      }
      else {
        setLibraryOverride(true);
      }
    }
  }

  public void addSuperMethod(RefMethodImpl refSuperMethod) {
    if (!getSuperMethods().contains(refSuperMethod)) {
      if (mySuperMethods == null){
        mySuperMethods = new ArrayList<RefMethod>(1);
      }
      mySuperMethods.add(refSuperMethod);
    }
  }

  public void markExtended(RefMethodImpl method) {
    if (!getDerivedMethods().contains(method)) {
      if (myDerivedMethods == null) {
        myDerivedMethods = new ArrayList<RefMethod>(1);
      }
      myDerivedMethods.add(method);
    }
  }

  @NotNull
  public RefParameter[] getParameters() {
    if (myParameters == null) return EMPTY_PARAMS_ARRAY;
    return myParameters;
  }

  public void buildReferences() {
    // Work on code block to find what we're referencing...
    PsiMethod method = (PsiMethod) getElement();
    if (method != null) {
      PsiCodeBlock body = method.getBody();
      final RefUtilImpl refUtil = (RefUtilImpl)RefUtil.getInstance();
      refUtil.addReferences(method, this, body);
      checkForSuperCall(method);
      setOnlyCallsSuper(refUtil.isMethodOnlyCallsSuper(method));

      setBodyEmpty(isOnlyCallsSuper() || !isExternalOverride() && (body == null || body.getStatements().length == 0));

      PsiType retType = method.getReturnType();
      if (retType != null) {
        PsiType psiType = retType;
        RefClass ownerClass = refUtil.getOwnerClass(getRefManager(), method);

        if (ownerClass != null) {
          psiType = psiType.getDeepComponentType();

          if (psiType instanceof PsiClassType) {
            PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
            if (psiClass != null && refUtil.belongsToScope(psiClass, getRefManager())) {
                RefClassImpl refClass = (RefClassImpl) getRefManager().getReference(psiClass);
              if (refClass != null) {
                refClass.addTypeReference(ownerClass);
                refClass.addClassExporter(this);
              }
            }
          }
        }
      }

      for (RefParameter parameter : getParameters()) {
        refUtil.setIsFinal(parameter, parameter.getElement().hasModifierProperty(PsiModifier.FINAL));
      }

      ((RefManagerImpl)getRefManager()).fireBuildReferences(this);
    }
  }

  private void collectUncaughtExceptions(PsiMethod method) {
    if (isExternalOverride()) return;
    @NonNls final String name = method.getName();
    if (getOwnerClass().isTestCase() && name.startsWith("test")) return;

    if (getSuperMethods().size() == 0) {
      PsiClassType[] throwsList = method.getThrowsList().getReferencedTypes();
      if (throwsList.length > 0) {
        myUnThrownExceptions = new ArrayList<PsiClass>(throwsList.length);
        for (final PsiClassType type : throwsList) {
          myUnThrownExceptions.add(type.resolve());
        }
      }
    }

    final PsiCodeBlock body = method.getBody();
    if (body == null) return;

    final PsiClassType[] exceptionTypes = ExceptionUtil.collectUnhandledExceptions(method, body, false);
    for (final PsiClassType exceptionType : exceptionTypes) {
      updateThrowsList(exceptionType);
    }
  }

  public void removeUnThrownExceptions(PsiClass unThrownException) {
    if (myUnThrownExceptions != null) {
      myUnThrownExceptions.remove(unThrownException);
    }
  }

  public void accept(RefVisitor visitor) {
    visitor.visitMethod(this);
  }

  public boolean isExternalOverride() {
    return isLibraryOverride(new HashSet<RefMethod>());
  }

  private boolean isLibraryOverride(Collection<RefMethod> processed) {
    if (processed.contains(this)) return false;
    processed.add(this);

    if (checkFlag(IS_LIBRARY_OVERRIDE_MASK)) return true;
    for (RefMethod superMethod : getSuperMethods()) {
      if (((RefMethodImpl)superMethod).isLibraryOverride(processed)) {
        setFlag(true, IS_LIBRARY_OVERRIDE_MASK);
        return true;
      }
    }

    return false;
  }

  public boolean isAppMain() {
    return checkFlag(IS_APPMAIN_MASK);
  }

  public boolean isAbstract() {
    return checkFlag(IS_ABSTRACT_MASK);
  }

  public boolean hasSuperMethods() {
    return getSuperMethods().size() > 0 || isLibraryOverride(new HashSet<RefMethod>());
  }

  public boolean isReferenced() {
    // Directly called from somewhere..
    for (RefElement refCaller : getInReferences()) {
      if (!getDerivedMethods().contains(refCaller)) return true;
    }

    // Library override probably called from library code.
    return isExternalOverride();
  }

  public boolean hasSuspiciousCallers() {
    // Directly called from somewhere..
    for (RefElement refCaller : getInReferences()) {
      if (((RefElementImpl)refCaller).isSuspicious() && !getDerivedMethods().contains(refCaller)) return true;
    }

    // Library override probably called from library code.
    if (isExternalOverride()) return true;

    // Class isn't instantiated. Most probably we have problem with class, not method.
    if (!isStatic() && !isConstructor()) {
      if (((RefClassImpl)getOwnerClass()).isSuspicious()) return true;

      // Is an override. Probably called via reference to base class.
      for (RefMethod refSuper : getSuperMethods()) {
        if (((RefMethodImpl)refSuper).isSuspicious()) return true;
      }
    }

    return false;
  }

  public boolean isConstructor() {
    return checkFlag(IS_CONSTRUCTOR_MASK);
  }

  public RefClass getOwnerClass() {
    return (RefClass) getOwner();
  }

  public String getName() {
    if (isValid()) {
      final String[] result = new String[1];
      final Runnable runnable = new Runnable() {
        public void run() {
          PsiMethod psiMethod = (PsiMethod) getElement();
          if (psiMethod instanceof JspHolderMethod) {
            result[0] = psiMethod.getName();
          }
          else {
            result[0] = PsiFormatUtil.formatMethod(psiMethod,
                                                   PsiSubstitutor.EMPTY,
                                                   PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
                                                   PsiFormatUtil.SHOW_TYPE
            );
          }
        }
      };

      ApplicationManager.getApplication().runReadAction(runnable);

      return result[0];
    } else {
      return super.getName();
    }
  }

  public String getExternalName() {
    final String[] result = new String[1];
    final Runnable runnable = new Runnable() {
      public void run() {
        final PsiMethod psiMethod = (PsiMethod)getElement();
        LOG.assertTrue(psiMethod != null);
        final RefClass refClass = (RefClass)getRefManager().getReference(psiMethod.getContainingClass());
        LOG.assertTrue(refClass != null);
        result[0] = refClass.getExternalName() + " " +
                    PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil
                      .SHOW_FQ_NAME | PsiFormatUtil
                      .SHOW_TYPE | PsiFormatUtil
                      .SHOW_PARAMETERS, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE);
      }
    };

    ApplicationManager.getApplication().runReadAction(runnable);

    return result[0];
  }

  @Nullable
  public static RefMethod methodFromExternalName(RefManager manager, String externalName) {
    return (RefMethod) manager.getReference(findPsiMethod(PsiManager.getInstance(manager.getProject()), externalName));
  }

  @Nullable
  public static PsiMethod findPsiMethod(PsiManager manager, String externalName) {
    final int spaceIdx = externalName.indexOf(' ');
    final String className = externalName.substring(0, spaceIdx);
    final PsiClass psiClass = RefClassImpl.findPsiClass(manager, className);
    if (psiClass == null) return null;
    try {
      PsiElementFactory factory = psiClass.getManager().getElementFactory();
      String methodSignature = externalName.substring(spaceIdx + 1);
      PsiMethod patternMethod = factory.createMethodFromText(methodSignature, psiClass);
      return psiClass.findMethodBySignature(patternMethod, false);
    } catch (IncorrectOperationException e) {
      // Do nothing. Returning null is acceptable in this case.
      return null;
    }
  }

  public void referenceRemoved() {
    if (getOwnerClass() != null) {
      ((RefClassImpl)getOwnerClass()).methodRemoved(this);
    }

    super.referenceRemoved();

    for (RefMethod superMethod : getSuperMethods()) {
      superMethod.getDerivedMethods().remove(this);
    }

    for (RefMethod subMethod : getDerivedMethods()) {
      subMethod.getSuperMethods().remove(this);
    }

    ArrayList<RefElement> deletedRefs = new ArrayList<RefElement>();
    for (RefParameter parameter : getParameters()) {
      RefUtil.getInstance().removeRefElement(parameter, deletedRefs);
    }
  }

  public boolean isSuspicious() {
    if (isConstructor() && PsiModifier.PRIVATE.equals(getAccessModifier()) && getParameters().length == 0 && getOwnerClass().getConstructors().size() == 1) return false;
    return super.isSuspicious();
  }

  public void setReturnValueUsed(boolean value) {
    if (checkFlag(IS_RETURN_VALUE_USED_MASK) == value) return;
    setFlag(value, IS_RETURN_VALUE_USED_MASK);
    for (RefMethod refSuper : getSuperMethods()) {
      ((RefMethodImpl)refSuper).setReturnValueUsed(value);
    }
  }

  public boolean isReturnValueUsed() {
    return checkFlag(IS_RETURN_VALUE_USED_MASK);
  }

  public void updateReturnValueTemplate(PsiExpression expression) {
    if (myReturnValueTemplate == null) return;

    if (getSuperMethods().size() > 0) {
      for (final RefMethod refMethod : getSuperMethods()) {
        RefMethodImpl refSuper = (RefMethodImpl)refMethod;
        refSuper.updateReturnValueTemplate(expression);
      }
    }else {
      String newTemplate = null;
      final RefUtilImpl refUtil = (RefUtilImpl)RefUtil.getInstance();
      if (expression instanceof PsiLiteralExpression) {
        PsiLiteralExpression psiLiteralExpression = (PsiLiteralExpression) expression;
        newTemplate = psiLiteralExpression.getText();
      } else if (expression instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiField) {
          PsiField psiField = (PsiField) resolved;
          if (psiField.hasModifierProperty(PsiModifier.STATIC) &&
              psiField.hasModifierProperty(PsiModifier.FINAL) &&
              refUtil.compareAccess(refUtil.getAccessModifier(psiField), getAccessModifier()) >= 0) {
            newTemplate = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
          }
        }
      } else if (RefUtilImpl.isCallToSuperMethod(expression, (PsiMethod) getElement())) return;

      //noinspection StringEquality
      if (myReturnValueTemplate == RETURN_VALUE_UNDEFINED) {
        myReturnValueTemplate = newTemplate;
      } else if (!Comparing.equal(myReturnValueTemplate, newTemplate)) {
        myReturnValueTemplate = null;
      }
    }
  }

  public void updateParameterValues(PsiExpression[] args) {
    if (isExternalOverride()) return;

    if (getSuperMethods().size() > 0) {
      for (RefMethod refSuper : getSuperMethods()) {
        ((RefMethodImpl)refSuper).updateParameterValues(args);
      }
    } else {
      final RefParameter[] params = getParameters();
      if (params.length == args.length) {
        for (int i = 0; i < params.length; i++) {
          RefParameter refParameter = params[i];
          ((RefParameterImpl)refParameter).updateTemplateValue(args[i]);
        }
      }
    }
  }

  public String getReturnValueIfSame() {
    //noinspection StringEquality
    if (myReturnValueTemplate == RETURN_VALUE_UNDEFINED) return null;
    return myReturnValueTemplate;
  }

  public void updateThrowsList(PsiClassType exceptionType) {
    if (getSuperMethods().size() > 0) {
      for (RefMethod refSuper : getSuperMethods()) {
        ((RefMethodImpl)refSuper).updateThrowsList(exceptionType);
      }
    } else if (myUnThrownExceptions != null) {
      if (exceptionType == null) {
        myUnThrownExceptions = null;
        return;
      }

      PsiClass[] arrayed = myUnThrownExceptions.toArray(new PsiClass[myUnThrownExceptions.size()]);
      for (int i = arrayed.length - 1; i >= 0; i--) {
        PsiClass classType = arrayed[i];
        if (InheritanceUtil.isInheritorOrSelf(exceptionType.resolve(), classType, true) ||
            InheritanceUtil.isInheritorOrSelf(classType, exceptionType.resolve(), true)) {
          myUnThrownExceptions.remove(i);
        }
      }

      if (myUnThrownExceptions.size() == 0) myUnThrownExceptions = null;
    }
  }

  @Nullable
  public PsiClass[] getUnThrownExceptions() {
    if (myUnThrownExceptions == null) return null;
    return myUnThrownExceptions.toArray(new PsiClass[myUnThrownExceptions.size()]);
  }


  public void setLibraryOverride(boolean libraryOverride) {
    setFlag(libraryOverride, IS_LIBRARY_OVERRIDE_MASK);
  }

  private void setAppMain(boolean appMain) {
    setFlag(appMain, IS_APPMAIN_MASK);
  }

  private void setAbstract(boolean anAbstract) {
    setFlag(anAbstract, IS_ABSTRACT_MASK);
  }

  public void setBodyEmpty(boolean bodyEmpty) {
    setFlag(bodyEmpty, IS_BODY_EMPTY_MASK);
  }

  private void setOnlyCallsSuper(boolean onlyCallsSuper) {
    setFlag(onlyCallsSuper, IS_ONLY_CALLS_SUPER_MASK);
  }



  private void setConstructor(boolean constructor) {
    setFlag(constructor, IS_CONSTRUCTOR_MASK);
  }

  public boolean isTestMethod() {
    return checkFlag(IS_TEST_METHOD_MASK);
  }

  private void setTestMethod(boolean testMethod){
    setFlag(testMethod, IS_TEST_METHOD_MASK);
  }

  public PsiModifierListOwner getElement() {
    return (PsiModifierListOwner)super.getElement();
  }

  public boolean isCalledOnSubClass() {
    return checkFlag(IS_CALLED_ON_SUBCLASS);
  }

  public void setCalledOnSubClass(boolean isCalledOnSubClass){
    setFlag(isCalledOnSubClass, IS_CALLED_ON_SUBCLASS);
  }

}
