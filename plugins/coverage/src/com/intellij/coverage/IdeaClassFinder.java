package com.intellij.coverage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.coverage.util.classFinder.ClassFinder;
import com.intellij.rt.coverage.util.classFinder.ClassPathEntry;
import com.intellij.util.lang.UrlClassLoader;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
* @author anna
*/
class IdeaClassFinder extends ClassFinder {
  private static final Logger LOG = Logger.getInstance("#" + IdeaClassFinder.class.getName());
  
  private final Project myProject;
  private final JavaCoverageSuite myCurrentSuite;

  public IdeaClassFinder(Project project, JavaCoverageSuite currentSuite) {
    super(obtainPatternsFromSuite(currentSuite), new ArrayList());
    myProject = project;
    myCurrentSuite = currentSuite;
  }

  private static List<Pattern> obtainPatternsFromSuite(JavaCoverageSuite currentSuite) {
    final List<Pattern> includePatterns = new ArrayList<Pattern>();
    for (String pattern : currentSuite.getFilteredPackageNames()) {
      includePatterns.add(Pattern.compile(pattern + ".*"));
    }

    for (String pattern : currentSuite.getFilteredClassNames()) {
      includePatterns.add(Pattern.compile(pattern));
    }
    return includePatterns;
  }

  @Override
  protected Collection getClassPathEntries() {
    final Collection entries = super.getClassPathEntries();
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
      if (extension != null) {
        final VirtualFile outputFile = extension.getCompilerOutputPath();
        try {
          if (outputFile != null) {
            final URL outputURL = VfsUtil.virtualToIoFile(outputFile).toURI().toURL();
            entries.add(new ClassPathEntry(outputFile.getPath(), new UrlClassLoader(new URL[]{outputURL}, null)));
          }
          if (myCurrentSuite.isTrackTestFolders()) {
            final VirtualFile testOutput = extension.getCompilerOutputPathForTests();
            if (testOutput != null) {
              final URL testOutputURL = VfsUtil.virtualToIoFile(testOutput).toURI().toURL();
              entries.add(new ClassPathEntry(testOutput.getPath(), new UrlClassLoader(new URL[]{testOutputURL}, null)));
            }
          }
        }
        catch (MalformedURLException e1) {
          LOG.error(e1);
        }
      }
    }
    return entries;
  }
}
