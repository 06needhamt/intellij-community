package org.jetbrains.jet.lang;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.resolve.ClassDescriptorResolver;
import org.jetbrains.jet.lang.resolve.OverloadResolver;
import org.jetbrains.jet.lang.types.BindingTrace;
import org.jetbrains.jet.lang.types.JetStandardLibrary;
import org.jetbrains.jet.lang.types.JetTypeChecker;
import org.jetbrains.jet.lang.types.JetTypeInferrer;

/**
 * @author abreslav
 */
public class JetSemanticServices {
    public static JetSemanticServices createSemanticServices(JetStandardLibrary standardLibrary, ErrorHandler errorHandler) {
        return new JetSemanticServices(standardLibrary, errorHandler);
    }

    public static JetSemanticServices createSemanticServices(Project project, ErrorHandler errorHandler) {
        return new JetSemanticServices(JetStandardLibrary.getJetStandardLibrary(project), errorHandler);
    }

    private final JetTypeInferrer typeInferrer;
    private final JetStandardLibrary standardLibrary;
    private final JetTypeChecker typeChecker;
    private final OverloadResolver overloadResolver;

    private final ErrorHandler errorHandler;

    private JetSemanticServices(JetStandardLibrary standardLibrary, ErrorHandler errorHandler) {
        this.standardLibrary = standardLibrary;
        this.errorHandler = errorHandler;
        this.typeInferrer = new JetTypeInferrer(BindingTrace.DUMMY, this);
        this.typeChecker = new JetTypeChecker(standardLibrary);
        this.overloadResolver = new OverloadResolver(typeChecker);
    }

    @NotNull
    public JetStandardLibrary getStandardLibrary() {
        return standardLibrary;
    }

    @NotNull
    public JetTypeInferrer getTypeInferrer() {
        return typeInferrer;
    }

    @NotNull
    public ClassDescriptorResolver getClassDescriptorResolver(BindingTrace trace) {
        return new ClassDescriptorResolver(this, trace);
    }

    @NotNull
    public JetTypeInferrer getTypeInferrer(BindingTrace trace) {
        return new JetTypeInferrer(trace, this);
    }

    @NotNull
    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    @NotNull
    public JetTypeChecker getTypeChecker() {
        return typeChecker;
    }

    @NotNull
    public OverloadResolver getOverloadResolver() {
        return overloadResolver;
    }
}
