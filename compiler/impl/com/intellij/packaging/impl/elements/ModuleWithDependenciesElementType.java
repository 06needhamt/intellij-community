package com.intellij.packaging.impl.elements;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

/**
 * @author nik
 */
public class ModuleWithDependenciesElementType extends PackagingElementType<ModuleWithDependenciesPackagingElement> {
  public static final ModuleWithDependenciesElementType MODULE_WITH_DEPENDENCIES_TYPE = new ModuleWithDependenciesElementType();

  public ModuleWithDependenciesElementType() {
    super("module-with-dependencies", "Module With Dependencies");
  }

  @Override
  public Icon getCreateElementIcon() {
    return IconLoader.getIcon("/nodes/ModuleOpen.png");
  }

  @NotNull
  public List<? extends ModuleWithDependenciesPackagingElement> createWithDialog(@NotNull PackagingEditorContext context,
                                                                                 Artifact artifact,
                                                                                 CompositePackagingElement<?> parent) {
    final List<Module> modules = ModuleOutputElementType.chooseModules(context, artifact);
    final List<ModuleWithDependenciesPackagingElement> elements = new ArrayList<ModuleWithDependenciesPackagingElement>();
    for (Module module : modules) {
      elements.add(new ModuleWithDependenciesPackagingElement(module.getName()));
    }
    return elements;
  }

  @NotNull
  public ModuleWithDependenciesPackagingElement createEmpty() {
    return new ModuleWithDependenciesPackagingElement();
  }
}
