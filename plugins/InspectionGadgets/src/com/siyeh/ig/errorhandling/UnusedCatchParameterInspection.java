package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class UnusedCatchParameterInspection extends StatementInspection{
    /** @noinspection PublicField*/
    public boolean m_ignoreCatchBlocksWithComments = false;
    /** @noinspection PublicField*/
    public boolean m_ignoreTestCases = false;



    public String getDisplayName(){
        return "Unused 'catch' parameter";
    }

    public String getGroupDisplayName(){
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Unused catch parameter #ref #loc";
    }

    public JComponent createOptionsPanel(){
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);
        final JCheckBox commentsCheckBox =
                new JCheckBox("Ignore catch blocks containing comments",
                              m_ignoreCatchBlocksWithComments);
        final ButtonModel commentsModel = commentsCheckBox.getModel();
        commentsModel.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent e){
                m_ignoreCatchBlocksWithComments = commentsModel.isSelected();
            }
        });
        final JCheckBox testCaseCheckBox =
                new JCheckBox("Ignore empty catch blocks in JUnit test cases",
                              m_ignoreTestCases);
        final ButtonModel model = testCaseCheckBox.getModel();
        model.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent e){
                m_ignoreTestCases = model.isSelected();
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(commentsCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(testCaseCheckBox, constraints);
        return panel;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new EmptyCatchBlockVisitor();
    }

    private class EmptyCatchBlockVisitor extends StatementInspectionVisitor{

        public void visitTryStatement(@NotNull PsiTryStatement statement){
            super.visitTryStatement(statement);
            if(m_ignoreTestCases){
                final PsiClass aClass =
                        ClassUtils.getContainingClass(statement);
                if(aClass != null &&
                           ClassUtils.isSubclass(aClass,
                                                 "junit.framework.TestCase")){
                    return;
                }
            }

            final PsiCatchSection[] catchSections = statement.getCatchSections();
            for(PsiCatchSection catchSection : catchSections){
                checkCatchSection(catchSection);
            }
        }

        private void checkCatchSection(PsiCatchSection section){
            final PsiParameter param = section.getParameter();
            final PsiCodeBlock block = section.getCatchBlock();
            if(param == null || block == null){
                return;
            }
            final String paramName = param.getName();
            if("ignore".equals(paramName)|| "ignored".equals(paramName))
            {
                return;
            }
            if(m_ignoreCatchBlocksWithComments){
                final PsiElement[] children = block.getChildren();
                for(final PsiElement child : children){
                    if(child instanceof PsiComment){
                        return;
                    }
                }
            }
            final CatchParameterUsedVisitor visitor =
                    new CatchParameterUsedVisitor(param);
            block.accept(visitor);

            if(!visitor.isUsed()){
                registerVariableError(param);
            }
        }
    }
}
