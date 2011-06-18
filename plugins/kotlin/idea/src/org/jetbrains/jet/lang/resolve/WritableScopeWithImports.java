package org.jetbrains.jet.lang.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.ErrorHandler;
import org.jetbrains.jet.lang.descriptors.ClassifierDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionGroup;
import org.jetbrains.jet.lang.descriptors.NamespaceDescriptor;
import org.jetbrains.jet.lang.descriptors.VariableDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author abreslav
 */
public abstract class WritableScopeWithImports extends JetScopeAdapter implements WritableScope {

    private String debugName;

    @Nullable
    private List<JetScope> imports;
    private WritableScope currentIndividualImportScope;
    protected final ErrorHandler errorHandler;

    public WritableScopeWithImports(@NotNull JetScope scope, @NotNull ErrorHandler errorHandler) {
        super(scope);
        this.errorHandler = errorHandler;
    }

    public WritableScopeWithImports setDebugName(@NotNull String debugName) {
        assert this.debugName == null : this.debugName;
        this.debugName = debugName;
        return this;
    }

    @NotNull
    protected final List<JetScope> getImports() {
        if (imports == null) {
            imports = new ArrayList<JetScope>();
        }
        return imports;
    }

    @Override
    public void importScope(@NotNull JetScope imported) {
        getImports().add(0, imported);
        currentIndividualImportScope = null;
    }

    @Override
    public VariableDescriptor getVariable(@NotNull String name) {
        // Meaningful lookup goes here
        for (JetScope imported : getImports()) {
            VariableDescriptor importedDescriptor = imported.getVariable(name);
            if (importedDescriptor != null) {
                return importedDescriptor;
            }
        }
        return null;
    }

    @NotNull
    @Override
    public FunctionGroup getFunctionGroup(@NotNull String name) {
        for (JetScope imported : getImports()) {
            FunctionGroup importedDescriptor = imported.getFunctionGroup(name);
            if (!importedDescriptor.isEmpty()) {
                return importedDescriptor;
            }
        }
        return FunctionGroup.EMPTY;
    }

    @Override
    public ClassifierDescriptor getClassifier(@NotNull String name) {
        for (JetScope imported : getImports()) {
            ClassifierDescriptor importedClassifier = imported.getClassifier(name);
            if (importedClassifier != null) {
                return importedClassifier;
            }
        }
        return null;
    }

    @Override
    public NamespaceDescriptor getNamespace(@NotNull String name) {
        for (JetScope imported : getImports()) {
            NamespaceDescriptor importedDescriptor = imported.getNamespace(name);
            if (importedDescriptor != null) {
                return importedDescriptor;
            }
        }
        return null;
    }

    public void importClassifierAlias(@NotNull String importedClassifierName, @NotNull ClassifierDescriptor classifierDescriptor) {
        if (currentIndividualImportScope == null) {
            WritableScopeImpl writableScope = new WritableScopeImpl(JetScope.EMPTY, getContainingDeclaration(), ErrorHandler.DO_NOTHING).setDebugName("Individual import scope");
            importScope(writableScope);
            currentIndividualImportScope = writableScope;
        }
        currentIndividualImportScope.addClassifierAlias(importedClassifierName, classifierDescriptor);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this)) + " " + debugName + " for " + getContainingDeclaration();
    }

}
