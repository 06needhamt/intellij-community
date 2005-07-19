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
package com.siyeh.ipp.fqnames;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;

public class ReplaceFullyQualifiedNameWithImportIntention extends Intention{
    public String getText(){
        return "Replace qualified name with import";
    }

    public String getFamilyName(){
        return "Replace Qualified Name With Import";
    }

    @NotNull
    public PsiElementPredicate getElementPredicate(){
        return new FullyQualifiedNamePredicate();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException{
        PsiJavaCodeReferenceElement reference =
                (PsiJavaCodeReferenceElement) element;
        while(reference != null &&
                reference.getParent() instanceof PsiJavaCodeReferenceElement){
            reference = (PsiJavaCodeReferenceElement) reference.getParent();
        }

        final PsiManager mgr = element.getManager();
        final CodeStyleManager styleManager = mgr.getCodeStyleManager();
        styleManager.shortenClassReferences(reference);
    }
}
