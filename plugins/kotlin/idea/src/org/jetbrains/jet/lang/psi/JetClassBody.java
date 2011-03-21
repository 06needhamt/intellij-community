package org.jetbrains.jet.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author max
 */
public class JetClassBody extends JetElement {
    public JetClassBody(@NotNull ASTNode node) {
        super(node);
    }

    public List<JetDeclaration> getDeclarations() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, JetDeclaration.class);
    }

    public List<JetConstructor> getSecondaryConstructors() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, JetConstructor.class);
    }

    @Override
    public void accept(@NotNull JetVisitor visitor) {
        visitor.visitClassBody(this);
    }
}
