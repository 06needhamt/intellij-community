package org.jetbrains.jet.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.JetNodeTypes;

import java.util.List;

/**
 * @author abreslav
 */
public class JetWhenExpression extends JetExpression {
    public JetWhenExpression(@NotNull ASTNode node) {
        super(node);
    }

    @NotNull
    public List<JetWhenEntry> getEntries() {
        return findChildrenByType(JetNodeTypes.WHEN_ENTRY);
    }

    @Override
    public void accept(JetVisitor visitor) {
        visitor.visitWhenExpression(this);
    }
}
