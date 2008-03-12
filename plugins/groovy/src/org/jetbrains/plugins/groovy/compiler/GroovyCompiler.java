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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.groovy.compiler.rt.CompilerMessage;
import org.jetbrains.groovy.compiler.rt.GroovycRunner;
import org.jetbrains.groovy.compiler.rt.MessageCollector;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyGrailsConfiguration;
import org.jetbrains.plugins.grails.module.GrailsModuleType;

import java.io.*;
import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 16.04.2007
 */

public class GroovyCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.groovy.compiler.GroovyCompiler");

  private static final String XMX_COMPILER_PROPERTY = "-Xmx300m";

  private Project myProject;

  public GroovyCompiler(Project project) {
    myProject = project;
  }

  @Nullable
  public TranslatingCompiler.ExitStatus compile(final CompileContext compileContext, final VirtualFile[] virtualFiles) {
    Set<TranslatingCompiler.OutputItem> successfullyCompiled = new HashSet<TranslatingCompiler.OutputItem>();
    Set<VirtualFile> toRecompile = new HashSet<VirtualFile>();

    GeneralCommandLine commandLine;

    Map<Module, Set<VirtualFile>> mapModulesToVirtualFiles = buildModuleToFilesMap(compileContext, virtualFiles);

    for (Map.Entry<Module, Set<VirtualFile>> entry : mapModulesToVirtualFiles.entrySet()) {

      commandLine = new GeneralCommandLine();
      final ProjectJdk jdk = ModuleRootManager.getInstance(entry.getKey()).getJdk();
      assert jdk != null; //verified before
      commandLine.setExePath(jdk.getVMExecutablePath());

//      commandLine.addParameter("-Xdebug");
//      commandLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5557");
      commandLine.addParameter("-cp");

      String rtJarPath = PathUtil.getJarPathForClass(GroovycRunner.class);
      final StringBuilder classPathBuilder = new StringBuilder();
      classPathBuilder.append(rtJarPath);
      classPathBuilder.append(File.pathSeparator);

      Module module = entry.getKey();
      ModuleType moduleType = module.getModuleType();
      String groovyPath = GroovyGrailsConfiguration.getInstance().getGroovyInstallPath();
      String grailsPath = GroovyGrailsConfiguration.getInstance().getGrailsInstallPath();

      String libPath = (moduleType instanceof GrailsModuleType && grailsPath != null && grailsPath.length() > 0 ||
          groovyPath == null || groovyPath.length() == 0 ? grailsPath : groovyPath) + "/lib";

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

      commandLine.addParameter(classPathBuilder.toString());
      commandLine.addParameter(XMX_COMPILER_PROPERTY);

      //for debug needs
//      commandLine.addParameter("-Xdebug");
//      commandLine.addParameter("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5239");

      commandLine.addParameter(GroovycRunner.class.getName());

      try {
        File fileWithParameters = File.createTempFile("toCompile", "");
        fillFileWithGroovycParameters(entry.getKey(), entry.getValue(), fileWithParameters);

        commandLine.addParameter(fileWithParameters.getPath());
      } catch (IOException e) {
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

          compileContext.addMessage(category, compilerMessage.getMessage(),
              url.replace('\\', '/'),
              compilerMessage.getLineNum(), compilerMessage.getColumnNum());
        }

        StringBuffer unparsedBuffer = processHandler.getUnparsedOutput();
        if (unparsedBuffer.length() != 0)
          compileContext.addMessage(CompilerMessageCategory.ERROR, unparsedBuffer.toString(), null, -1, -1);

        successfullyCompiled.addAll(processHandler.getSuccessfullyCompiled());
      } catch (ExecutionException e) {
        LOG.error(e);
      }
    }

    refresh(successfullyCompiled); //make VFS know about our files
    return new GroovyCompileExitStatus(successfullyCompiled, toRecompile.toArray(new VirtualFile[toRecompile.size()]));
  }

  private void refresh(Set<OutputItem> successfullyCompiled) {
    for (OutputItem item : successfullyCompiled) {
      LocalFileSystem.getInstance().refreshAndFindFileByPath(item.getOutputPath());
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

  private boolean required(String name) {
    name = name.toLowerCase();
    if (!name.endsWith(".jar"))
      return false;

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

  private CompilerMessageCategory getMessageCategory(CompilerMessage compilerMessage) {
    String category;
    category = compilerMessage.getCategory();

    if (MessageCollector.ERROR.equals(category)) return CompilerMessageCategory.ERROR;
    if (MessageCollector.INFORMATION.equals(category)) return CompilerMessageCategory.INFORMATION;
    if (MessageCollector.STATISTICS.equals(category)) return CompilerMessageCategory.STATISTICS;
    if (MessageCollector.WARNING.equals(category)) return CompilerMessageCategory.WARNING;

    return CompilerMessageCategory.ERROR;
  }

  class GroovyCompileExitStatus implements ExitStatus {
    private OutputItem[] myCompiledItems;
    private VirtualFile[] myToRecompile;

    public GroovyCompileExitStatus(Set<TranslatingCompiler.OutputItem> compiledItems, VirtualFile[] toRecompile) {
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

    FileOutputStream stream;
    try {
      stream = new FileOutputStream(f);
    } catch (FileNotFoundException e) {
      LOG.error(e);
      return;
    }

    PrintStream printer = new PrintStream(stream);

/*    filename1
*    filname2
*    filname3
*    ...
*    filenameN
*/

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    //files
    for (VirtualFile item : virtualFiles) {
      if (!moduleRootManager.getFileIndex().isInTestSourceContent(item)) {
        printer.println(GroovycRunner.SRC_FILE);
      } else {
        printer.println(GroovycRunner.TEST_FILE);
      }
      printer.println(item.getPath());
    }

    //classpath
    printer.println(GroovycRunner.CLASSPATH);
    printer.println(getCompilationClasspath(module).getPathsString());

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
    return "groovy compiler";
  }

  public boolean validateConfiguration(CompileScope compileScope) {
    if (compileScope.getFiles(GroovyFileType.GROOVY_FILE_TYPE, true).length == 0) return true;

    final String groovyInstallPath = GroovyGrailsConfiguration.getInstance().getGroovyInstallPath();
    final String grailsInstallPath = GroovyGrailsConfiguration.getInstance().getGrailsInstallPath();
    if ((groovyInstallPath == null || groovyInstallPath.length() == 0) &&
        (grailsInstallPath == null || grailsInstallPath.length() == 0)) {
      Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.facet"), GroovyBundle.message("cannot.compile"));
      return false;
    }

    Set<Module> nojdkModules = new HashSet<Module>();
    for (Module module : compileScope.getAffectedModules()) {
      final ProjectJdk jdk = ModuleRootManager.getInstance(module).getJdk();
      if (jdk == null) nojdkModules.add(module);
    }

    if (!nojdkModules.isEmpty()) {
      final Module[] modules = nojdkModules.toArray(new Module[nojdkModules.size()]);
      if (modules.length == 1) {
        Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.sdk", modules[0].getName()), GroovyBundle.message("cannot.compile"));
      } else {
        StringBuffer modulesList = new StringBuffer();
        for (int i = 0; i < modules.length; i++) {
          if (i > 0) modulesList.append(", ");
          modulesList.append(modules[i].getName());
        }
        Messages.showErrorDialog(myProject, GroovyBundle.message("cannot.compile.groovy.files.no.sdk.mult", modulesList.toString()), GroovyBundle.message("cannot.compile"));
      }
      return false;
    }

    return true;
  }

  private PathsList getCompilationClasspath(Module module) {
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

  public PathsList getNonExcludedModuleSourceFolders(Module module) {
    ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
    PathsList sourceFolders = findAllSourceFolders(contentEntries);
    sourceFolders.getPathList().removeAll(findExcludedFolders(contentEntries));
    return sourceFolders;
  }

  private PathsList findAllSourceFolders(ContentEntry[] contentEntries) {
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

  private Set<VirtualFile> findExcludedFolders(ContentEntry[] entries) {
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
