package org.jetbrains.jet.plugin.run;

import com.intellij.execution.*;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author yole
 */
public class JetRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> implements CommonJavaRunConfigurationParameters {
    public String MAIN_CLASS_NAME;
    public String VM_PARAMETERS;
    public String PROGRAM_PARAMETERS;
    public String WORKING_DIRECTORY;
    public boolean ALTERNATIVE_JRE_PATH_ENABLED;
    public String ALTERNATIVE_JRE_PATH;
    private Map<String,String> myEnvs = new LinkedHashMap<String, String>();
    public boolean PASS_PARENT_ENVS = true;

    public JetRunConfiguration(String name, RunConfigurationModule runConfigurationModule, ConfigurationFactory factory) {
        super(name, runConfigurationModule, factory);
        runConfigurationModule.init();
    }

    @Override
    public Collection<Module> getValidModules() {
        return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
    }

    @Override
    protected ModuleBasedConfiguration createInstance() {
        return new JetRunConfiguration(getName(), getConfigurationModule(), getFactory());
    }

    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        return new JetRunConfigurationEditor(getProject());
    }

    public void readExternal(final Element element) throws InvalidDataException {
        PathMacroManager.getInstance(getProject()).expandPaths(element);
        super.readExternal(element);

        JavaRunConfigurationExtensionManager.getInstance().readExternal(this, element);
        DefaultJDOMExternalizer.readExternal(this, element);
        readModule(element);
        EnvironmentVariablesComponent.readExternal(element, getEnvs());
    }

    public void writeExternal(final Element element) throws WriteExternalException {
        super.writeExternal(element);
        JavaRunConfigurationExtensionManager.getInstance().writeExternal(this, element);
        DefaultJDOMExternalizer.writeExternal(this, element);
        writeModule(element);
        EnvironmentVariablesComponent.writeExternal(element, getEnvs());
        PathMacroManager.getInstance(getProject()).collapsePathsRecursively(element);
    }

    public void setVMParameters(String value) {
      VM_PARAMETERS = value;
    }

    public String getVMParameters() {
      return VM_PARAMETERS;
    }

    public void setProgramParameters(String value) {
      PROGRAM_PARAMETERS = value;
    }

    public String getProgramParameters() {
      return PROGRAM_PARAMETERS;
    }

    public void setWorkingDirectory(String value) {
      WORKING_DIRECTORY = ExternalizablePath.urlValue(value);
    }

    public String getWorkingDirectory() {
      return ExternalizablePath.localPathValue(WORKING_DIRECTORY);
    }

    public void setPassParentEnvs(boolean passParentEnvs) {
      PASS_PARENT_ENVS = passParentEnvs;
    }

    @NotNull
    public Map<String, String> getEnvs() {
      return myEnvs;
    }

    public void setEnvs(@NotNull final Map<String, String> envs) {
      this.myEnvs = envs;
    }

    public boolean isPassParentEnvs() {
      return PASS_PARENT_ENVS;
    }

    @Override
    public String getRunClass() {
        return MAIN_CLASS_NAME;
    }

    @Override
    public String getPackage() {
        return null;
    }

    public boolean isAlternativeJrePathEnabled() {
       return ALTERNATIVE_JRE_PATH_ENABLED;
     }

     public void setAlternativeJrePathEnabled(boolean enabled) {
       ALTERNATIVE_JRE_PATH_ENABLED = enabled;
     }

     public String getAlternativeJrePath() {
       return ALTERNATIVE_JRE_PATH;
     }

     public void setAlternativeJrePath(String path) {
       ALTERNATIVE_JRE_PATH = path;
     }

    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment executionEnvironment) throws ExecutionException {
        final JavaCommandLineState state = new MyJavaCommandLineState(executionEnvironment);
        state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
        return state;
    }

    private class MyJavaCommandLineState extends JavaCommandLineState {
        protected MyJavaCommandLineState(@NotNull ExecutionEnvironment environment) {
            super(environment);
        }

        @Override
        protected JavaParameters createJavaParameters() throws ExecutionException {
            final JavaParameters params = new JavaParameters();
            final int classPathType = JavaParametersUtil.getClasspathType(getConfigurationModule(), MAIN_CLASS_NAME, false);
            JavaParametersUtil.configureModule(getConfigurationModule(), params, classPathType, null);
            params.setMainClass(MAIN_CLASS_NAME);
            return params;
        }
    }
}
