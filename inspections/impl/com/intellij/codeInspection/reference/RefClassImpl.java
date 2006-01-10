/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:29:19 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.execution.junit.JUnitUtil;
import com.intellij.j2ee.ejb.EjbRolesUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.ejb.role.EjbClassRoleEnum;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RefClassImpl extends RefElementImpl implements RefClass {
  private static final int IS_ANONYMOUS_MASK = 0x10000;
  private static final int IS_INTERFACE_MASK = 0x20000;
  private static final int IS_UTILITY_MASK   = 0x40000;
  private static final int IS_ABSTRACT_MASK  = 0x80000;
  private static final int IS_EJB_MASK       = 0x100000;
  private static final int IS_APPLET_MASK    = 0x200000;
  private static final int IS_SERVLET_MASK   = 0x400000;
  private static final int IS_TESTCASE_MASK  = 0x800000;
  private static final int IS_LOCAL_MASK     = 0x1000000;

  private HashSet<RefClass> myBases;
  private HashSet<RefClass> mySubClasses;
  private ArrayList<RefMethod> myConstructors;
  private RefMethodImpl myDefaultConstructor;
  private ArrayList<RefMethod> myOverridingMethods;
  private HashSet<RefElement> myInTypeReferences;
  private HashSet<RefElement> myInstanceReferences;
  private ArrayList<RefElement> myClassExporters;

  RefClassImpl(PsiClass psiClass, RefManager manager) {
    super(psiClass, manager);
  }

  protected void initialize() {
    myConstructors = new ArrayList<RefMethod>(1);
    mySubClasses = new HashSet<RefClass>(0);
    myBases = new HashSet<RefClass>(0);
    myOverridingMethods = new ArrayList<RefMethod>(2);
    myInTypeReferences = new HashSet<RefElement>(0);
    myInstanceReferences = new HashSet<RefElement>(0);
    myDefaultConstructor = null;

    final PsiClass psiClass = (PsiClass)getElement();

    PsiElement psiParent = psiClass.getParent();
    if (psiParent instanceof PsiFile) {
      PsiJavaFile psiFile = (PsiJavaFile) psiParent;
      String packageName = psiFile.getPackageName();
      if (!"".equals(packageName)) {
        ((RefPackageImpl)getRefManager().getPackage(packageName)).add(this);
      } else {
        ((RefPackageImpl)getRefManager().getRefProject().getDefaultPackage()).add(this);
      }
      final Module module = ModuleUtil.findModuleForPsiElement(psiClass);
      LOG.assertTrue(module != null);
      ((RefModuleImpl)getRefManager().getRefModule(module)).add(this);
    } else {
      while (!(psiParent instanceof PsiClass || psiParent instanceof PsiMethod || psiParent instanceof PsiField)) {
        psiParent = psiParent.getParent();
      }
      RefElement refParent = getRefManager().getReference(psiParent);
      LOG.assertTrue (refParent != null);
      ((RefElementImpl)refParent).add(this);

    }

    setAbstract(psiClass.hasModifierProperty(PsiModifier.ABSTRACT));

    setAnonymous(psiClass instanceof PsiAnonymousClass);
    setIsLocal(!(isAnonymous() || psiParent instanceof PsiClass || psiParent instanceof PsiFile));
    setInterface(psiClass.isInterface());

    initializeSuperReferences(psiClass);

    PsiMethod[] psiMethods = psiClass.getMethods();
    PsiField[] psiFields = psiClass.getFields();

    setUtilityClass(psiMethods.length > 0 || psiFields.length > 0);

    for (PsiField psiField : psiFields) {
      ((RefManagerImpl)getRefManager()).getFieldReference(this, psiField);
    }

    if (!isApplet()) {
      final PsiClass servlet = ((RefManagerImpl)getRefManager()).getServlet();
      setServlet(servlet != null && psiClass.isInheritor(servlet, true));
    }
    if (!isApplet() && !isServlet()) {
      setTestCase(JUnitUtil.isTestCaseClass(psiClass));
      for (RefClass refBase : getBaseClasses()) {
        ((RefClassImpl)refBase).setTestCase(true);
      }
    }

    for (PsiMethod psiMethod : psiMethods) {
      RefMethod refMethod = ((RefManagerImpl)getRefManager()).getMethodReference(this, psiMethod);

      if (refMethod != null) {
        if (psiMethod.isConstructor()) {
          if (psiMethod.getParameterList().getParameters().length > 0 || !psiMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
            setUtilityClass(false);
          }

          addConstructor(refMethod);
          if (psiMethod.getParameterList().getParameters().length == 0) {
            setDefaultConstructor((RefMethodImpl)refMethod);
          }
        }
        else {
          if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
            setUtilityClass(false);
          }
        }
      }
    }

    if (myConstructors.size() == 0 && !isInterface() && !isAnonymous()) {
      RefImplicitConstructorImpl refImplicitConstructor = new RefImplicitConstructorImpl(this);
      setDefaultConstructor(refImplicitConstructor);
      addConstructor(refImplicitConstructor);
    }

    if (isInterface()) {
      for (int i = 0; i < psiFields.length && isUtilityClass(); i++) {
        PsiField psiField = psiFields[i];
        if (!psiField.hasModifierProperty(PsiModifier.STATIC)) {
          setUtilityClass(false);
        }
      }
    }


    final PsiClass applet = ((RefManagerImpl)getRefManager()).getApplet();
    setApplet(applet != null && psiClass.isInheritor(applet, true));
    ((RefManagerImpl)getRefManager()).fireNodeInitialized(this);
  }

  private void initializeSuperReferences(PsiClass psiClass) {
    if (!isSelfInheritor(psiClass)) {
      for (PsiClass psiSuperClass : psiClass.getSupers()) {
        if (RefUtil.getInstance().belongsToScope(psiSuperClass, getRefManager())) {
          RefClass refClass = (RefClass)getRefManager().getReference(psiSuperClass);
          if (refClass != null) {
            myBases.add(refClass);
            refClass.getSubClasses().add(this);
          }
        }
      }
    }
  }

  public boolean isSelfInheritor(PsiClass psiClass) {
    return isSelfInheritor(psiClass, new ArrayList<PsiClass>());
  }

  private static boolean isSelfInheritor(PsiClass psiClass, ArrayList<PsiClass> visited) {
    if (visited.contains(psiClass)) return true;

    visited.add(psiClass);
    for (PsiClass aSuper : psiClass.getSupers()) {
      if (isSelfInheritor(aSuper, visited)) return true;
    }
    visited.remove(psiClass);

    return false;
  }

  private void setDefaultConstructor(RefMethodImpl defaultConstructor) {
    if (defaultConstructor != null) {
      for (RefClass superClass : getBaseClasses()) {
        RefMethodImpl superDefaultConstructor = (RefMethodImpl)superClass.getDefaultConstructor();

        if (superDefaultConstructor != null) {
          superDefaultConstructor.addInReference(defaultConstructor);
          defaultConstructor.addOutReference(superDefaultConstructor);
        }
      }
    }

    myDefaultConstructor = defaultConstructor;
  }

  public void buildReferences() {
    PsiClass psiClass = (PsiClass) getElement();

    if (psiClass != null) {
      for (PsiClassInitializer classInitializer : psiClass.getInitializers()) {
        ((RefUtilImpl)RefUtil.getInstance()).addReferences(psiClass, this, classInitializer.getBody());
      }

      PsiField[] psiFields = psiClass.getFields();
      for (PsiField psiField : psiFields) {
        ((RefManagerImpl)getRefManager()).getFieldReference(this, psiField);
      }

      PsiMethod[] psiMethods = psiClass.getMethods();
      for (PsiMethod psiMethod : psiMethods) {
        ((RefManagerImpl)getRefManager()).getMethodReference(this, psiMethod);
      }

      EjbClassRole role = EjbRolesUtil.getEjbRole(psiClass);
      if (role != null) {
        setEjb(true);
        if (role.getType() == EjbClassRoleEnum.EJB_CLASS_ROLE_HOME_INTERFACE ||
            role.getType() == EjbClassRoleEnum.EJB_CLASS_ROLE_REMOTE_INTERFACE) {
          PsiClassType remoteExceptionType = psiClass.getManager().getElementFactory().createTypeByFQClassName("java.rmi.RemoteException", psiClass.getResolveScope());
          for (PsiMethod psiMethod : psiClass.getAllMethods()) {
            if (!RefUtil.getInstance().belongsToScope(psiMethod, getRefManager())) continue;
            RefMethodImpl refMethod = (RefMethodImpl)((RefManagerImpl)getRefManager()).getMethodReference(this, psiMethod);
            if (refMethod != null) {
              refMethod.updateThrowsList(remoteExceptionType);
            }
          }
        }
      }
      ((RefManagerImpl)getRefManager()).fireBuildReferences(this);
    }
  }

  public void accept(RefVisitor visitor) {
    visitor.visitClass(this);
  }

  public HashSet<RefClass> getBaseClasses() {
    return myBases;
  }

  public HashSet<RefClass> getSubClasses() {
    return mySubClasses;
  }

  public ArrayList<RefMethod> getConstructors() {
    return myConstructors;
  }

  public Set<RefElement> getInTypeReferences() {
    return myInTypeReferences;
  }

  public void addTypeReference(RefElement from) {
    if (from != null) {
      myInTypeReferences.add(from);
      ((RefManagerImpl)getRefManager()).fireNodeMarkedReferenced(this, from, false);
    }
  }

  public Set<RefElement> getInstanceReferences() {
    return myInstanceReferences;
  }

  public void addInstanceReference(RefElement from) {
    myInstanceReferences.add(from);
  }

  public RefMethod getDefaultConstructor() {
    return myDefaultConstructor;
  }

  private void addConstructor(RefMethod refConstructor) {
    myConstructors.add(refConstructor);
  }

  public void addLibraryOverrideMethod(RefMethod refMethod) {
    myOverridingMethods.add(refMethod);
  }

  public List<RefMethod> getLibraryMethods() {
    return myOverridingMethods;
  }

  public boolean isAnonymous() {
    return checkFlag(IS_ANONYMOUS_MASK);
  }

  public boolean isInterface() {
    return checkFlag(IS_INTERFACE_MASK);
  }

  public boolean isSuspicious() {
    if (isUtilityClass() && getOutReferences().isEmpty()) return false;
    return super.isSuspicious();
  }

  public boolean isUtilityClass() {
    return checkFlag(IS_UTILITY_MASK);
  }

  public String getExternalName() {
    final String[] result = new String[1];
    final Runnable runnable = new Runnable() {
      public void run() {
        PsiClass psiClass = (PsiClass) getElement();
        result[0] = PsiFormatUtil.formatClass(psiClass, PsiFormatUtil.SHOW_NAME |
          PsiFormatUtil.SHOW_FQ_NAME);
      }
    };

    ApplicationManager.getApplication().runReadAction(runnable);

    return result[0];
  }

  public static RefClass classFromExternalName(RefManager manager, String externalName) {
    PsiClass psiClass = PsiManager.getInstance(manager.getProject()).findClass(externalName);
    RefClass refClass = null;

    if (psiClass != null) {
        refClass = (RefClass) manager.getReference(psiClass);
    }

    return refClass;
  }

  public void referenceRemoved() {
    super.referenceRemoved();

    for (RefClass subClass : getSubClasses()) {
      ((RefClassImpl)subClass).removeBase(this);
    }

    for (RefClass superClass : getBaseClasses()) {
      superClass.getSubClasses().remove(this);
    }
  }

  private void removeBase(RefClass superClass) {
    getBaseClasses().remove(superClass);
  }

  protected void methodRemoved(RefMethod method) {
    getConstructors().remove(method);
    getLibraryMethods().remove(method);

    if (getDefaultConstructor() == method) {
      setDefaultConstructor(null);
    }
  }

  public boolean isAbstract() {
    return checkFlag(IS_ABSTRACT_MASK);
  }

  public boolean isEjb() {
    return checkFlag(IS_EJB_MASK);
  }

  public boolean isApplet() {
    return checkFlag(IS_APPLET_MASK);
  }

  public boolean isServlet() {
    return checkFlag(IS_SERVLET_MASK);
  }

  public boolean isTestCase() {
    return checkFlag(IS_TESTCASE_MASK);
  }

  public boolean isLocalClass() {
    return checkFlag(IS_LOCAL_MASK);
  }

 
  public boolean isReferenced() {
    if (super.isReferenced()) return true;

    if (isInterface() || isAbstract()) {
      if (getSubClasses().size() > 0) return true;
    }

    return false;
  }

  public boolean hasSuspiciousCallers() {
    if (super.hasSuspiciousCallers()) return true;

    if (isInterface() || isAbstract()) {
      if (getSubClasses().size() > 0) return true;
    }

    return false;
  }

  public void addClassExporter(RefElement exporter) {
    if (myClassExporters == null) myClassExporters = new ArrayList<RefElement>(1);
    if (myClassExporters.contains(exporter)) return;
    myClassExporters.add(exporter);
  }

  public List<RefElement> getClassExporters() {
    return myClassExporters;
  }

  private void setAnonymous(boolean anonymous) {
    setFlag(anonymous, IS_ANONYMOUS_MASK);
  }

  private void setInterface(boolean anInterface) {
    setFlag(anInterface, IS_INTERFACE_MASK);
  }

  private void setUtilityClass(boolean utilityClass) {
    setFlag(utilityClass, IS_UTILITY_MASK);
  }

  private void setAbstract(boolean anAbstract) {
    setFlag(anAbstract, IS_ABSTRACT_MASK);
  }

  private void setEjb(boolean ejb) {
    setFlag(ejb, IS_EJB_MASK);
  }

  private void setApplet(boolean applet) {
    setFlag(applet, IS_APPLET_MASK);
  }

  private void setServlet(boolean servlet) {
    setFlag(servlet, IS_SERVLET_MASK);
  }

  private void setTestCase(boolean testCase) {
    setFlag(testCase, IS_TESTCASE_MASK);
  }

  public void setIsLocal(boolean isLocal) {
    setFlag(isLocal, IS_LOCAL_MASK);
  }
}

