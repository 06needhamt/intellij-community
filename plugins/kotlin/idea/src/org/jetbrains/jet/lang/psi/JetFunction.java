package org.jetbrains.jet.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.JetNodeTypes;
import org.jetbrains.jet.lexer.JetTokens;

import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class JetFunction extends JetTypeParameterListOwner {
    public JetFunction(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public void accept(@NotNull JetVisitor visitor) {
        visitor.visitFunction(this);
    }

    @Nullable @IfNotParsed
    public JetParameterList getValueParameterList() {
        return (JetParameterList) findChildByType(JetNodeTypes.VALUE_PARAMETER_LIST);
    }

    @NotNull
    public List<JetParameter> getValueParameters() {
        JetParameterList list = getValueParameterList();
        return list != null ? list.getParameters() : Collections.<JetParameter>emptyList();
    }

    @Nullable
    public JetExpression getBodyExpression() {
        return findChildByClass(JetExpression.class);
    }


    @Nullable
    public JetTypeReference getReceiverTypeRef() {
        PsiElement child = getFirstChild();
        while (child != null) {
            IElementType tt = child.getNode().getElementType();
            if (tt == JetTokens.LPAR || tt == JetTokens.COLON) break;
            if (child instanceof JetTypeReference) {
                return (JetTypeReference) child;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    @Nullable
    public JetTypeReference getReturnTypeRef() {
        boolean colonPassed = false;
        PsiElement child = getFirstChild();
        while (child != null) {
            IElementType tt = child.getNode().getElementType();
            if (tt == JetTokens.COLON) {
                colonPassed = true;
            }
            if (colonPassed && child instanceof JetTypeReference) {
                return (JetTypeReference) child;
            }
            child = child.getNextSibling();
        }

        return null;
    }

    public boolean hasBlockBody() {
        return findChildByType(JetTokens.EQ) != null;
    }
}
