package org.jetbrains.jet.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lexer.JetTokens;

/**
 * @author max
 */
public class JetImportDirective extends JetElement {
    public JetImportDirective(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public void accept(@NotNull JetVisitor visitor) {
        visitor.visitImportDirective(this);
    }

    public String getImportedName() {
        StringBuilder answer = new StringBuilder();
        ASTNode childNode = getNode().getFirstChildNode();
        while (childNode != null) {
            IElementType tt = childNode.getElementType();
            if (tt == JetTokens.MAP || tt == JetTokens.AS_KEYWORD) break;
            if (tt == JetTokens.IDENTIFIER || tt == JetTokens.DOT) {
                answer.append(childNode.getText());
            }
            childNode = childNode.getTreeNext();
        }
        return answer.toString();
    }

    @Nullable
    public String getAliasName() {
        boolean asPassed = false;
        ASTNode childNode = getNode().getFirstChildNode();
        while (childNode != null) {
            IElementType tt = childNode.getElementType();
            if (tt == JetTokens.AS_KEYWORD) asPassed = true;
            if (asPassed && tt == JetTokens.IDENTIFIER) {
                return childNode.getText();
            }

            childNode = childNode.getTreeNext();
        }

        return null;
    }

    public boolean isAllUnder() {
        return getNode().findChildByType(JetTokens.MAP) != null;
    }
}
