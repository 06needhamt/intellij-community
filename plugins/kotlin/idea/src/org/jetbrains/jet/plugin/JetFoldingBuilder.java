package org.jetbrains.jet.plugin;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.JetNodeTypes;
import org.jetbrains.jet.lexer.JetTokens;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class JetFoldingBuilder implements FoldingBuilder, DumbAware {
    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {
        List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
        appendDescriptors(node, document, descriptors);
        return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
    }

    private void appendDescriptors(ASTNode node, Document document, List<FoldingDescriptor> descriptors) {
        TextRange textRange = node.getTextRange();
        IElementType type = node.getElementType();
        if ((type == JetNodeTypes.BLOCK || type == JetNodeTypes.CLASS_BODY) &&
            !isOneLine(textRange, document)) {
            descriptors.add(new FoldingDescriptor(node, textRange));
        } else if (type == JetNodeTypes.PARENTHESIZED && node.getFirstChildNode().getElementType() == JetTokens.IDE_TEMPLATE_START) {
            if (node.getTreeParent().getTextLength() > 0) {
                descriptors.add(new FoldingDescriptor(node, textRange, null, Collections.<Object>emptySet(), true));
            }
        }
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
          appendDescriptors(child, document, descriptors);
          child = child.getTreeNext();
        }
    }

    private static boolean isOneLine(TextRange textRange, Document document) {
        return document.getLineNumber(textRange.getStartOffset()) == document.getLineNumber(textRange.getEndOffset());
    }

    @Override
    public String getPlaceholderText(@NotNull ASTNode astNode) {
        if (astNode.getElementType() == JetNodeTypes.PARENTHESIZED && astNode.getFirstChildNode().getElementType() == JetTokens.IDE_TEMPLATE_START) {
            ASTNode placeholderExpression = astNode.getFirstChildNode().getTreeNext();
            return placeholderExpression.getText();
        }
        return "{...}";
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode astNode) {
        return false;
    }
}
