/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ipp.increment;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.MutablyNamedIntention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ExtractIncrementIntention
        extends MutablyNamedIntention{
    public String getTextForElement(PsiElement element){
        final PsiJavaToken sign;
        if(element instanceof PsiPostfixExpression){
            sign = ((PsiPostfixExpression) element).getOperationSign();
        } else{
            sign = ((PsiPrefixExpression) element).getOperationSign();
        }
        final String operator = sign.getText();
        return "Extract " + operator;
    }

    public String getFamilyName(){
        return "Extract Increment";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new ExtractIncrementPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiExpression operand;
        if(element instanceof PsiPostfixExpression){
            operand = ((PsiPostfixExpression) element).getOperand();
        } else{
            operand = ((PsiPrefixExpression) element).getOperand();
        }
        final PsiStatement statement = PsiTreeUtil
                .getParentOfType(element, PsiStatement.class);
        assert statement!=null;
        final PsiElement parent = statement.getParent();
        assert parent != null;
        final PsiManager mgr = element.getManager();

        final PsiElementFactory factory = mgr.getElementFactory();
        final String newStatementText = element.getText() + ';';
        final PsiStatement newCall =
                factory.createStatementFromText(newStatementText, null);

        final PsiElement insertedElement;
        if(element instanceof PsiPostfixExpression){
            insertedElement = parent.addAfter(newCall, statement );
        }else{
            insertedElement = parent.addBefore(newCall, statement);
        }

        final CodeStyleManager codeStyleManager = mgr.getCodeStyleManager();
        codeStyleManager.reformat(insertedElement);
        replaceExpression(operand.getText(), (PsiExpression) element);
    }


}
