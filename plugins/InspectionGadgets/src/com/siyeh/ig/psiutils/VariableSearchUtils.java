/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VariableSearchUtils {

    private VariableSearchUtils() {
        super();
    }

    public static boolean existsLocalOrParameter(@NotNull String varName,
                                                 @Nullable PsiElement context) {
        if (context == null) {
            return false;
        }
        if (existsParameter(varName, context)) {
            return true;
        }
        if (existsLocal(varName, context)) {
            return true;
        }
        if (existsForLoopLocal(varName, context)) {
            return true;
        }
        return existsForeachLoopLocal(varName, context);
    }

    private static boolean existsParameter(@NotNull String varName,
                                           PsiElement context) {
        PsiMethod ancestor =
                PsiTreeUtil.getParentOfType(context, PsiMethod.class);
        while (ancestor != null) {
            final PsiParameterList parameterList = ancestor.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            for (final PsiParameter parameter : parameters) {
                final String parameterName = parameter.getName();
                if (varName.equals(parameterName)) {
                    return true;
                }
            }
            ancestor = PsiTreeUtil.getParentOfType(ancestor, PsiMethod.class);
        }
        return false;
    }

    private static boolean existsLocal(@NotNull String variableName,
                                       PsiElement context) {
        PsiCodeBlock ancestor =
                PsiTreeUtil.getParentOfType(context, PsiCodeBlock.class);
        while (ancestor != null) {
            final PsiStatement[] statements = ancestor.getStatements();
            for (final PsiStatement statement : statements) {
                if (statement instanceof PsiDeclarationStatement) {
                    final PsiDeclarationStatement declarationStatement =
                            (PsiDeclarationStatement) statement;
                    final PsiElement[] elements =
                            declarationStatement.getDeclaredElements();
                    for (PsiElement element : elements) {
                        if (!(element instanceof PsiLocalVariable)) {
                            continue;
                        }
                        final PsiLocalVariable localVariable =
                                (PsiLocalVariable) element;
                        final String localVariableName =
                                localVariable.getName();
                        if(variableName.equals(localVariableName)) {
                            return true;
                        }
                    }
                }
            }
            ancestor =
                    PsiTreeUtil.getParentOfType(ancestor, PsiCodeBlock.class);
        }
        return false;
    }

    private static boolean existsForLoopLocal(@NotNull String variableName, 
                                              PsiElement context) {
        PsiForStatement forLoopAncestor =
                PsiTreeUtil.getParentOfType(context, PsiForStatement.class);
        while (forLoopAncestor != null) {
            final PsiStatement initialization =
                    forLoopAncestor.getInitialization();
            if (initialization instanceof PsiDeclarationStatement) {
                final PsiDeclarationStatement declarationStatement =
                        (PsiDeclarationStatement) initialization;
                final PsiElement[] elements =
                        declarationStatement.getDeclaredElements();
                for (PsiElement element : elements) {
                    final PsiLocalVariable localVariable =
                            (PsiLocalVariable) element;
                    final String localVariableName = localVariable.getName();
                    if (variableName.equals(localVariableName)) {
                        return true;
                    }
                }
            }
            forLoopAncestor = PsiTreeUtil.getParentOfType(forLoopAncestor,
                    PsiForStatement.class);
        }
        return false;
    }

    private static boolean existsForeachLoopLocal(@NotNull String variableName,
                                                  PsiElement context) {
        PsiForeachStatement forLoopAncestor =
                PsiTreeUtil.getParentOfType(context, PsiForeachStatement.class);
        while (forLoopAncestor != null) {
            final PsiParameter parameter =
                    forLoopAncestor.getIterationParameter();
            final String parameterName = parameter.getName();
            if (variableName.equals(parameterName)) {
                return true;
            }
            forLoopAncestor = PsiTreeUtil.getParentOfType(forLoopAncestor,
                    PsiForeachStatement.class);
        }
        return false;
    }
}