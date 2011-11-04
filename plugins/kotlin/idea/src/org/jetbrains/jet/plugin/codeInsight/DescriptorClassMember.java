package org.jetbrains.jet.plugin.codeInsight;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.MemberChooserObject;
import com.intellij.ui.SimpleColoredComponent;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.resolve.DescriptorRenderer;

import javax.swing.*;

/**
 * @author yole
 */
public class DescriptorClassMember implements ClassMember {
    private final DeclarationDescriptor myDescriptor;

    public DescriptorClassMember(DeclarationDescriptor descriptor) {
        myDescriptor = descriptor;
    }

    @Override
    public MemberChooserObject getParentNodeDelegate() {
        final DeclarationDescriptor parent = myDescriptor.getContainingDeclaration();
        return new DescriptorClassMember(parent);
    }

    @Override
    public void renderTreeNode(SimpleColoredComponent component, JTree tree) {
        component.append(getText());
    }

    @Override
    public String getText() {
        return DescriptorRenderer.TEXT.render(myDescriptor);
    }

    public DeclarationDescriptor getDescriptor() {
        return myDescriptor;
    }
}
