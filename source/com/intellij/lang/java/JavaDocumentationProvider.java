/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.java;

import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.LangBundle;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.documentation.CodeDocumentationProvider;
import com.intellij.lang.documentation.ExtensibleDocumentationProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 11, 2006
 * Time: 7:45:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class JavaDocumentationProvider extends ExtensibleDocumentationProvider implements CodeDocumentationProvider {
  private static final String LINE_SEPARATOR = "\n";

  @NonNls private static final String PARAM_TAG = "@param";
  @NonNls private static final String RETURN_TAG = "@return";
  @NonNls private static final String THROWS_TAG = "@throws";

  public String getQuickNavigateInfo(PsiElement element) {
    if (element instanceof PsiClass) {
      return generateClassInfo((PsiClass) element);
    } else if (element instanceof PsiMethod) {
      return generateMethodInfo((PsiMethod) element);
    } else if (element instanceof PsiField) {
      return generateFieldInfo((PsiField) element);
    } else if (element instanceof PsiVariable) {
      return generateVariableInfo((PsiVariable) element);
    } else if (element instanceof PsiPackage) {
      return generatePackageInfo((PsiPackage) element);
    }
    return super.getQuickNavigateInfo(element);
  }

  private static void newLine(StringBuffer buffer) {
    // Don't know why space has to be added after newline for good text alignment...
    buffer.append("\n ");
  }

  private static void generateType(@NonNls StringBuffer buffer, PsiType type, PsiElement context) {
    if (type instanceof PsiPrimitiveType) {
      buffer.append(type.getCanonicalText());

      return;
    }

    if (type instanceof PsiWildcardType) {
      PsiWildcardType wc = ((PsiWildcardType) type);
      PsiType bound = wc.getBound();

      buffer.append("?");

      if (bound != null) {
        buffer.append(wc.isExtends() ? " extends " : " super ");
        generateType(buffer, bound, context);
      }
    }

    if (type instanceof PsiArrayType) {
      generateType(buffer, ((PsiArrayType) type).getComponentType(), context);
      buffer.append("[]");

      return;
    }

    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType) type).resolveGenerics();
      PsiClass psiClass = result.getElement();
      PsiSubstitutor psiSubst = result.getSubstitutor();

      if (psiClass == null || psiClass instanceof PsiTypeParameter) {
        buffer.append(type.getPresentableText());
        return;
      }

      buffer.append(JavaDocUtil.getShortestClassName(psiClass, context));

      if (psiClass.hasTypeParameters()) {
        StringBuffer subst = new StringBuffer();
        boolean goodSubst = true;

        PsiTypeParameter[] params = psiClass.getTypeParameters();

        subst.append("<");
        for (int i = 0; i < params.length; i++) {
          PsiType t = psiSubst.substitute(params[i]);

          if (t == null) {
            goodSubst = false;
            break;
          }

          generateType(subst, t, context);

          if (i < params.length - 1) {
            subst.append(", ");
          }
        }

        if (goodSubst) {
          subst.append(">");
          String text = subst.toString();

          buffer.append(text);
        }
      }
    }
  }

  private static void generateInitializer(StringBuffer buffer, PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      String text = initializer.getText().trim();
      int index1 = text.indexOf('\n');
      if (index1 < 0) index1 = text.length();
      int index2 = text.indexOf('\r');
      if (index2 < 0) index2 = text.length();
      int index = Math.min(index1, index2);
      boolean trunc = index < text.length();
      text = text.substring(0, index);
      buffer.append(" = ");
      buffer.append(text);
      if (trunc) {
        buffer.append("...");
      }
    }
  }

  private static void generateModifiers(StringBuffer buffer, PsiElement element) {
    String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);

    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
  }

  private static String generatePackageInfo(PsiPackage aPackage) {
    return aPackage.getQualifiedName();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String generateClassInfo(PsiClass aClass) {
    StringBuffer buffer = new StringBuffer();

    if (aClass instanceof PsiAnonymousClass) return LangBundle.message("java.terms.anonymous.class");

    PsiFile file = aClass.getContainingFile();
    final Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module != null) {
      buffer.append('[').append(module.getName()).append("] ");
    }

    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile) file).getPackageName();
      if (packageName.length() > 0) {
        buffer.append(packageName);
        newLine(buffer);
      }
    }

    generateModifiers(buffer, aClass);

    final String classString =
      aClass.isInterface() ? "java.terms.interface" :
      aClass instanceof PsiTypeParameter ? "java.terms.type.parameter" :
      aClass.isEnum() ? "java.terms.enum" : "java.terms.class";
    buffer.append(LangBundle.message(classString) + " ");

    buffer.append(JavaDocUtil.getShortestClassName(aClass, aClass));

    if (aClass.hasTypeParameters()) {
      PsiTypeParameter[] parms = aClass.getTypeParameters();

      buffer.append("<");

      for (int i = 0; i < parms.length; i++) {
        PsiTypeParameter p = parms[i];

        buffer.append(p.getName());

        PsiClassType[] refs = p.getExtendsList().getReferencedTypes();

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            generateType(buffer, refs[j], aClass);

            if (j < refs.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < parms.length - 1) {
          buffer.append(", ");
        }
      }

      buffer.append(">");
    }

    PsiClassType[] refs;
    if (!aClass.isEnum() && !aClass.isAnnotationType()) {
      PsiReferenceList extendsList = aClass.getExtendsList();
      refs = extendsList == null ? PsiClassType.EMPTY_ARRAY : extendsList.getReferencedTypes();
      if (refs.length > 0 || !aClass.isInterface() && !"java.lang.Object".equals(aClass.getQualifiedName())) {
        buffer.append(" extends ");
        if (refs.length == 0) {
          buffer.append("Object");
        } else {
          for (int i = 0; i < refs.length; i++) {
            generateType(buffer, refs[i], aClass);

            if (i < refs.length - 1) {
              buffer.append(", ");
            }
          }
        }
      }
    }

    refs = aClass.getImplementsListTypes();
    if (refs.length > 0) {
      newLine(buffer);
      buffer.append("implements ");
      for (int i = 0; i < refs.length; i++) {
        generateType(buffer, refs[i], aClass);

        if (i < refs.length - 1) {
          buffer.append(", ");
        }
      }
    }

    return buffer.toString();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static String generateMethodInfo(PsiMethod method) {
    StringBuffer buffer = new StringBuffer();

    PsiClass parentClass = method.getContainingClass();

    if (parentClass != null) {
      buffer.append(JavaDocUtil.getShortestClassName(parentClass, method));
      newLine(buffer);
    }

    generateModifiers(buffer, method);

    PsiTypeParameter[] params = method.getTypeParameters();

    if (params.length > 0) {
      buffer.append("<");
      for (int i = 0; i < params.length; i++) {
        PsiTypeParameter param = params[i];

        buffer.append(param.getName());

        PsiClassType[] extendees = param.getExtendsList().getReferencedTypes();

        if (extendees.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < extendees.length; j++) {
            generateType(buffer, extendees[j], method);

            if (j < extendees.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < params.length - 1) {
          buffer.append(", ");
        }
      }
      buffer.append("> ");
    }

    if (method.getReturnType() != null) {
      generateType(buffer, method.getReturnType(), method);
      buffer.append(" ");
    }

    buffer.append(method.getName());

    buffer.append(" (");
    PsiParameter[] parms = method.getParameterList().getParameters();
    for (int i = 0; i < parms.length; i++) {
      PsiParameter parm = parms[i];
      generateType(buffer, parm.getType(), method);
      buffer.append(" ");
      if (parm.getName() != null) {
        buffer.append(parm.getName());
      }
      if (i < parms.length - 1) {
        buffer.append(", ");
      }
    }

    buffer.append(")");

    PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
    if (refs.length > 0) {
      newLine(buffer);
      buffer.append(" throws ");
      for (int i = 0; i < refs.length; i++) {
        PsiClass throwsClass = refs[i].resolve();

        if (throwsClass != null) {
          buffer.append(JavaDocUtil.getShortestClassName(throwsClass, method));
        } else {
          buffer.append(refs[i].getPresentableText());
        }

        if (i < refs.length - 1) {
          buffer.append(", ");
        }
      }
    }

    return buffer.toString();
  }

  private static String generateFieldInfo(PsiField field) {
    StringBuffer buffer = new StringBuffer();
    PsiClass parentClass = field.getContainingClass();

    if (parentClass != null) {
      buffer.append(JavaDocUtil.getShortestClassName(parentClass, field));
      newLine(buffer);
    }

    generateModifiers(buffer, field);

    generateType(buffer, field.getType(), field);
    buffer.append(" ");
    buffer.append(field.getName());

    generateInitializer(buffer, field);

    return buffer.toString();
  }

  private static String generateVariableInfo(PsiVariable variable) {
    StringBuffer buffer = new StringBuffer();

    generateModifiers(buffer, variable);

    generateType(buffer, variable.getType(), variable);

    buffer.append(" ");

    buffer.append(variable.getName());
    generateInitializer(buffer, variable);

    return buffer.toString();
  }

  public PsiComment findExistingDocComment(final PsiComment _element) {
    PsiElement parentElement = _element.getParent();

    return parentElement instanceof PsiDocCommentOwner ? ((PsiDocCommentOwner)parentElement).getDocComment() : null;
  }

  public String generateDocumentationContentStub(PsiComment _element) {
    PsiElement parentElement = _element.getParent();
    final Project project = _element.getProject();
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      if (parentElement instanceof PsiMethod) {
        PsiMethod psiMethod = (PsiMethod)parentElement;
        final PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
        final CodeDocumentationAwareCommenter commenter = (CodeDocumentationAwareCommenter)LanguageCommenters.INSTANCE
          .forLanguage(parentElement.getLanguage());
        final Map<String, String> param2Description = new HashMap<String, String>();
        final PsiMethod[] superMethods = psiMethod.findSuperMethods();
        for (PsiMethod superMethod : superMethods) {
          final PsiDocComment comment = superMethod.getDocComment();
          if (comment != null) {
            final PsiDocTag[] params = comment.findTagsByName("param");
            for (PsiDocTag param : params) {
              final PsiElement[] dataElements = param.getDataElements();
              if (dataElements != null) {
                String paramName = null;
                for (PsiElement dataElement : dataElements) {
                  if (dataElement instanceof PsiDocParamRef) {
                    paramName = dataElement.getReference().getCanonicalText();
                    break;
                  }
                }
                if (paramName != null) {
                  param2Description.put(paramName, param.getText());
                }
              }
            }
          }
        }
        for (PsiParameter parameter : parameters) {
          String description = param2Description.get(parameter.getName());
          if (description != null) {
            builder.append(createDocCommentLine("", project, commenter));
            if (description.indexOf('\n') > -1) description = description.substring(0, description.lastIndexOf('\n'));
            builder.append(description);
          } else {
            builder.append(createDocCommentLine(PARAM_TAG, project, commenter));
            builder.append(parameter.getName());
          }
          builder.append(LINE_SEPARATOR);
        }

        final PsiTypeParameterList typeParameterList = psiMethod.getTypeParameterList();
        if (typeParameterList != null) {
          createTypeParamsListComment(builder, project, commenter, typeParameterList);
        }
        if (psiMethod.getReturnType() != null && psiMethod.getReturnType() != PsiType.VOID) {
          builder.append(createDocCommentLine(RETURN_TAG, project, commenter));
          builder.append(LINE_SEPARATOR);
        }

        final PsiJavaCodeReferenceElement[] references = psiMethod.getThrowsList().getReferenceElements();
        for (PsiJavaCodeReferenceElement reference : references) {
          builder.append(createDocCommentLine(THROWS_TAG, project,commenter));
          builder.append(reference.getText());
          builder.append(LINE_SEPARATOR);
        }
      } else if (parentElement instanceof PsiClass) {
        final PsiTypeParameterList typeParameterList = ((PsiClass)parentElement).getTypeParameterList();
        if (typeParameterList != null) {
           final CodeDocumentationAwareCommenter commenter = (CodeDocumentationAwareCommenter)LanguageCommenters.INSTANCE
          .forLanguage(parentElement.getLanguage());
          createTypeParamsListComment(builder, project, commenter, typeParameterList);
        }
      }
      return builder.length() > 0 ? builder.toString() : null;
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  private static void createTypeParamsListComment(final StringBuilder buffer, final Project project, final CodeDocumentationAwareCommenter commenter,
                                           final PsiTypeParameterList typeParameterList) {
    final PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
    for (PsiTypeParameter typeParameter : typeParameters) {
      buffer.append(createDocCommentLine(PARAM_TAG, project, commenter));
      buffer.append("<").append(typeParameter.getName()).append(">");
      buffer.append(LINE_SEPARATOR);
    }
  }

  public static String createDocCommentLine(String lineData, Project project, CodeDocumentationAwareCommenter commenter) {
    if (!CodeStyleSettingsManager.getSettings(project).JD_LEADING_ASTERISKS_ARE_ENABLED) {
      return " " + lineData + " ";
    } else {
      if (lineData.length() == 0) {
        return commenter.getDocumentationCommentLinePrefix() + " ";
      } else {
        return commenter.getDocumentationCommentLinePrefix() + " " + lineData + " ";
      }

    }
  }
}
