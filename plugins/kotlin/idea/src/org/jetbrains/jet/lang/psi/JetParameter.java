package org.jetbrains.jet.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.JetNodeTypes;
import org.jetbrains.jet.lexer.JetTokens;

/**
 * @author max
 */
public class JetParameter extends JetNamedDeclaration {
    public JetParameter(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public void accept(JetVisitor visitor) {
        visitor.visitParameter(this);
    }

    @Nullable
    public JetTypeReference getTypeReference() {
        return (JetTypeReference) findChildByType(JetNodeTypes.TYPE_REFERENCE);
    }

    @Nullable
    public JetExpression getDefaultValue() {
        boolean passedEQ = false;
        ASTNode child = getNode().getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == JetTokens.EQ) passedEQ = true;
            if (passedEQ && child.getPsi() instanceof JetExpression) {
                return (JetExpression) child.getPsi();
            }
            child = child.getTreeNext();
        }

        return null;
    }

    public boolean isOut() {
        JetModifierList modifierList = getModifierList();
        return modifierList != null && modifierList.hasModifier(JetTokens.OUT_KEYWORD);
    }

    public boolean isRef() {
        JetModifierList modifierList = getModifierList();
        return modifierList != null && modifierList.hasModifier(JetTokens.REF_KEYWORD);
    }
}
