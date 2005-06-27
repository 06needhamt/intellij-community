package com.siyeh.ig.visibility;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class MethodOverloadsParentMethodInspection extends MethodInspection{
    private final RenameFix fix = new RenameFix();

    public String getID(){
        return "MethodOverloadsMethodOfSuperclass";
    }

    public String getDisplayName(){
        return "Method overloads method of superclass";
    }

    public String getGroupDisplayName(){
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "Method '#ref' overloads a compatible method of a superclass, when overriding might have been intended #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MethodOverloadsParentMethodVisitor();
    }

    private static class MethodOverloadsParentMethodVisitor
            extends BaseInspectionVisitor{
        public void visitMethod(@NotNull PsiMethod method){
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.PRIVATE)
                    || method.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }

            final PsiMethod[] superMethods = PsiSuperMethodUtil
                    .findSuperMethods(method);
            if(superMethods != null && superMethods.length != 0){
                return;
            }
            PsiClass ancestorClass = aClass.getSuperClass();
            final Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
            while(ancestorClass != null){
                if(!visitedClasses.add(ancestorClass)){
                    return;
                }
                if(methodOverloads(method, ancestorClass)){
                    registerMethodError(method);
                    return;
                }
                ancestorClass = ancestorClass.getSuperClass();
            }
        }

        private static boolean methodOverloads(PsiMethod meth,
                                               PsiClass ancestorClass){
            if(methodOverrides(meth, ancestorClass)){
                return false;
            }
            final String methName = meth.getName();
            final PsiParameterList parameterList = meth.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            final PsiMethod[] methods = ancestorClass
                    .findMethodsByName(methName, false);
            if(methods != null){
                for(final PsiMethod testMethod : methods){
                    if(!testMethod.hasModifierProperty(PsiModifier.PRIVATE) &&
                            !testMethod.hasModifierProperty(PsiModifier.STATIC) &&
                            !isOverriddenInClass(testMethod, meth.getContainingClass())){
                        final PsiParameterList testParameterList = testMethod
                                .getParameterList();
                        final PsiParameter[] testParameters = testParameterList
                                .getParameters();
                        if(testParameters.length == parameters.length &&
                                !parametersAreCompatible(parameters,
                                                         testParameters)){
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static boolean isOverriddenInClass(PsiMethod method,
                                                   PsiClass aClass){
            final PsiMethod[] methods = aClass.getMethods();
            for(PsiMethod testMethod : methods){
                if(testMethod.getName().equals(method.getName()))
                {
                    final PsiMethod[] superMethods = PsiSuperMethodUtil
                            .findSuperMethods(testMethod, true);
                    for(final PsiMethod superMethod : superMethods){
                        if(superMethod .equals(method)){
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static boolean parametersAreCompatible(
                PsiParameter[] parameters, PsiParameter[] testParameters){
            for(int i = 0; i < parameters.length; i++){
                final PsiParameter parameter = parameters[i];
                final PsiType parameterType = parameter.getType();
                final PsiParameter testParameter = testParameters[i];
                final PsiType testParameterType = testParameter.getType();

                if(!parameterType.isAssignableFrom(testParameterType)){
                    return false;
                }
            }
            return true;
        }

        private static boolean methodOverrides(PsiMethod meth,
                                               PsiClass ancestorClass){
            final PsiMethod[] superMethods = PsiSuperMethodUtil
                    .findSuperMethods(meth, true);
            for(final PsiMethod superMethod : superMethods){
                if(ancestorClass.equals(superMethod.getContainingClass())){
                    return true;
                }
            }
            return false;
        }
    }
}
