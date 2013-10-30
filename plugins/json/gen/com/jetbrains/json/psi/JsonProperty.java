// This is a generated file. Not intended for manual editing.
package com.jetbrains.json.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import com.jetbrains.json.psi.impl.JsonPropertyImpl;

public interface JsonProperty extends PsiElement {

  @NotNull
  JsonPropertyName getPropertyName();

  @Nullable
  JsonPropertyValue getPropertyValue();

  @NotNull
  String getName();

  @Nullable
  JsonPropertyValue getValue();

  void delete();

}
