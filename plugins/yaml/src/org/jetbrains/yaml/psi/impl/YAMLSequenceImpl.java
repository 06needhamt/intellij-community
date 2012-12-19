package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequence;

/**
 * @author oleg
 */
public class YAMLSequenceImpl extends YAMLPsiElementImpl implements YAMLSequence {
  public YAMLSequenceImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @NotNull
  public YAMLKeyValue[] getKeysValues() {
    YAMLKeyValue[] result = PsiTreeUtil.getChildrenOfType(this, YAMLKeyValue.class);
    return result != null ? result : YAMLKeyValue.EMPTY_ARRAY;
  }

  @Override
  public String toString() {
    return "YAML sequence";
  }
}