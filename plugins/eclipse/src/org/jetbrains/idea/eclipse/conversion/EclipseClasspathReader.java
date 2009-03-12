/*
 * Copyright 2000-2008 JetBrains s.r.o.
 *
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

/*
 * User: anna
 * Date: 11-Nov-2008
 */
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.util.ErrorLog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class EclipseClasspathReader {
  private final String myRootPath;
  private final Project myProject;
  private ContentEntry myContentEntry;

  public EclipseClasspathReader(final String rootPath, final Project project) {
    myRootPath = rootPath;
    myProject = project;
  }

  public void init(ModifiableRootModel model) {
    myContentEntry = model.addContentEntry(VfsUtil.pathToUrl(myRootPath));
    model.inheritSdk();
  }

  public static void collectVariables(Set<String> usedVariables, Element classpathElement) {
    for (Object o : classpathElement.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
      final Element element = (Element)o;
      final String kind = element.getAttributeValue(EclipseXml.KIND_ATTR);
      if (Comparing.strEqual(kind, EclipseXml.VAR_KIND)) {
        String path = element.getAttributeValue(EclipseXml.PATH_ATTR);
        if (path == null) continue;
        int slash = path.indexOf("/");
        if (slash > 0) {
          usedVariables.add(path.substring(0, slash));
        }
        else {
          usedVariables.add(path);
        }


        final String srcPath = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
        if (srcPath == null) continue;
        final int varStart = srcPath.startsWith("/") ? 1 : 0;
        final int slash2 = srcPath.indexOf("/", varStart);
        if (slash2 > 0) {
          usedVariables.add(srcPath.substring(varStart, slash2));
        }
        else {
          usedVariables.add(srcPath.substring(varStart));
        }
      }
    }
  }

  public void readClasspath(ModifiableRootModel model,
                            final Collection<String> unknownLibraries, Collection<String> unknownJdks, final Set<String> usedVariables, Set<String> refsToModules,
                            final String testPattern,
                            Element classpathElement) throws IOException, ConversionException {
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (!(orderEntry instanceof ModuleSourceOrderEntry)) {
        model.removeOrderEntry(orderEntry);
      }
    }
    for (Object o : classpathElement.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
      try {
        readClasspathEntry(model, unknownLibraries, unknownJdks, usedVariables, refsToModules, testPattern, (Element)o);
      }
      catch (ConversionException e) {
        ErrorLog.rethrow(ErrorLog.Level.Warning, null, EclipseXml.CLASSPATH_FILE, e);
      }
    }
  }

  private void readClasspathEntry(ModifiableRootModel rootModel,
                                  final Collection<String> unknownLibraries, Collection<String> unknownJdks, final Set<String> usedVariables,
                                  Set<String> refsToModules, final String testPattern,
                                  Element element)
    throws ConversionException {
    String kind = element.getAttributeValue(EclipseXml.KIND_ATTR);
    if (kind == null) {
      throw new ConversionException("Missing classpathentry/@kind");
    }


    String path = element.getAttributeValue(EclipseXml.PATH_ATTR);
    if (path == null) {
      throw new ConversionException("Missing classpathentry/@path");
    }

    final boolean exported = EclipseXml.TRUE_VALUE.equals(element.getAttributeValue(EclipseXml.EXPORTED_ATTR));

    if (kind.equals(EclipseXml.SRC_KIND)) {
      if (path.startsWith("/")) {
        final String moduleName = path.substring(1);
        refsToModules.add(moduleName);
        rootModel.addInvalidModuleEntry(moduleName).setExported(exported);
      }
      else {
        getContentEntry().addSourceFolder(VfsUtil.pathToUrl(myRootPath + "/" + path), testPattern != null && path.matches(testPattern));
      }
    }

    else if (kind.equals(EclipseXml.OUTPUT_KIND)) {
      final CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
      compilerModuleExtension.setCompilerOutputPath(VfsUtil.pathToUrl(myRootPath + "/" + path));
      compilerModuleExtension.inheritCompilerOutputPath(false);
    }

    else if (kind.equals(EclipseXml.LIB_KIND)) {
      final String libName = getPresentableName(path);
      final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(libName);
      final Library.ModifiableModel modifiableModel = library.getModifiableModel();

      modifiableModel.addRoot(getUrl(path), OrderRootType.CLASSES);

      final String sourcePath = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
      if (sourcePath != null) {
        modifiableModel.addRoot(getUrl(sourcePath), OrderRootType.SOURCES);
      }

      final List<String> docPaths = getClasspathEntryAttribute(element);
      if (docPaths != null) {
        for (String docPath : docPaths) {
          modifiableModel.addRoot(docPath, JavadocOrderRootType.getInstance());
        }
      }

      modifiableModel.commit();

      setLibraryEntryExported(rootModel, exported, library);
    }
    else if (kind.equals(EclipseXml.VAR_KIND)) {
      int slash = path.indexOf("/");
      if (slash == 0) {
        throw new ConversionException("Incorrect 'classpathentry/var@path' format");
      }

      final String libName = getPresentableName(path);
      final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(libName);
      final Library.ModifiableModel modifiableModel = library.getModifiableModel();


      final String clsVar;
      final String clsPath;
      if (slash > 0) {
        clsVar = path.substring(0, slash);
        clsPath = path.substring(slash + 1);
      }
      else {
        clsVar = path;
        clsPath = null;
      }
      usedVariables.add(clsVar);
      final String url = PathMacroManager.getInstance(rootModel.getModule()).expandPath(getVariableRelatedPath(clsVar, clsPath));
      modifiableModel.addRoot(getUrl(url), OrderRootType.CLASSES);

      final String srcPathAttr = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
      if (srcPathAttr != null) {
        final String srcVar;
        final String srcPath;
        final int varStart = srcPathAttr.startsWith("/") ? 1 : 0;

        int slash2 = srcPathAttr.indexOf("/", varStart);
        if (slash2 > 0) {
          srcVar = srcPathAttr.substring(varStart, slash2);
          srcPath = srcPathAttr.substring(slash2 + 1);
        }
        else {
          srcVar = srcPathAttr.substring(varStart);
          srcPath = null;
        }
        usedVariables.add(srcVar);
        final String srcUrl = PathMacroManager.getInstance(rootModel.getModule()).expandPath(getVariableRelatedPath(srcVar, srcPath));
        modifiableModel.addRoot(getUrl(srcUrl), OrderRootType.SOURCES);
      }

      final List<String> docPaths = getClasspathEntryAttribute(element);
      if (docPaths != null) {
        for (String docPath : docPaths) {
          modifiableModel.addRoot(docPath, JavadocOrderRootType.getInstance());
        }
      }

      modifiableModel.commit();

      setLibraryEntryExported(rootModel, exported, library);
    }
    else if (kind.equals(EclipseXml.CON_KIND)) {
      if (path.equals(EclipseXml.ECLIPSE_PLATFORM)) {
        addNamedLibrary(rootModel, unknownLibraries, exported, IdeaXml.ECLIPSE_LIBRARY, LibraryTablesRegistrar.APPLICATION_LEVEL);
      }
      else if (path.startsWith(EclipseXml.JRE_CONTAINER)) {

        final String jdkName = getLastPathComponent(path);
        if (jdkName == null) {
          rootModel.inheritSdk();
        }
        else {
          final Sdk moduleJdk = ProjectJdkTable.getInstance().findJdk(jdkName);
          if (moduleJdk != null) {
            rootModel.setSdk(moduleJdk);
          }
          else {
            rootModel.setInvalidSdk(jdkName, IdeaXml.JAVA_SDK_TYPE);
            unknownJdks.add(jdkName);
          }
        }
        OrderEntry[] orderEntries = rootModel.getOrderEntries();
        orderEntries = ArrayUtil.append(orderEntries, orderEntries[0]);
        rootModel.rearrangeOrderEntries(ArrayUtil.remove(orderEntries, 0));
      }
      else if (path.startsWith(EclipseXml.USER_LIBRARY)) {
        addNamedLibrary(rootModel, unknownLibraries, exported, getPresentableName(path), LibraryTablesRegistrar.PROJECT_LEVEL);
      }
      else if (path.startsWith(EclipseXml.JUNIT_CONTAINER)) {
        final String junitName = IdeaXml.JUNIT + getPresentableName(path);
        final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(junitName);
        final Library.ModifiableModel modifiableModel = library.getModifiableModel();
        modifiableModel.addRoot(getJunitClsUrl(junitName.contains("4")), OrderRootType.CLASSES);
        modifiableModel.commit();
      }
    }
    else {
      throw new ConversionException("Unknown classpathentry/@kind: " + kind);
    }
  }

  private static void setLibraryEntryExported(ModifiableRootModel rootModel, boolean exported, Library library) {
    for (OrderEntry orderEntry : rootModel.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry && ((LibraryOrderEntry)orderEntry).isModuleLevel()
          && Comparing.equal(((LibraryOrderEntry)orderEntry).getLibrary(), library)) {
        ((LibraryOrderEntry)orderEntry).setExported(exported);
        break;
      }
    }
  }

  private void addNamedLibrary(final ModifiableRootModel rootModel,
                               final Collection<String> unknownLibraries,
                               final boolean exported,
                               final String name,
                               final String notFoundLibraryLevel) {
    final LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();
    Library lib = tablesRegistrar.getLibraryTable().getLibraryByName(name);
    if (lib == null) {
      lib = tablesRegistrar.getLibraryTable(myProject).getLibraryByName(name);
    }
    if (lib != null) {
      rootModel.addLibraryEntry(lib).setExported(exported);
    }
    else {
      unknownLibraries.add(name);
      rootModel.addInvalidLibrary(name, notFoundLibraryLevel).setExported(exported);
    }
  }

  @NotNull
  private static String getPresentableName(@NotNull String path) {
    final String pathComponent = getLastPathComponent(path);
    return pathComponent != null ? pathComponent : path;
  }

  @Nullable
  public static String getLastPathComponent(final String path) {
    final int idx = path.lastIndexOf('/');
    return idx < 0 || idx == path.length() - 1 ? null : path.substring(idx + 1);
  }

  private ContentEntry getContentEntry() {
    return myContentEntry;
  }

  private static String getVariableRelatedPath(String var, String path) {
    return var == null ? null : ("$" + var + "$" + (path == null ? "" : ("/" + path)));
  }

  private String getUrl(final String path) {
    String url = null;
    if (path.startsWith("/")) {
      final String relativePath = new File(myRootPath).getParent() + "/" + path;
      final File file = new File(relativePath);
      if (file.exists()) {
        url = VfsUtil.pathToUrl(relativePath);
      }
    }
    if (url == null) {
      final String absPath = myRootPath + "/" + path;
      if (new File(absPath).exists()) {
        url = VfsUtil.pathToUrl(absPath);
      } else {
        url = VfsUtil.pathToUrl(path);
      }
    }
    final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(url);
    if (localFile != null) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
      if (jarFile != null) {
        url = jarFile.getUrl();
      }
    }
    return url;
  }

  @Nullable
  private List<String> getClasspathEntryAttribute(Element element) {
    Element attributes = element.getChild("attributes");
    if (attributes == null) {
      return null;
    }
    List<String> result = new ArrayList<String>();
    for (Object o : attributes.getChildren("attribute")) {
      if (Comparing.strEqual(((Element)o).getAttributeValue("name"), "javadoc_location")) {
        Element attribute = (Element)o;
        final String javadocPath = attribute.getAttributeValue("value");

        if (javadocPath.startsWith(EclipseXml.FILE_PROTOCOL) &&
            new File(javadocPath.substring(EclipseXml.FILE_PROTOCOL.length())).exists()) {
          result.add(VfsUtil.pathToUrl(javadocPath.substring(EclipseXml.FILE_PROTOCOL.length())));
        }
        else {

          final String protocol = VirtualFileManager.extractProtocol(javadocPath);
          if (Comparing.strEqual(protocol, HttpFileSystem.getInstance().getProtocol())) {
            result.add(javadocPath);
          }
          else if (javadocPath.startsWith(EclipseXml.JAR_PREFIX)) {
            final String jarJavadocPath = javadocPath.substring(EclipseXml.JAR_PREFIX.length());
            if (jarJavadocPath.startsWith(EclipseXml.PLATFORM_PROTOCOL)) {
              String relativeToPlatform = jarJavadocPath.substring(EclipseXml.PLATFORM_PROTOCOL.length() + "resources".length());
              result
                .add(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, new File(myRootPath).getParent() + "/" + relativeToPlatform));
            }
            else if (jarJavadocPath.startsWith(EclipseXml.FILE_PROTOCOL)) {
              result
                .add(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, jarJavadocPath.substring(EclipseXml.FILE_PROTOCOL.length())));
            }
            else {
              result.add(javadocPath);
            }
          }
        }
      }
    }
    return result;
  }

  static String getJunitClsUrl(final boolean version4) {
    String url = version4 ? JavaSdkUtil.getJunit4JarPath() : JavaSdkUtil.getJunit3JarPath();
    final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.pathToUrl(url));
    if (localFile != null) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
      url = jarFile != null ? jarFile.getUrl() : localFile.getUrl();
    }
    return url;
  }

  public static void readIDEASpecific(final Element root, ModifiableRootModel model) throws InvalidDataException {
    PathMacroManager.getInstance(model.getModule()).expandPaths(root);

    model.getModuleExtension(CompilerModuleExtension.class).readExternal(root);
    model.getModuleExtension(LanguageLevelModuleExtension.class).readExternal(root);

    final ContentEntry[] entries = model.getContentEntries();
    if (entries.length > 0) {
      for (Object o : root.getChildren(IdeaXml.TEST_FOLDER_TAG)) {
        final String url = ((Element)o).getAttributeValue(IdeaXml.URL_ATTR);
        SourceFolder folderToBeTest = null;
        for (SourceFolder folder : entries[0].getSourceFolders()) {
          if (Comparing.strEqual(folder.getUrl(), url)) {
            folderToBeTest = folder;
            break;
          }
        }
        if (folderToBeTest != null) {
          entries[0].removeSourceFolder(folderToBeTest);
        }
        entries[0].addSourceFolder(url, true);
      }

      for (Object o : root.getChildren(IdeaXml.EXCLUDE_FOLDER_TAG)) {
        entries[0].addExcludeFolder(((Element)o).getAttributeValue(IdeaXml.URL_ATTR));
      }
    }

    for (Object o : root.getChildren("lib")) {
      Element libElement = (Element)o;
      final String libName = libElement.getAttributeValue("name");
      final Library libraryByName = model.getModuleLibraryTable().getLibraryByName(libName);
      if (libraryByName != null) {
        final Library.ModifiableModel modifiableModel = libraryByName.getModifiableModel();
        for (Object r : libElement.getChildren("srcroot")) {
          modifiableModel.addRoot(((Element)r).getAttributeValue("url"), OrderRootType.SOURCES);
        }
        modifiableModel.commit();
      }
    }
  }
}