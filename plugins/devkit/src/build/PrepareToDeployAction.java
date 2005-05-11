package org.jetbrains.idea.devkit.build;

import com.intellij.j2ee.make.ManifestBuilder;
import com.intellij.j2ee.make.ModuleBuildProperties;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;

/**
 * User: anna
 * Date: May 5, 2005
 */
public class PrepareToDeployAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final Module module = (Module)e.getDataContext().getData(DataConstants.MODULE);

    try {

      HashSet<Module> modules = new HashSet<Module>();
      PluginBuildUtil.getDependencies(module, modules);
      modules.add(module);

      File jarFile = preparePluginsJar(module, modules);

      HashSet<Library> libs = new HashSet<Library>();
      PluginBuildUtil.getLibraries(module, libs);
      for (Iterator<Module> iterator = modules.iterator(); iterator.hasNext();) {
        Module module1 = iterator.next();
        PluginBuildUtil.getLibraries(module1, libs);
      }

      final String defaultPath = new File(module.getModuleFilePath()).getParent() + File.separator + module.getName();
      final String zipPath = defaultPath + ".zip";
      final File zipFile = new File(zipPath);
      if (libs.size() == 0){
        if (new File(zipPath).exists()){
          if (Messages.showYesNoDialog(module.getProject(), "Do you want to delete \'" + zipPath + "\'?", "Info", Messages.getInformationIcon()) == DialogWrapper.OK_EXIT_CODE){
            FileUtil.delete(zipFile);
          }
        }
        FileUtil.copy(jarFile, new File(defaultPath + ".jar"));
        return;
      }

      if (zipFile.exists() || zipFile.createNewFile()) {
        if (new File(defaultPath + ".jar").exists()){
          if (Messages.showYesNoDialog(module.getProject(), "Do you want to delete \'" + defaultPath + ".jar\'?", "Info", Messages.getInformationIcon()) == DialogWrapper.OK_EXIT_CODE){
            FileUtil.delete(new File(defaultPath + ".jar"));
          }
        }
        final ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        ZipUtil.addFileToZip(zos, jarFile, "/lib/" + module.getName() + ".jar", new HashSet<String>(), new FileFilter() {
          public boolean accept(File pathname) {
            return true;
          }
        });
        Set<String> names = new HashSet<String>();
        for (Library library : libs) {
          String libraryName = null;
          final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
          final HashSet<String> libraryWrittenItems = new HashSet<String>();
          File libraryJar = FileUtil.createTempFile("temp", ".jar");
          libraryJar.deleteOnExit();
          ZipOutputStream jar = null;
          for (VirtualFile virtualFile : files) {
            File ioFile = VfsUtil.virtualToIoFile(virtualFile);
            if (!(virtualFile.getFileSystem() instanceof JarFileSystem)) {
              if (jar == null) {
                jar = new JarOutputStream(new FileOutputStream(libraryJar));
              }
              ZipUtil.addFileOrDirRecursively(jar, libraryJar, VfsUtil.virtualToIoFile(virtualFile), "", new FileFilter() {
                public boolean accept(File pathname) {
                  return true;
                }
              }, libraryWrittenItems);
              if (libraryName == null) {
                libraryName = library.getName() != null ? library.getName() : virtualFile.getName();
                if (names.contains(libraryName)) {
                  int i = 1;
                  while (true) {
                    if (!names.contains(libraryName + i)) {
                      libraryName = libraryName + i;
                      break;
                    }
                    i++;
                  }
                }
                names.add(libraryName);
              }
            }
            else {
              ZipUtil.addFileOrDirRecursively(zos, jarFile, ioFile, "/lib/" + ioFile.getName(), new FileFilter() {
                public boolean accept(File pathname) {
                  return true;
                }
              }, new HashSet<String>());
            }
          }
          if (libraryName != null) {
            jar.close();
            ZipUtil.addFileOrDirRecursively(zos, jarFile, libraryJar, "/lib/" + libraryName + ".jar", new FileFilter() {
              public boolean accept(File pathname) {
                return true;
              }
            }, new HashSet<String>());
          }
        }
        zos.close();
      }
    }
    catch (IOException e1) {
    }
  }

  private File preparePluginsJar(Module module, final HashSet<Module> modules) throws IOException {
        File jarFile = FileUtil.createTempFile("temp", "jar");
        jarFile.deleteOnExit();
        final Manifest manifest = new Manifest();
        Attributes mainAttributes = manifest.getMainAttributes();
        ManifestBuilder.setGlobalAttributes(mainAttributes);
        ZipOutputStream jarPlugin = new JarOutputStream(new FileOutputStream(jarFile), manifest);

        final HashSet<String> writtenItemRelativePaths = new HashSet<String>();
        for (Module module1 : modules) {
          ZipUtil.addDirToZipRecursively(jarPlugin, jarFile, new File(ModuleRootManager.getInstance(module1).getCompilerOutputPath().getPath()), "", new FileFilter() {
            public boolean accept(File pathname) {
              return true;
            }
          }, writtenItemRelativePaths);
        }
        final String pluginXmlPath = ((PluginModuleBuildProperties)ModuleBuildProperties.getInstance(module)).getPluginXmlPath();
        ZipUtil.addFileToZip(jarPlugin,
                             new File(pluginXmlPath),
                             "/META-INF/plugin.xml",
                             writtenItemRelativePaths,
                             new FileFilter() {
                              public boolean accept(File pathname) {
                                return true;
                              }
                             });
        jarPlugin.close();
        return jarFile;
  }

  public void update(AnActionEvent e) {
    final Module module = (Module)e.getDataContext().getData(DataConstants.MODULE);
    e.getPresentation().setVisible(module != null && module.getModuleType() instanceof PluginModuleType);
    e.getPresentation().setText("_Prepare Plugin Module \'" + (module != null ? module.getName() : "") + "\' to Deploy");
  }
}
