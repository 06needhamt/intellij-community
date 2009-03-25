package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.util.ProcessingContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;


/**
 * @author Maxim.Mossienko
 */
public interface CustomizableReferenceProvider {
  final class CustomizationKey<T> {
    private final String myOptionDescription;

    public CustomizationKey(@NonNls String optionDescription) {
      myOptionDescription = optionDescription;
    }

    public String toString() { return myOptionDescription; }

    public T getValue(@Nullable Map<CustomizationKey,Object> options) {
      return options == null ? null : (T)options.get(this);
    }

    public boolean getBooleanValue(@Nullable Map<CustomizationKey,Object> options) {
      if (options == null) {
        return false;
      }
      final Boolean o = (Boolean)options.get(this);
      return o != null && o.booleanValue();
    }

    public void putValue(Map<CustomizationKey,Object> options, T value) {
      options.put(this, value);
    }
  }

  void setOptions(@Nullable Map<CustomizationKey,Object> options);
  @Nullable Map<CustomizationKey,Object> getOptions();

  @NotNull
  public abstract PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext matchingContext);
}
