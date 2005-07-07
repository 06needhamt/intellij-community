package com.siyeh.ig.style;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class TooBroadScopeInspection extends StatementInspection
{
	/** @noinspection PublicField for externalization*/
	public boolean m_allowConstructorAsInitializer = false;

    public String getID()
    {
        return "TooBroadScope";
    }

    public String getDisplayName()
    {
        return "Scope of variable is too broad";
    }

    public String getGroupDisplayName()
    {
        return GroupNames.DATA_FLOW_ISSUES;
    }

    @Nullable
	public JComponent createOptionsPanel() {
		// html allows text to wrap
		return new SingleCheckboxOptionsPanel(
				"<html>Allow initializer of variables to construct objects. Potentially " +
				"unsafe: quick fix may modify semantics if the constructor has " +
				"non-local side-effects.</html>",
				this, "m_allowConstructorAsInitializer");
	}

	@Nullable
    protected String buildErrorString(PsiElement location)
    {
        return "Scope of variable '#ref' is too broad #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location)
    {
        return new TooBroadScopeInspectionFix(location.getText());
    }

    private static class TooBroadScopeInspectionFix extends InspectionGadgetsFix
    {
        private String m_name;

        TooBroadScopeInspectionFix(String name){
            super();
            m_name = name;
        }

        public String getName()
        {
            return "Narrow scope of '" + m_name + '\'';
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException
        {
            final PsiElement variableIdentifier =
                    descriptor.getPsiElement();
            final PsiVariable variable = (PsiVariable)variableIdentifier.getParent();
            assert variable != null;
            final PsiManager manager = variable.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final PsiReference[] references =
                    searchHelper.findReferences(variable, variable.getUseScope(), false);
            final PsiReference firstReference = references[0];
            final PsiElement referenceElement = firstReference.getElement();
            PsiElement commonParent = ScopeUtils.getCommonParent(references);
            assert commonParent != null;
            final PsiExpression initializer = variable.getInitializer();
            if (initializer != null)
            {
                final PsiElement variableScope =
                        ScopeUtils.getParentOfTypes(variable, ScopeUtils.TYPES);
                assert variableScope != null;
                commonParent = ScopeUtils.moveOutOfLoops(commonParent, variableScope);
                if (commonParent == null)
                {
                    return;
                }
            }
            final PsiElement firstReferenceScope= ScopeUtils.getParentOfTypes(referenceElement,
                                                                              ScopeUtils.TYPES);
            assert firstReferenceScope != null;
            final PsiElement location;
            PsiDeclarationStatement newDeclaration;
            if (firstReferenceScope.equals(commonParent))
            {
                location = referenceElement;
                newDeclaration = moveDeclarationToLocation(variable, location);
            }
            else
            {
                final PsiElement commonParentChild =
                        ScopeUtils.getChildWhichContainsElement(commonParent, referenceElement);
                assert commonParentChild != null;
                location = ScopeUtils.findInsertionPoint(commonParentChild, variable);
                assert location != null;
                newDeclaration = createNewDeclaration(variable, initializer);
                newDeclaration =
                (PsiDeclarationStatement)commonParent.addAfter(newDeclaration,
                                                               location);
            }

            removeOldVariable(variable);
            highlightElement(newDeclaration);
        }

        private static void removeOldVariable(@NotNull PsiVariable variable)
                throws IncorrectOperationException
        {
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)variable.getParent();
            assert declaration != null;
            final PsiElement[] declaredElements = declaration.getDeclaredElements();
            if (declaredElements.length == 1)
            {
                declaration.delete();
            }
            else
            {
                variable.delete();
            }
        }

        private static PsiDeclarationStatement createNewDeclaration(
                @NotNull PsiVariable variable,
                @Nullable PsiExpression initializer)
                throws IncorrectOperationException
        {

            final PsiManager manager = variable.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiDeclarationStatement newDeclaration =
                    factory.createVariableDeclarationStatement(
                            variable.getName(), variable.getType(), initializer, false);
            final PsiLocalVariable newVariable =
                    (PsiLocalVariable)newDeclaration.getDeclaredElements()[0];
            final PsiModifierList newModifierList = newVariable.getModifierList();

            final PsiModifierList modifierList = variable.getModifierList();
            if (modifierList.hasExplicitModifier(PsiModifier.FINAL))
            {
                newModifierList.setModifierProperty(PsiModifier.FINAL, true);
            }
            else
            {
                // remove final when PsiDeclarationFactory adds one by mistake
                newModifierList.setModifierProperty(PsiModifier.FINAL, false);
            }

            final PsiAnnotation[] annotations = modifierList.getAnnotations();
            for (PsiAnnotation annotation : annotations)
            {
                newModifierList.add(annotation);
            }
            return newDeclaration;
        }

        private static void highlightElement(@NotNull PsiElement element)
        {
            final Project project = element.getProject();
            final FileEditorManager editorManager = FileEditorManager.getInstance(project);
            final HighlightManager highlightManager = HighlightManager.getInstance(project);
            final EditorColorsManager editorColorsManager = EditorColorsManager.getInstance();

            final Editor editor = editorManager.getSelectedTextEditor();
            final EditorColorsScheme globalScheme = editorColorsManager.getGlobalScheme();
            final TextAttributes textattributes =
                    globalScheme .getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
            final PsiElement[] elements = new PsiElement[]{element};
            highlightManager.addOccurrenceHighlights(editor, elements, textattributes, true, null);

            final WindowManager windowManager = WindowManager.getInstance();
            final StatusBar statusBar = windowManager.getStatusBar(project);
            statusBar.setInfo("Press Escape to remove the highlighting");
        }

        private PsiDeclarationStatement moveDeclarationToLocation(@NotNull PsiVariable variable,
                                                                  @NotNull PsiElement location
        )
                throws IncorrectOperationException
        {
            PsiStatement statement =
                    PsiTreeUtil.getParentOfType(location, PsiStatement.class, false);
            assert statement != null;
            PsiElement statementParent = statement.getParent();
            while (statementParent instanceof PsiStatement &&
                   !(statementParent instanceof PsiForStatement))
            {
                statement = (PsiStatement)statementParent;
                statementParent = statement.getParent();
            }
            assert statementParent != null;

            final PsiExpression initializer = variable.getInitializer();
            if (initializer == null && statement instanceof PsiExpressionStatement)
            {
                final PsiExpressionStatement expressionStatement =
                        (PsiExpressionStatement)statement;
                final PsiExpression expression = expressionStatement.getExpression();
                if (expression instanceof PsiAssignmentExpression)
                {
                    final PsiAssignmentExpression assignmentExpression =
                            (PsiAssignmentExpression)expression;
                    final PsiExpression lExpression = assignmentExpression.getLExpression();
                    if (location.equals(lExpression))
                    {
                        PsiDeclarationStatement newDeclaration= createNewDeclaration(variable,
                                                                                     assignmentExpression.getRExpression());
                        newDeclaration = (PsiDeclarationStatement)statementParent
                                .addBefore(newDeclaration,
                                           statement);
                        final PsiElement parent = assignmentExpression.getParent();
                        assert parent != null;
                        parent.delete();
                        return newDeclaration;
                    }
                }
            }

            PsiDeclarationStatement newDeclaration =
                    createNewDeclaration(variable, initializer);
            if (statement instanceof PsiForStatement)
            {
                final PsiForStatement forStatement = (PsiForStatement)statement;
                final PsiStatement initialization = forStatement.getInitialization();
                newDeclaration = (PsiDeclarationStatement)
                        forStatement.addBefore(newDeclaration, initialization);
                initialization.delete();
                return newDeclaration;
            }
            else
            {
                return (PsiDeclarationStatement)
                        statementParent.addBefore(newDeclaration, statement);
            }
        }
    }

    public BaseInspectionVisitor buildVisitor()
    {
        return new TooBroadScopeVisitor();
    }

	private class TooBroadScopeVisitor extends StatementInspectionVisitor
    {

        public void visitVariable(@NotNull PsiVariable variable)
        {
            super.visitVariable(variable);
            if (!(variable instanceof PsiLocalVariable))
            {
                return;
            }
            final PsiExpression initializer = variable.getInitializer();
			if (!isMoveable(initializer))
			{
                return;
            }
            final PsiElement variableScope =
                    ScopeUtils.getParentOfTypes(variable, ScopeUtils.TYPES);
            if (variableScope == null)
            {
                return;
            }
            final PsiManager manager = variable.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final PsiReference[] references =
                    searchHelper.findReferences(variable, variable.getUseScope(), false);
            if (references.length == 0)
            {
                return;
            }
            PsiElement commonParent = ScopeUtils.getCommonParent(references);
            if (commonParent == null)
            {
                return;
            }
            if (initializer != null)
            {
                commonParent = ScopeUtils.moveOutOfLoops(commonParent, variableScope);
                if (commonParent == null)
                {
                    return;
                }
            }

            if (PsiTreeUtil.isAncestor(variableScope, commonParent, true))
            {
                registerVariableError(variable);
                return;
            }
            if (commonParent instanceof PsiForStatement)
            {
                return;
            }

            final PsiReference firstReference = references[0];
            final PsiElement referenceElement = firstReference.getElement();
            if (referenceElement == null)
            {
                return;
            }
            final PsiElement blockChild =
                    ScopeUtils.getChildWhichContainsElement(variableScope, referenceElement);
            if (blockChild == null)
            {
                return;
            }
            final PsiElement insertionPoint = ScopeUtils.findInsertionPoint(blockChild, variable);
            if (insertionPoint == null)
            {
                if (initializer != null)
                {
                return;
            }
                if (!(blockChild instanceof PsiExpressionStatement))
                {
                    return;
                }
                final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)blockChild;
                final PsiExpression expression = expressionStatement.getExpression();
                if (!(expression instanceof PsiAssignmentExpression))
                {
                    return;
                }
                final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
                final PsiExpression lExpression = assignmentExpression.getLExpression();
                if (!lExpression.equals(firstReference))
                {
                    return;
                }
            }
            registerVariableError(variable);
        }

		private boolean isMoveable(PsiExpression expression)
		{
			if (expression == null)
			{
				return true;
    }
			if (PsiUtil.isConstantExpression(expression))
			{
				return true;
}			if (expression instanceof PsiNewExpression)
			{
				final PsiNewExpression newExpression = (PsiNewExpression)expression;
				if (newExpression.getArrayDimensions().length > 0)
				{
					return true;
				}
				final PsiArrayInitializerExpression arrayInitializer =
						newExpression.getArrayInitializer();
				boolean result = true;
				if (arrayInitializer != null)
				{
					final PsiExpression[] initializers = arrayInitializer.getInitializers();
					for (final PsiExpression initializerExpression : initializers)
					{
						result &= isMoveable(initializerExpression);
					}
				}
				else if (!m_allowConstructorAsInitializer)
				{
					return false;
				}

				final PsiExpressionList argumentList = newExpression.getArgumentList();
				if (argumentList == null)
				{
					return result;
				}
				final PsiExpression[] expressions = argumentList.getExpressions();
				for (final PsiExpression argumentExpression : expressions)
				{
					result &= isMoveable(argumentExpression);
				}
				return result;
			}
			return false;
		}
	}
}