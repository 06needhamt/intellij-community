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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.psi.jsp.JspFile;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class EmptySynchronizedStatementInspection extends StatementInspection {
    public String getDisplayName() {
        return "Empty 'synchronized' statement";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Empty #ref statement #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptySynchronizedStatementVisitor();
    }

    private static class EmptySynchronizedStatementVisitor extends StatementInspectionVisitor {

        public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
            super.visitSynchronizedStatement(statement);

            if(statement.getContainingFile() instanceof JspFile){
                return;
            }
            final PsiCodeBlock body = statement.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements.length > 0) {
                return;
            }
            registerStatementError(statement);
        }

    }

}
