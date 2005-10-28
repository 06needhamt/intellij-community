package com.intellij.psi.impl;

import com.intellij.ant.PsiAntElement;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.ide.IconUtilEx;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.jsp.WebDirectoryElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.RowIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.Icons;
import com.intellij.util.SmartList;
import gnu.trove.TIntObjectHashMap;

import javax.swing.*;
import java.util.List;

public abstract class ElementBase extends UserDataHolderBase implements Iconable {
  public Icon getIcon(int flags) {
    if (!(this instanceof PsiElement)) return null;
    final PsiElement element = (PsiElement)this;
    RowIcon baseIcon;
    final boolean isLocked = (flags & ICON_FLAG_READ_STATUS) != 0 && !element.isWritable();
    int elementFlags = isLocked ? FLAGS_LOCKED : 0;
    if (element instanceof PsiDirectory) {
      Icon symbolIcon;
      final PsiDirectory psiDirectory = (PsiDirectory)element;
      if (psiDirectory.getPackage() != null) {
        symbolIcon = Icons.PACKAGE_ICON;
      }
      else {
        symbolIcon = Icons.DIRECTORY_CLOSED_ICON;
      }
      final VirtualFile vFile = psiDirectory.getVirtualFile();
      final Project project = psiDirectory.getProject();
      boolean isExcluded = isExcluded(vFile, project);
      baseIcon = createLayeredIcon(symbolIcon, elementFlags | (isExcluded ? FLAGS_EXCLUDED : 0));
    }
    else if (element instanceof PsiPackage) {
      baseIcon = createLayeredIcon(Icons.PACKAGE_ICON, elementFlags);
    }
    else if (element instanceof PsiFile) {
      PsiFile file = (PsiFile)element;

      VirtualFile virtualFile = file.getVirtualFile();
      final Icon fileTypeIcon;
      if (virtualFile == null) {
        fileTypeIcon = file.getFileType().getIcon();
      }
      else {
        fileTypeIcon = IconUtil.getIcon(virtualFile, flags & ~ICON_FLAG_READ_STATUS, file.getProject());
      }
      baseIcon = createLayeredIcon(fileTypeIcon, elementFlags);
    }
    else if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      Icon symbolIcon = getClassBaseIcon(aClass);
      baseIcon = createLayeredIcon(symbolIcon, getFlags(aClass, isLocked));
    }
    else if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;
      final EjbMethodRole role = J2EERolesUtil.getEjbRole(method);
      Icon methodIcon;
      if (role == null) {
        methodIcon = method.hasModifierProperty(PsiModifier.ABSTRACT) ? Icons.ABSTRACT_METHOD_ICON : Icons.METHOD_ICON;
      }
      else {
        methodIcon = role.getIcon();
      }
      baseIcon = createLayeredIcon(methodIcon, getFlags(method, false));
    }
    else if (element instanceof PsiField) {
      baseIcon = createLayeredIcon(Icons.FIELD_ICON, getFlags((PsiField)element, false));
    }
    else if (element instanceof PsiParameter) {
      baseIcon = createLayeredIcon(Icons.PARAMETER_ICON, 0);
    }
    else if (element instanceof PsiVariable) {
      baseIcon = createLayeredIcon(Icons.VARIABLE_ICON, getFlags((PsiVariable)element, false));
    }
    else if (element instanceof XmlTag) {
      return Icons.XML_TAG_ICON;
    }
    else if (element instanceof PsiAntElement) {
      return ((PsiAntElement)element).getRole().getIcon();
    } else if (element instanceof WebDirectoryElement) {
      return Icons.DIRECTORY_CLOSED_ICON;
    }
    else {
      return null;
    }
    if ((flags & ICON_FLAG_VISIBILITY) != 0) {
      PsiModifierList modifierList = element instanceof PsiModifierListOwner ? ((PsiModifierListOwner)element).getModifierList() : null;
      IconUtilEx.setVisibilityIcon(modifierList, baseIcon);
    }
    return baseIcon;
  }

  private static boolean isExcluded(final VirtualFile vFile, final Project project) {
    if (vFile != null) {
      if (ProjectRootManager.getInstance(project).getFileIndex().isInSource(vFile)
          && CompilerConfiguration.getInstance(project).isExcludedFromCompilation(vFile)) {
        return true;
      }
    }
    return false;
  }
  public static int getFlags(PsiModifierListOwner element, final boolean isLocked) {
    final PsiFile containingFile = element.getContainingFile();
    final VirtualFile vFile = containingFile == null ? null : containingFile.getVirtualFile();
    final boolean isEnum = element instanceof PsiClass && ((PsiClass)element).isEnum();
    int flags = (element.hasModifierProperty(PsiModifier.FINAL) && !isEnum ? FLAGS_FINAL : 0)
                      | (element.hasModifierProperty(PsiModifier.STATIC) && !isEnum ? FLAGS_STATIC : 0)
                      | (isLocked ? FLAGS_LOCKED : 0)
                      | (isExcluded(vFile, element.getProject()) ? FLAGS_EXCLUDED : 0);
    if (element instanceof PsiClass) {
      final PsiClass aClass = (PsiClass)element;
      if (element.hasModifierProperty(PsiModifier.ABSTRACT) && !((PsiClass)element).isInterface()) {
        flags |= FLAGS_ABSTRACT;
      }
      final PsiClass testClass = aClass.getManager().findClass("junit.framework.TestCase", aClass.getResolveScope());
      if (testClass != null && InheritanceUtil.isInheritorOrSelf(aClass, testClass, true)) {
        flags |= FLAGS_JUNIT_TEST;
      }
    }
    return flags;
  }

  private static final Icon STATIC_MARK_ICON = IconLoader.getIcon("/nodes/staticMark.png");
  private static final Icon FINAL_MARK_ICON = IconLoader.getIcon("/nodes/finalMark.png");
  private static final Icon ABSTRACT_EXCEPTION_CLASS_ICON = IconLoader.getIcon("/nodes/abstractException.png");
  private static final Icon JUNIT_TEST_MARK = IconLoader.getIcon("/nodes/junitTestMark.png");

  private static final int CLASS_KIND_INTERFACE     = 10;
  private static final int CLASS_KIND_ANNOTATION    = 20;
  public static final int CLASS_KIND_CLASS         = 30;
  private static final int CLASS_KIND_ANONYMOUS     = 40;
  private static final int CLASS_KIND_ENUM          = 50;
  private static final int CLASS_KIND_ASPECT        = 60;
  private static final int CLASS_KIND_JSP           = 70;
  public static final int CLASS_KIND_EXCEPTION = 80;
  private static final int CLASS_KIND_JUNIT_TEST = 90;

  public static final int FLAGS_ABSTRACT = 0x100;
  private static final int FLAGS_STATIC = 0x200;
  private static final int FLAGS_FINAL = 0x400;
  private static final int FLAGS_LOCKED = 0x800;
  private static final int FLAGS_EXCLUDED = 0x1000;
  private static final int FLAGS_JUNIT_TEST = 0x2000;

  private static final Key<CachedValue<Integer>> CLASS_KIND_KEY = new Key<CachedValue<Integer>>("CLASS_KIND_KEY");

  public static int getClassKind(final PsiClass aClass) {
    if (!aClass.isValid()) {
      aClass.putUserData(CLASS_KIND_KEY, null);
      return CLASS_KIND_CLASS;
    }

    CachedValue<Integer> value = aClass.getUserData(CLASS_KIND_KEY);
    if (value == null) {
      value = aClass.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<Integer>() {
        public Result<Integer> compute() {
          return new Result<Integer>(new Integer(getClassKindImpl(aClass)),
                                     new Object[] {PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT});
        }
      }, false);
      aClass.putUserData(CLASS_KIND_KEY, value);
    }
    return value.getValue().intValue();
  }

  private static int getClassKindImpl(PsiClass aClass) {
    if (!aClass.isValid()) return CLASS_KIND_CLASS;

    final EjbClassRole role = J2EERolesUtil.getEjbRole(aClass);
    if (role != null) return role.getType();
    if (aClass.isAnnotationType()) {
      return CLASS_KIND_ANNOTATION;
    }
    if (aClass.isEnum()) {
      return CLASS_KIND_ENUM;
    }
    if (aClass.isInterface()) {
      return CLASS_KIND_INTERFACE;
    }
    if (aClass instanceof JspClass) {
      return CLASS_KIND_JSP;
    }
    if (aClass instanceof PsiAnonymousClass) {
      return CLASS_KIND_ANONYMOUS;
    }

    final PsiManager manager = aClass.getManager();
    final PsiClass javaLangTrowable = manager.findClass("java.lang.Throwable", aClass.getResolveScope());
    final boolean isException = javaLangTrowable != null && InheritanceUtil.isInheritorOrSelf(aClass, javaLangTrowable, true);
    if (isException) {
      return CLASS_KIND_EXCEPTION;
    }

    final PsiClass testClass = aClass.getManager().findClass("junit.framework.TestCase", aClass.getResolveScope());
    if (testClass != null && InheritanceUtil.isInheritorOrSelf(aClass, testClass, true)) {
      return CLASS_KIND_JUNIT_TEST;
    }
    return CLASS_KIND_CLASS;
  }

  private static final TIntObjectHashMap<Icon> BASE_ICON = new TIntObjectHashMap<Icon>(20);
  static {
    BASE_ICON.put(CLASS_KIND_CLASS, Icons.CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_CLASS | FLAGS_ABSTRACT, Icons.ABSTRACT_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_ANNOTATION, Icons.ANNOTATION_TYPE_ICON);
    BASE_ICON.put(CLASS_KIND_ANNOTATION | FLAGS_ABSTRACT, Icons.ANNOTATION_TYPE_ICON);
    BASE_ICON.put(CLASS_KIND_ANONYMOUS, Icons.ANONYMOUS_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_ANONYMOUS | FLAGS_ABSTRACT, Icons.ANONYMOUS_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_ASPECT, Icons.ASPECT_ICON);
    BASE_ICON.put(CLASS_KIND_ASPECT | FLAGS_ABSTRACT, Icons.ASPECT_ICON);
    BASE_ICON.put(CLASS_KIND_ENUM, Icons.ENUM_ICON);
    BASE_ICON.put(CLASS_KIND_ENUM | FLAGS_ABSTRACT, Icons.ENUM_ICON);
    BASE_ICON.put(CLASS_KIND_EXCEPTION, Icons.EXCEPTION_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_EXCEPTION | FLAGS_ABSTRACT, ABSTRACT_EXCEPTION_CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_INTERFACE, Icons.INTERFACE_ICON);
    BASE_ICON.put(CLASS_KIND_INTERFACE | FLAGS_ABSTRACT, Icons.INTERFACE_ICON);
    BASE_ICON.put(CLASS_KIND_JSP, Icons.JSP_ICON);
    BASE_ICON.put(CLASS_KIND_JSP | FLAGS_ABSTRACT, Icons.JSP_ICON);
    BASE_ICON.put(CLASS_KIND_JUNIT_TEST, Icons.CLASS_ICON);
    BASE_ICON.put(CLASS_KIND_JUNIT_TEST | FLAGS_ABSTRACT, Icons.ABSTRACT_CLASS_ICON);
  }

  private static Icon getClassBaseIcon(final PsiClass aClass) {
    final EjbClassRole role = J2EERolesUtil.getEjbRole(aClass);
    if (role != null) return role.getIcon();
    final int classKind = getClassKind(aClass);
    final boolean isAbstract = aClass.hasModifierProperty(PsiModifier.ABSTRACT);
    return BASE_ICON.get(classKind | (isAbstract ? FLAGS_ABSTRACT : 0));
  }

  private static RowIcon createLayeredIcon(Icon icon, int flags) {
    if (flags != 0) {
      List<Icon> iconLayers = new SmartList<Icon>();
      if ((flags & FLAGS_STATIC) != 0) {
        iconLayers.add(STATIC_MARK_ICON);
      }
      if ((flags & FLAGS_LOCKED) != 0) {
        iconLayers.add(Icons.LOCKED_ICON);
      }
      if ((flags & FLAGS_EXCLUDED) != 0) {
        iconLayers.add(Icons.EXCLUDED_FROM_COMPILE_ICON);
      }
      final boolean isFinal = (flags & FLAGS_FINAL) != 0;
      if (isFinal) {
        iconLayers.add(FINAL_MARK_ICON);
      }
      if ((flags & FLAGS_JUNIT_TEST) != 0) {
        iconLayers.add(JUNIT_TEST_MARK);
      }
      LayeredIcon layeredIcon = new LayeredIcon(1 + iconLayers.size());
      layeredIcon.setIcon(icon, 0);
      for (int i = 0; i < iconLayers.size(); i++) {
        Icon icon1 = iconLayers.get(i);
        layeredIcon.setIcon(icon1, i+1);
      }
      icon = layeredIcon;
    }
    RowIcon baseIcon = new RowIcon(2);
    baseIcon.setIcon(icon, 0);
    return baseIcon;
  }
}