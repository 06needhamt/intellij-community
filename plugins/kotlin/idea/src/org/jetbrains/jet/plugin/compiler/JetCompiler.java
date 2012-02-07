package org.jetbrains.jet.plugin.compiler;

import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.*;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.plugin.JetFileType;

import java.io.*;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.compiler.CompilerMessageCategory.*;

/**
 * @author yole
 */
public class JetCompiler implements TranslatingCompiler {
    private static final Logger LOG = Logger.getInstance("#org.jetbrains.jet.plugin.compiler.JetCompiler");

    private static final boolean RUN_OUT_OF_PROCESS = false;

    @Override
    public boolean isCompilableFile(VirtualFile virtualFile, CompileContext compileContext) {
        return virtualFile.getFileType() instanceof JetFileType;
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Jet Language Compiler";
    }

    @Override
    public boolean validateConfiguration(CompileScope compileScope) {
        return true;
    }

    @Override
    public void compile(final CompileContext compileContext, Chunk<Module> moduleChunk, final VirtualFile[] virtualFiles, OutputSink outputSink) {
        if (virtualFiles.length == 0) return;

        final Module module = compileContext.getModuleByFile(virtualFiles[0]);
        final VirtualFile outputDir = compileContext.getModuleOutputDirectory(module);
        if (outputDir == null) {
            compileContext.addMessage(ERROR, "[Internal Error] No output directory", "", -1, -1);
            return;
        }

        File kotlinHome = PathUtil.getDefaultCompilerPath();
        if (kotlinHome == null) {
            compileContext.addMessage(ERROR, "Cannot find kotlinc home. Make sure plugin is properly installed", "", -1, -1);
            return;
        }
        
        
        StringBuilder script = new StringBuilder();

        script.append("import kotlin.modules.*\n");
        script.append("fun project() {\n");
        script.append("module(\"" + moduleChunk.getNodes().iterator().next().getName() + "\") {\n");

        for (VirtualFile sourceFile : virtualFiles) {
            script.append("sources += \"" + path(sourceFile) + "\"\n");
        }

        ModuleChunk chunk = new ModuleChunk((CompileContextEx) compileContext, moduleChunk, Collections.<Module, List<VirtualFile>>emptyMap());

        // TODO: have a bootclasspath in script API
        for (VirtualFile root : chunk.getCompilationBootClasspathFiles()) {
            script.append("classpath += \"" + path(root) + "\"\n");
        }

        for (VirtualFile root : chunk.getCompilationClasspathFiles()) {
            script.append("classpath += \"" + path(root) + "\"\n");
        }

        script.append("}\n");
        script.append("}\n");

        File scriptFile = new File(path(outputDir), "script.kts");
        try {
            FileUtil.writeToFile(scriptFile, script.toString());
        } catch (IOException e) {
            compileContext.addMessage(ERROR, "[Internal Error] Cannot write script to " + scriptFile.getAbsolutePath(), "", -1, -1);
            return;
        }

        if (RUN_OUT_OF_PROCESS) {
            runOutOfProcess(compileContext, outputDir, kotlinHome, scriptFile);
        }
        else {
            runInProcess(compileContext, outputDir, kotlinHome, scriptFile);
        }
    }

    private static List<File> kompilerClasspath(File kotlinHome, CompileContext context) {
        File libs = new File(kotlinHome, "lib");

        if (!libs.exists() || libs.isFile()) {
            context.addMessage(ERROR, "Broken compiler at '" + libs.getAbsolutePath() + "'. Make sure plugin is properly installed", "", -1, -1);
            return Collections.emptyList();
        }


        ArrayList<File> answer = new ArrayList<File>();
        File[] jars = libs.listFiles();
        if (jars != null) {
            for (File jar : jars) {
                if (jar.isFile() && jar.getName().endsWith(".jar")) {
                    answer.add(jar);
                }
            }
        }

        return answer;
    }

    private void runInProcess(CompileContext compileContext, VirtualFile outputDir, File kotlinHome, File scriptFile) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outputStream);

        int rc = execInProcess(kotlinHome, outputDir, scriptFile, out, compileContext);

        ProcessAdapter listener = createProcessListener(compileContext);

        BufferedReader reader = new BufferedReader(new StringReader(outputStream.toString()));
        while (true) {
            try {
                String line = reader.readLine();
                if (line == null) break;
                listener.onTextAvailable(new ProcessEvent(NullProcessHandler.INSTANCE, line), ProcessOutputTypes.STDOUT);
            } catch (IOException e) {
                // Can't be
                throw new RuntimeException(e);
            }
        }

        ProcessEvent termintationEvent = new ProcessEvent(NullProcessHandler.INSTANCE, rc);
        listener.processWillTerminate(termintationEvent, false);
        listener.processTerminated(termintationEvent);
    }

    private static int execInProcess(File kotlinHome, VirtualFile outputDir, File scriptFile, PrintStream out, CompileContext context) {
        URLClassLoader loader = getOrCreateClassloader(kotlinHome, context);
        try {
            Class<?> kompiler = Class.forName("org.jetbrains.jet.cli.KotlinCompiler", true, loader);
            Method exec = kompiler.getDeclaredMethod("exec", PrintStream.class, String[].class);
            Object rc = exec.invoke(null, out, new String[]{"-module", scriptFile.getAbsolutePath(), "-output", path(outputDir)});
            if (rc instanceof Integer) {
                return ((Integer) rc).intValue();
            }
            else {
                throw new RuntimeException("Unexpected return: " + rc);
            }
        } catch (Throwable e) {
            LOG.error(e);
            return -1;
        }
    }

    private static SoftReference<URLClassLoader> ourClassloaderRef = new SoftReference<URLClassLoader>(null);

    private static URLClassLoader getOrCreateClassloader(File kotlinHome, CompileContext context) {
        URLClassLoader answer = ourClassloaderRef.get();
        if (answer == null) {
            answer = createClassloader(kotlinHome, context);
            ourClassloaderRef = new SoftReference<URLClassLoader>(answer);
        }
        return answer;
    }

    private static URLClassLoader createClassloader(File kotlinHome, CompileContext context) {
        List<File> jars = kompilerClasspath(kotlinHome, context);
        URL[] urls = new URL[jars.size()];
        for (int i = 0; i < urls.length; i++) {
            try {
                urls[i] = jars.get(i).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e); // Checked exceptions are great! I love them, and I love brilliant library designers too!
            }
        }

        return new URLClassLoader(urls);
    }

    private static void runOutOfProcess(CompileContext compileContext, VirtualFile outputDir, File kotlinHome, File scriptFile) {
        final SimpleJavaParameters params = new SimpleJavaParameters();
        params.setJdk(new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()));
        params.setMainClass("org.jetbrains.jet.cli.KotlinCompiler");
        params.getProgramParametersList().add("-module", scriptFile.getAbsolutePath());
        params.getProgramParametersList().add("-output", path(outputDir));

        for (File jar : kompilerClasspath(kotlinHome, compileContext)) {
            params.getClassPath().add(jar);
        }

        params.getVMParametersList().addParametersString("-Djava.awt.headless=true -Xmx512m");
//        params.getVMParametersList().addParametersString("-agentlib:yjpagent=sampling");

        Sdk sdk = params.getJdk();

        final GeneralCommandLine commandLine = JdkUtil.setupJVMCommandLine(
                ((JavaSdkType) sdk.getSdkType()).getVMExecutablePath(sdk), params, false);
        try {
            final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString()) {
              @Override
              public Charset getCharset() {
                return commandLine.getCharset();
              }
            };

            ProcessAdapter processListener = createProcessListener(compileContext);
            processHandler.addProcessListener(processListener);

            processHandler.startNotify();
            processHandler.waitFor();
        } catch (Exception e) {
            compileContext.addMessage(ERROR, "[Internal Error] " + e.getLocalizedMessage(), "", -1, -1);
            return;
        }
    }

    private static ProcessAdapter createProcessListener(final CompileContext compileContext) {
        return new ProcessAdapter() {
            StringBuilder stderr = null;

            @Override
            public void onTextAvailable(ProcessEvent event, Key outputType) {
                String text = event.getText();
                String levelCode = parsePrefix(text);
                if (outputType == ProcessOutputTypes.STDERR) {
                    if (stderr == null) {
                        stderr = new StringBuilder();
                    }
                    stderr.append(text);
                    return;
                }
                if (levelCode != null) {
                    CompilerMessageCategory category = categories.get(levelCode);
                    text = text.substring(levelCode.length());

                    String path = "";
                    int line = -1;
                    int column = -1;
                    int colonIndex = text.indexOf(':');
                    if (colonIndex > 0) {
                        path = "file://" + text.substring(0, colonIndex).trim();
                        text = text.substring(colonIndex + 1);

                        Pattern position = Pattern.compile("\\((\\d+),\\s*(\\d+)\\)");

                        Matcher matcher = position.matcher(text);
                        if (matcher.find()) {
                            line = Integer.parseInt(matcher.group(1));
                            column = Integer.parseInt(matcher.group(2));
                            text = text.substring(matcher.group(0).length());
                        }
                    }

                    compileContext.addMessage(category, text, path, line, column);
                }
                else {
                    compileContext.addMessage(INFORMATION, text, "", -1, -1);
                }
            }

            @Override
            public void processTerminated(ProcessEvent event) {
                if (event.getExitCode() != 0) {
                    compileContext.addMessage(ERROR, "Compiler terminated with exit code: " + event.getExitCode(), "", -1, -1);
                }
                // By alex.tkachman:
                if (stderr != null) {
                    compileContext.addMessage(ERROR, "stderr output:\r\n" + stderr.toString(), "", -1, -1);
                }
            }
        };
    }

    private static String[] messagePrefixes = new String[] {"ERROR:", "WARNING:", "INFO:"} ;
    private static Map<String, CompilerMessageCategory> categories = new HashMap<String, CompilerMessageCategory>();
    static {
        categories.put("ERROR:", ERROR);
        categories.put("WARNING:", WARNING);
        categories.put("INFORMATION:", INFORMATION);
    }

    private static String parsePrefix(String message) {
        for (String prefix : messagePrefixes) {
            if (message.startsWith(prefix)) return prefix;
        }
        return null;
    }
    
    private static String path(VirtualFile root) {
        String path = root.getPath();
        if (path.endsWith("!/")) {
            return path.substring(0, path.length() - 2);
        }

        return path;
    }

    private static class NullProcessHandler extends ProcessHandler {
        public static NullProcessHandler INSTANCE = new NullProcessHandler();
        @Override
        protected void destroyProcessImpl() {
            throw new UnsupportedOperationException("destroyProcessImpl is not implemented");
        }

        @Override
        protected void detachProcessImpl() {
            throw new UnsupportedOperationException("detachProcessImpl is not implemented"); // TODO
        }

        @Override
        public boolean detachIsDefault() {
            throw new UnsupportedOperationException("detachIsDefault is not implemented");
        }

        @Override
        public OutputStream getProcessInput() {
            throw new UnsupportedOperationException("getProcessInput is not implemented");
        }
    }
}
