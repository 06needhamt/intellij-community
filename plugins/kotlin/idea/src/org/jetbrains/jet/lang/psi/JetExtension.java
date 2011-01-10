package org.jetbrains.jet.lang.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.JetNodeTypes;

import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class JetExtension extends JetTypeParameterListOwner {
    public JetExtension(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public void accept(@NotNull JetVisitor visitor) {
        visitor.visitExtension(this);
    }

    @NotNull
    public List<JetDeclaration> getDeclarations() {
        JetClassBody body = (JetClassBody) findChildByType(JetNodeTypes.CLASS_BODY);
        if (body == null) return Collections.emptyList();

        return body.getDeclarations();
    }

    @Nullable(IF_NOT_PARSED)
    public JetTypeReference getTargetTypeRef() {
        return (JetTypeReference) findChildByType(JetNodeTypes.TYPE_REFERENCE);
    }
}
