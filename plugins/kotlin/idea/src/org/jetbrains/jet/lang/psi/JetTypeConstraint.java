package org.jetbrains.jet.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.JetNodeTypes;
import org.jetbrains.jet.lexer.JetTokens;

/**
 * @author max
 */
public class JetTypeConstraint extends JetElement {
    public JetTypeConstraint(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public void accept(JetVisitor visitor) {
        visitor.visitTypeConstraint(this);
    }

    public boolean isClassObjectContraint() {
        return findChildByType(JetTokens.CLASS_KEYWORD) != null &&
                findChildByType(JetTokens.OBJECT_KEYWORD) != null;
    }

    @Nullable
    public JetTypeReference getSubjectTypeReference() {
        ASTNode node = getNode().getFirstChildNode();
        while (node != null) {
            IElementType tt = node.getElementType();
            if (tt == JetTokens.COLON) break;
            if (tt == JetNodeTypes.TYPE_REFERENCE) return (JetTypeReference) node.getPsi();
            node = node.getTreeNext();
        }

        return null;
    }

    @Nullable
    public JetTypeReference getExtendsTypeReference() {
        boolean passedColon = false;
        ASTNode node = getNode().getFirstChildNode();
        while (node != null) {
            IElementType tt = node.getElementType();
            if (tt == JetTokens.COLON) passedColon = true;
            if (passedColon && tt == JetNodeTypes.TYPE_REFERENCE) return (JetTypeReference) node.getPsi();
            node = node.getTreeNext();
        }

        return null;
    }
}
