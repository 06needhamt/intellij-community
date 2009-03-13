/*
 * Copyright 2000-2007 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.OutputItemImpl;
import com.intellij.compiler.impl.resourceCompiler.ResourceCompiler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovycRunner;
import org.jetbrains.groovy.compiler.rt.MessageCollector;
import org.jetbrains.plugins.grails.config.GrailsConfigUtils;
import org.jetbrains.plugins.grails.module.GrailsModuleType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyFacet;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */

public class GroovyCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.groovy.compiler.GroovyCompiler");

  private static final String XMX_COMPILER_PROPERTY = "-Xmx300m";

  private final Project myProject;
  @NonNls private static final String GROOVY_COMPILER = "groovy compiler";

  public GroovyCompiler(Project project) {
    myProject = project;
  }

  @Nullable
  public ExitStatus compile(final CompileContext compileContext, final VirtualFile[] virtualFiles) {
    Set<OutputItem> successfullyCompiled = new HashSet<OutputItem>();
    Set<VirtualFile> toRecompile = new HashSet<VirtualFile>();

    Map<Module, Set<VirtualFile>> mapModulesToVirtualFiles = buildModuleToFilesMap(compileContext, virtualFiles);

    for (Map.Entry<Module, Set<VirtualFile>> entry : mapModulesToVirtualFiles.entrySet()) {
      final Module module = entry.getKey();
      final GroovyFacet facet = GroovyFacet.getInstance(module);
      final Set<VirtualFile> moduleFiles = entry.getValue();
      if (facet == null || facet.getConfiguration().isCompileGroovyFiles()) {
        doCompile(compileContext, successfullyCompiled, toRecompile, module, moduleFiles);
      } else {
        final ResourceCompiler resourceCompiler = new ResourceCompiler(myProject, CompilerConfiguration.getInstance(myProject));
        final ExitStatus exitStatus = resourceCompiler.compile(compileContext, moduleFiles.toArray(new VirtualFile[moduleFiles.size()]));
        successfullyCompiled.addAll(Arrays.asList(exitStatus.getSuccessfullyCompiled()));
        toRecompile.addAll(Arrays.asList(exitStatus.getFilesToRecompile()));
      }
    }

    return new GroovyCompileExitStatus(successfullyCompiled, toRecompile.toArray(new VirtualFile[toRecompile.size()]));
  }

  private void doCompile(CompileContext compileContext, Set<OutputItem> successfullyCompiled, Set<VirtualFile> toRecompile, final Module module,
                         final Set<VirtualFile> toCompile) {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    assert sdk != null; //verified before
    SdkType sdkType = sdk.getSdkType();
    assert sdkType instanceof JavaSdkType;
    commandLine.setExePath(((JavaSdkType)sdkType).getVMExecutablePath(sdk));

//      for debug
//      commandLine.addParameter("-Xdebug");
//      commandLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5557");

    commandLine.addParameter("-cp");

    String rtJarPath = PathUtil.getJarPathForClass(GroovycRunner.class);
    final StringBuilder classPathBuilder = new StringBuilder();
    classPathBuilder.append(rtJarPath);
    classPathBuilder.append(File.pathSeparator);

    ModuleType moduleType = module.getModuleType();
    String groovyPath = GroovyConfigUtils.getInstance().getSDKInstallPath(module);
    String grailsPath = GrailsConfigUtils.getInstance().getSDKInstallPath(module);

    String libPath =
      (moduleType instanceof GrailsModuleType && grailsPath.length() > 0 || groovyPath.length() == 0 ? grailsPath : groovyPath) + "/lib";

    libPath = libPath.replace(File.separatorChar, '/');
    VirtualFile lib = LocalFileSystem.getInstance().findFileByPath(libPath);
    if (lib != null) {
      for (VirtualFile file : lib.getChildren()) {
        if (required(file.getName())) {
          classPathBuilder.append(file.getPath());
          classPathBuilder.append(File.pathSeparator);
        }
      }
    }

    classPathBuilder.append(getModuleSpecificClassPath(module));
    commandLine.addParameter(classPathBuilder.toString());
    commandLine.addParameter(XMX_COMPILER_PROPERTY);
    //commandLine.addParameter("-Xdebug");
    //commandLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

    // Setting up process encoding according to locale
    final ArrayList<String> list = new ArrayList<String>();
    CompilerUtil.addLocaleOptions(list, false);
    commandLine.addParameters(list);

    commandLine.addParameter(GroovycRunner.class.getName());

    try {
      File fileWithParameters = File.createTempFile("toCompile", "");
      fillFileWithGroovycParameters(module, toCompile, fileWithParameters);

      commandLine.addParameter(fileWithParameters.getPath());
    }
    catch (IOException e) {
      LOG.error(e);
    }

    GroovycOSProcessHandler processHandler;

    try {
      processHandler = new GroovycOSProcessHandler(compileContext, commandLine.createProcess(), commandLine.getCommandLineString());

      processHandler.startNotify();
      processHandler.waitFor();

      Set<File> toRecompileFiles = processHandler.getToRecompileFiles();
      for (File toRecompileFile : toRecompileFiles) {
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(toRecompileFile);
        LOG.assertTrue(vFile != null);
        toRecompile.add(vFile);
      }

      for (CompilerMessage compilerMessage : processHandler.getCompilerMessages()) {
        final CompilerMessageCategory category;
        category = getMessageCategory(compilerMessage);

        final String url = compilerMessage.getUrl();

        compileContext.addMessage(category, compilerMessage.getMessage(), url.replace('\\', '/'), compilerMessage.getLineNum(),
                                  compilerMessage.getColumnNum());
      }

      StringBuffer unparsedBuffer = processHandler.getUnparsedOutput();
      if (unparsedBuffer.length() != 0) compileContext.addMessage(CompilerMessageCategory.ERROR, unparsedBuffer.toString(), null, -1, -1);

      addSuccessfullyCompiled(successfullyCompiled, processHandler);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
  }

  private static String getModuleSpecificClassPath(final Module module) {
    final StringBuffer buffer = new StringBuffer();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        ModuleRootManager manager = ModuleRootManager.getInstance(module);
        for (OrderEntry entry : manager.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry) {
            Library library = ((LibraryOrderEntry)entry).getLibrary();
            if (library == null) continue;
            for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
              String path = file.getPath();
              if (path != null && path.endsWith(".jar!/")) {
                buffer.append(StringUtil.trimEnd(path, "!/")).append(File.pathSeparator);
              }
            }
          }
        }
      }
    });
    return buffer.toString();
  }

  private static void addSuccessfullyCompiled(Set<OutputItem> successfullyCompiled, GroovycOSProcessHandler processHandler) {
    Set<OutputItem> toplevel = processHandler.getSuccessfullyCompiled();
    for (OutputItem item : toplevel) { //add closure files
      VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(item.getOutputPath());
      if (vFile != null) {//defensive check
        VirtualFile parent = vFile.getParent();
        assert parent != null;
        parent.refresh(false, false);
        String prefix = vFile.getNameWithoutExtension() + "$_closure";
        for (VirtualFile child : parent.getChildren()) {
          if (child.getName().startsWith(prefix)) {
            successfullyCompiled.add(new OutputItemImpl(item.getOutputRootDirectory(), new String(child.getPath().substring(item.getOutputRootDirectory().length() + 1)), item.getSourceFile()));
          }
        }
      }
      successfullyCompiled.add(item);
    }
  }


  static HashSet<String> required = new HashSet<String>();

  static {
    required.add("groovy");
    required.add("asm");
    required.add("antlr");
    required.add("junit");
    required.add("jline");
    required.add("ant");
    required.add("commons");
  }

  private static boolean required(String name) {
    name = name.toLowerCase();
    if (!name.endsWith(".jar")) return false;

    name = name.substring(0, name.lastIndexOf('.'));
    int ind = name.lastIndexOf('-');
    if (ind != -1 && name.length() > ind + 1 && Character.isDigit(name.charAt(ind + 1))) {
      name = name.substring(0, ind);
    }

    for (String requiredStr : required) {
      if (name.contains(requiredStr)) return true;
    }

    return false;
  }

  private static CompilerMessageCategory getMessageCategory(CompilerMessage compilerMessage) {
    String category;
    category = compilerMessage.getCategory();

    if (MessageCollector.ERROR.equals(category)) return CompilerMessageCategory.ERROR;
    if (MessageCollector.INFORMATION.equals(category)) return CompilerMessageCategory.INFORMATION;
    if (MessageCollector.STATISTICS.equals(category)) return CompilerMessageCategory.STATISTICS;
    if (MessageCollector.WARNING.equals(category)) return CompilerMessageCategory.WARNING;

    return CompilerMessageCategory.ERROR;
  }

  static class GroovyCompileExitStatus implements ExitStatus {
    private final OutputItem[] myCompiledItems;
    private final VirtualFile[] myToRecompile;

    public GroovyCompileExitStatus(Set<OutputItem> compiledItems, VirtualFile[] toRecompile) {
      myToRecompile = toRecompile;
      myCompiledItems = compiledItems.toArray(new OutputItem[compiledItems.size()]);
    }

    public OutputItem[] getSuccessfullyCompiled() {
      return myCompiledItems;
    }

    public VirtualFile[] getFilesToRecompile() {
      return myToRecompile;
    }
  }

  private void fillFileWithGroovycParameters(Module module, Set<VirtualFile> virtualFiles, File f) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Running groovyc on: " + virtualFiles.toString());
    }

    FileOutputStream stream;
    try {
      stream = new FileOutputStream(f);
    }
    catch (FileNotFoundException e) {
      LOG.error(e);
      return;
    }

    final PrintStream printer = new PrintStream(stream);

/*    filename1
*    filname2
*    filname3
*    ...
*    filenameN
*/

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    //files
    for (final VirtualFile item : virtualFiles) {
      final boolean isSource = !moduleRootManager.getFileIndex().isInTestSourceContent(item);
      if (isSource) {
        printer.println(GroovycRunner.SRC_FILE);
      }
      else {
        printer.println(GroovycRunner.TEST_FILE);
      }
      printer.println(item.getPath());
      if (isSource) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final PsiFile file = PsiManager.getInstance(myProject).findFile(item);
            if (file instanceof GroovyFileBase) {
              for (PsiClass psiClass : ((GroovyFileBase)file).getClasses()) {
                printer.println(psiClass.getQualifiedName());
              }
            }
          }
        });
        printer.println(GroovycRunner.END);
      }
    }

    //classpath
    printer.println(GroovycRunner.CLASSPATH);
    printer.println(getCompilationClasspath(module).getPathsString());

    //Grails injections  support
    printer.println(GroovycRunner.IS_GRAILS);
    printer.println(GrailsConfigUtils.getInstance().isSDKConfigured(module) && module.getModuleType() instanceof GrailsModuleType);

    final Charset ideCharset = EncodingProjectManager.getInstance(myProject).getDefaultCharset();
    if (!Comparing.equal(CharsetToolkit.getDefaultSystemCharset(), ideCharset)) {
      printer.println(GroovycRunner.ENCODING);
      printer.println(ideCharset.name());
    }

    //production output
    printer.println(GroovycRunner.OUTPUTPATH);
    printer.println(CompilerPaths.getModuleOutputPath(module, false));

    //test output
    printer.println(GroovycRunner.TEST_OUTPUTPATH);
    printer.println(CompilerPaths.getModuleOutputPath(module, true));
    printer.close();
  }

  public boolean isCompilableFile(VirtualFile virtualFile, CompileContext compileContext) {
    return GroovyFileType.GROOVY_FILE_TYPE.equals(virtualFile.getFileType());
  }

  @NotNull
  public String getDescription() {
    return GROOVY_COMPILER;
  }

  public boolean validateConfiguration(CompileScope compileScope) {
    VirtualFile[] files = compileScope.getFiles(GroovyFileType.GROOVY_FILE_TYPE, true);
    if (files.length == 0) return true;
    Set<Module> modules = new HashSet<Module>();
    for (VirtualFile file : files) {
      ProjectRootManager rootManager = ProjectRootManager.getInstance(myProject);
      Module module = rootManager.getFileIndex().getModuleForFile(file);
      if (module != null) {
        modules.add(module);
      }
    }

    for (Module module : modules) {
      final String groovyInstallPath = GroovyConfigUtils.getInstance().getSDKInstallPath(module);
      final String grailsInstallPath = GrailsConfigUtils.getInstance().getSDKInstallPath(module);
      if (groovyInstallPath.length() == 0 && grailsInstallPath.length() == 0) {
        if (!GroovyConfigUtils.getInstance().tryToSetUpGroovyFacetOntheFly(module)) {
          Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.facet", module.getName()),
                                   GroovyBundle.message("cannot.compile"));
          ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
        }
        return false;
      }
    }

    Set<Module> nojdkModules = new HashSet<Module>();
    for (Module module : compileScope.getAffectedModules()) {
      if(!(module.getModuleType() instanceof JavaModuleType)) continue;
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) nojdkModules.add(module);
    }

    if (!nojdkModules.isEmpty()) {
      final Module[] noJdkArray = nojdkModules.toArray(new Module[nojdkModules.size()]);
      if (noJdkArray.length == 1) {
        Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.sdk", noJdkArray[0].getName()),
                                 GroovyBundle.message("cannot.compile"));
      }
      else {
        StringBuffer modulesList = new StringBuffer();
        for (int i = 0; i < noJdkArray.length; i++) {
          if (i > 0) modulesList.append(", ");
          modulesList.append(noJdkArray[i].getName());
        }
        Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.sdk.mult", modulesList.toString()),
                                 GroovyBundle.message("cannot.compile"));
      }
      return false;
    }

    return true;
  }

  private static PathsList getCompilationClasspath(Module module) {
    ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    OrderEntry[] entries = rootManager.getOrderEntries();
    Set<VirtualFile> cpVFiles = new HashSet<VirtualFile>();

    for (OrderEntry orderEntry : entries) {
      cpVFiles.addAll(Arrays.asList(orderEntry.getFiles(OrderRootType.COMPILATION_CLASSES)));
    }

    VirtualFile[] filesArray = cpVFiles.toArray(new VirtualFile[cpVFiles.size()]);
    PathsList pathsList = new PathsList();
    for (VirtualFile file : filesArray) {
      String filePath = file.getPath();

      int jarSeparatorIndex = filePath.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (jarSeparatorIndex > 0) {
        filePath = filePath.substring(0, jarSeparatorIndex);
      }

      pathsList.add(filePath);
    }

    final String output = CompilerPaths.getModuleOutputPath(module, false);
    if (output != null) pathsList.add(output);
    return pathsList;
  }

  public static PathsList getNonExcludedModuleSourceFolders(Module module) {
    ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    PathsList sourceFolders = findAllSourceFolders(contentEntries);
    sourceFolders.getPathList().removeAll(findExcludedFolders(contentEntries));
    return sourceFolders;
  }

  private static PathsList findAllSourceFolders(ContentEntry[] contentEntries) {
    PathsList sourceFolders = new PathsList();
    for (ContentEntry contentEntry : contentEntries) {
      for (SourceFolder folder : contentEntry.getSourceFolders()) {
        VirtualFile file = folder.getFile();
        if (file == null) continue;

        if (file.isDirectory() && file.isWritable()) {
          sourceFolders.add(file);
        }
      }
    }
    return sourceFolders;
  }

  private static Set<VirtualFile> findExcludedFolders(ContentEntry[] entries) {
    Set<VirtualFile> excludedFolders = new HashSet<VirtualFile>();
    for (ContentEntry entry : entries) {
      excludedFolders.addAll(Arrays.asList(entry.getExcludeFolderFiles()));
    }
    return excludedFolders;
  }

  private static Map<Module, Set<VirtualFile>> buildModuleToFilesMap(final CompileContext context, final VirtualFile[] files) {
    final Map<Module, Set<VirtualFile>> map = new HashMap<Module, Set<VirtualFile>>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        for (VirtualFile file : files) {
          final Module module = context.getModuleByFile(file);

          if (module == null) {
            continue;
          }

          Set<VirtualFile> moduleFiles = map.get(module);
          if (moduleFiles == null) {
            moduleFiles = new HashSet<VirtualFile>();
            map.put(module, moduleFiles);
          }
          moduleFiles.add(file);
        }
      }
    });
    return map;
  }
}
