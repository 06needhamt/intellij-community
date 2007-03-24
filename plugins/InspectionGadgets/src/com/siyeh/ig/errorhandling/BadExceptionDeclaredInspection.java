/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.ui.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.*;

public class BadExceptionDeclaredInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public String exceptionsString =
      "java.lang.Throwable" + "," +
      "java.lang.Exception" + "," +
      "java.lang.Error" + "," +
      "java.lang.RuntimeException" + "," +
      "java.lang.NullPointerException" + "," +
      "java.lang.ClassCastException" + "," +
      "java.lang.ArrayIndexOutOfBoundsException";

    /** @noinspection PublicField*/
    public boolean ignoreTestCases = false;
    private final List<String> exceptionList = new ArrayList<String>(32);

    public BadExceptionDeclaredInspection() {
        parseExceptionsString();
    }

    @NotNull
    public String getID(){
        return "ProhibitedExceptionDeclared";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "bad.exception.declared.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "bad.exception.declared.problem.descriptor");
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseExceptionsString();
    }

    private void parseExceptionsString(){
        final String[] strings = exceptionsString.split(",");
        exceptionList.clear();
        exceptionList.addAll(Arrays.asList(strings));
    }

    public void writeSettings(Element element) throws WriteExternalException{
        formatExceptionsString();
        super.writeSettings(element);
    }

    private void formatExceptionsString(){
        final StringBuilder buffer = new StringBuilder();
        final int size = exceptionList.size();
        if (size > 0) {
            buffer.append(exceptionList.get(0));
            for (int i = 1; i < size; i++) {
                buffer.append(',');
                buffer.append(exceptionList.get(i));
            }
        }
        exceptionsString = buffer.toString();
    }

    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    public BaseInspectionVisitor buildVisitor(){
        return new BadExceptionDeclaredVisitor();
    }

    private class BadExceptionDeclaredVisitor extends BaseInspectionVisitor{

        private final Set<String> exceptionSet = new HashSet(exceptionList);

        public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            if(ignoreTestCases){
                final PsiClass containingClass = method.getContainingClass();
                if(ClassUtils.isSubclass(containingClass,
                        "junit.framework.Test")){
                    return;
                }
            }
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] references =
                    throwsList.getReferenceElements();
            for(PsiJavaCodeReferenceElement reference : references){
                final PsiElement element = reference.resolve();
                if (!(element instanceof PsiClass)) {
                    continue;
                }
                final PsiClass thrownClass = (PsiClass)element;
                final String qualifiedName = thrownClass.getQualifiedName();
                if (qualifiedName != null &&
                        exceptionSet.contains(qualifiedName)) {
                    registerError(reference);
                }
            }
        }
    }

    private class Form{
        
        JPanel contentPanel;
        JButton addButton;
        JButton removeButton;
        JCheckBox ignoreTestCasesCheckBox;
        IGTable table;

        Form(){
            super();
            addButton.setAction(new AddAction(table));
            removeButton.setAction(new RemoveAction(table));
            ignoreTestCasesCheckBox.setSelected(ignoreTestCases);
            ignoreTestCasesCheckBox.setAction(new ToggleAction(
                    InspectionGadgetsBundle.message(
                            "bad.exception.declared.ignore.exceptions.declared.in.junit.test.cases.option"),
                    BadExceptionDeclaredInspection.this, "ignoreTestCases"));
        }

        private void createUIComponents() {
            table = new IGTable(new ListWrappingTableModel(exceptionList,
                    InspectionGadgetsBundle.message(
                            "exception.class.column.name")));
        }

        public JComponent getContentPanel(){
            return contentPanel;
        }
    }
}