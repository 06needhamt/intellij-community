package com.intellij.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiImplUtil");

  private PsiImplUtil() {
  }

  @NotNull public static PsiMethod[] getConstructors(PsiClass aClass) {
    final List<PsiMethod> constructorsList = new SmartList<PsiMethod>();
    final PsiMethod[] methods = aClass.getMethods();
    for (final PsiMethod method : methods) {
      if (method.isConstructor()) constructorsList.add(method);
    }
    return constructorsList.toArray(new PsiMethod[constructorsList.size()]);
  }

  @Nullable
  public static PsiAnnotationMemberValue findDeclaredAttributeValue(PsiAnnotation annotation, @NonNls String attributeName) {
    if ("value".equals(attributeName)) attributeName = null;
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    for (PsiNameValuePair attribute : attributes) {
      @NonNls final String name = attribute.getName();
      if (ObjectUtils.equals(name, attributeName)
          || attributeName == null && name.equals("value")) return attribute.getValue();
    }
    return null;
  }
  
  @Nullable
  public static PsiAnnotationMemberValue findAttributeValue(PsiAnnotation annotation, @NonNls String attributeName) {
    final PsiAnnotationMemberValue value = findDeclaredAttributeValue(annotation, attributeName);
    if (value != null) return value;

    if (attributeName == null) attributeName = "value";
    PsiElement resolved = annotation.getNameReferenceElement().resolve();
    if (resolved != null) {
      PsiMethod[] methods = ((PsiClass)resolved).getMethods();
      for (PsiMethod method : methods) {
        if (method instanceof PsiAnnotationMethod && ObjectUtils.equals(method.getName(), attributeName)) {
          return ((PsiAnnotationMethod)method).getDefaultValue();
        }
      }
    }
    return null;
  }

  public static PsiTypeParameter[] getTypeParameters(PsiTypeParameterListOwner owner) {
    final PsiTypeParameterList typeParameterList = owner.getTypeParameterList();
    if (typeParameterList != null) {
      return typeParameterList.getTypeParameters();
    }
    return PsiTypeParameter.EMPTY_ARRAY;
  }

  @NotNull public static PsiJavaCodeReferenceElement[] namesToPackageReferences(PsiManager manager, String[] names) {
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[names.length];
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      try {
        refs[i] = manager.getElementFactory().createPackageReferenceElement(name);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return refs;
  }

  public static int getParameterIndex(PsiParameter parameter, PsiParameterList parameterList) {
    PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (parameter.equals(parameters[i])) return i;
    }
    LOG.assertTrue(false);
    return -1;
  }

  public static int getTypeParameterIndex(PsiTypeParameter typeParameter, PsiTypeParameterList typeParameterList) {
    PsiTypeParameter[] typeParameters = typeParameterList.getTypeParameters();
    for (int i = 0; i < typeParameters.length; i++) {
      if (typeParameter.equals(typeParameters[i])) return i;
    }
    LOG.assertTrue(false);
    return -1;
  }

  @NotNull public static Object[] getReferenceVariantsByFilter(PsiJavaCodeReferenceElement reference,
                                                      ElementFilter filter) {
    FilterScopeProcessor processor = new FilterScopeProcessor(filter);
    PsiScopesUtil.resolveAndWalk(processor, reference, null, true);
    return processor.getResults().toArray();
  }

  public static boolean processDeclarationsInMethod(PsiMethod method, PsiScopeProcessor processor, PsiSubstitutor substitutor,
                                                    PsiElement lastParent, PsiElement place) {
    final ElementClassHint hint = processor.getHint(ElementClassHint.class);
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, method);
    if (hint == null || hint.shouldProcess(PsiClass.class)) {
      final PsiTypeParameterList list = method.getTypeParameterList();
      if (list != null && !list.processDeclarations(processor, substitutor, null, place)) return false;
    }
    if (lastParent instanceof PsiCodeBlock) {
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      for (PsiParameter parameter : parameters) {
        if (!processor.execute(parameter, substitutor)) return false;
      }
    }

    return true;
  }

  public static boolean hasTypeParameters(PsiTypeParameterListOwner psiMethod) {
    final PsiTypeParameterList typeParameterList = psiMethod.getTypeParameterList();
    return typeParameterList != null && typeParameterList.getTypeParameters().length != 0;
  }

  @NotNull public static PsiType[] typesByReferenceParameterList(final PsiReferenceParameterList parameterList) {
    PsiTypeElement[] typeElements = parameterList.getTypeParameterElements();

    return typesByTypeElements(typeElements);
  }

  @NotNull public static PsiType[] typesByTypeElements(PsiTypeElement[] typeElements) {
    PsiType[] types = new PsiType[typeElements.length];
    for(int i = 0; i < types.length; i++){
      types[i] = typeElements[i].getType();
    }
    return types;
  }

  public static PsiType getType (PsiClassObjectAccessExpression classAccessExpression) {
    GlobalSearchScope resolveScope = classAccessExpression.getResolveScope();
    PsiManager manager = classAccessExpression.getManager();
    final PsiClass classClass = manager.findClass("java.lang.Class", resolveScope);
    if (classClass == null){
      return new PsiClassReferenceType(new LightClassReference(manager, "Class", "java.lang.Class", resolveScope));
    }
    if (PsiUtil.getLanguageLevel(classAccessExpression).compareTo(LanguageLevel.JDK_1_5) < 0) {
      //Raw java.lang.Class
      return manager.getElementFactory().createType(classClass);
    }

    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    PsiType operandType = classAccessExpression.getOperand().getType();
    if (operandType instanceof PsiPrimitiveType && !PsiType.NULL.equals(operandType)) {
      if (PsiType.VOID.equals(operandType)) {
        operandType = manager.getElementFactory().createTypeByFQClassName("java.lang.Void", classAccessExpression.getResolveScope());
      }
      else {
        operandType = ((PsiPrimitiveType)operandType).getBoxedType(classAccessExpression);
      }
    }
    final PsiTypeParameter[] typeParameters = classClass.getTypeParameters();
    if (typeParameters.length == 1) {
      substitutor = substitutor.put(typeParameters[0], operandType);
    }

    return new PsiImmediateClassType(classClass, substitutor);
  }

  public static PsiAnnotation findAnnotation(PsiModifierList modifierList, @NotNull String qualifiedName) {
    PsiAnnotation[] annotations = modifierList.getAnnotations();
    for (PsiAnnotation annotation : annotations) {
      if (qualifiedName.equals(annotation.getQualifiedName())) return annotation;
    }

    return null;
  }

  @Nullable
  public static ASTNode findDocComment(final CompositeElement element) {
    TreeElement node = element.getFirstChildNode();
    while(node != null && (ElementType.WHITE_SPACE_BIT_SET.contains(node.getElementType()) ||
                           node.getElementType() == JavaTokenType.C_STYLE_COMMENT ||
                           node.getElementType() == JavaTokenType.END_OF_LINE_COMMENT)) {
      node = node.getTreeNext();
    }

    if (node != null && node.getElementType() == JavaDocElementType.DOC_COMMENT) {
      return node;
    }
    else {
      return null;
    }
  }

  public static PsiType normalizeWildcardTypeByPosition(final PsiType type, final PsiExpression expression) {
    PsiExpression toplevel = expression;
    while (toplevel.getParent() instanceof PsiArrayAccessExpression &&
           ((PsiArrayAccessExpression)toplevel.getParent()).getArrayExpression() == toplevel) {
      toplevel = (PsiExpression)toplevel.getParent();
    }

    final PsiType normalized = doNormalizeWildcardByPosition(type, expression, toplevel);
    if (normalized instanceof PsiClassType && !PsiUtil.isAccessedForWriting(toplevel)) {
      return PsiUtil.captureToplevelWildcards(normalized, expression);
    }

    return normalized;
  }

  private static PsiType doNormalizeWildcardByPosition(final PsiType type, final PsiExpression expression, final PsiExpression toplevel) {
    if (type instanceof PsiCapturedWildcardType) {
      return doNormalizeWildcardByPosition(((PsiCapturedWildcardType)type).getWildcard(), expression, toplevel);
    }


    if (type instanceof PsiWildcardType) {
      final PsiWildcardType wildcardType = (PsiWildcardType)type;

      if (PsiUtil.isAccessedForWriting(toplevel)) {
        return wildcardType.isSuper() ? wildcardType.getBound() : PsiCapturedWildcardType.create(wildcardType, expression);
      }
      else {
        if (wildcardType.isExtends()) {
          return wildcardType.getBound();
        }
        else {
          return PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
        }
      }
    }
    else if (type instanceof PsiArrayType) {
      final PsiType componentType = ((PsiArrayType)type).getComponentType();
      final PsiType normalizedComponentType = doNormalizeWildcardByPosition(componentType, expression, toplevel);
      if (normalizedComponentType != componentType) {
        return normalizedComponentType.createArrayType();
      }
    }

    return type;
  }
}
