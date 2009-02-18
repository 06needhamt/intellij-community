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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EclipseClasspathWriter {
  private ModifiableRootModel myModel;
 
  public EclipseClasspathWriter(final ModifiableRootModel model) {
    myModel = model;
  }

  public void writeClasspath(Element classpathElement, @Nullable Element oldRoot) throws ConversionException {
    for (OrderEntry orderEntry : myModel.getOrderEntries()) {
      createClasspathEntry(orderEntry, classpathElement, oldRoot);
    }

    final Element orderEntry =
      addOrderEntry(EclipseXml.OUTPUT_KIND, getRelativePath(myModel.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputUrl()),
                    classpathElement, oldRoot);
    setAttributeIfAbsent(orderEntry, EclipseXml.PATH_ATTR, EclipseXml.BIN_DIR);
  }

  private void createClasspathEntry(OrderEntry entry, Element classpathRoot, Element oldRoot) throws ConversionException {
    if (entry instanceof ModuleSourceOrderEntry) {
      final ContentEntry[] entries = ((ModuleSourceOrderEntry)entry).getRootModel().getContentEntries();
      if (entries.length > 0) {
        final ContentEntry contentEntry = entries[0];
        for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          addOrderEntry(EclipseXml.SRC_KIND, getRelativePath(sourceFolder.getUrl()), classpathRoot, oldRoot);
        }
      }
    }
    else if (entry instanceof ModuleOrderEntry) {
      Element orderEntry = addOrderEntry(EclipseXml.SRC_KIND, "/" + ((ModuleOrderEntry)entry).getModuleName(), classpathRoot, oldRoot);
      setAttributeIfAbsent(orderEntry, EclipseXml.COMBINEACCESSRULES_ATTR, EclipseXml.FALSE_VALUE);
      setExported(orderEntry, ((ExportableOrderEntry)entry));
    }
    else if (entry instanceof LibraryOrderEntry) {
      final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
      final String libraryName = libraryOrderEntry.getLibraryName();
      if (libraryOrderEntry.isModuleLevel()) {
        if (libraryName != null && libraryName.contains(IdeaXml.JUNIT)) {
          final Element orderEntry = addOrderEntry(EclipseXml.CON_KIND, EclipseXml.JUNIT_CONTAINER + "/" + libraryName.substring(IdeaXml.JUNIT.length()), classpathRoot,
                                                   oldRoot);
          setExported(orderEntry, libraryOrderEntry);
        } else {
          final String[] files = libraryOrderEntry.getUrls(OrderRootType.CLASSES);
          if (files.length > 0) {
            final String[] kind = new String[]{EclipseXml.LIB_KIND};
            final String relativeClassPath = getRelativePath(files[0], kind);
            final Element orderEntry = addOrderEntry(kind[0], relativeClassPath, classpathRoot, oldRoot);

            final String[] srcFiles = libraryOrderEntry.getUrls(OrderRootType.SOURCES);
            setOrRemoveAttribute(orderEntry, EclipseXml.SOURCEPATH_ATTR, srcFiles.length == 0 ? null : getRelativePath(srcFiles[srcFiles.length - 1], new String[1], Comparing.strEqual(kind[0], EclipseXml.VAR_KIND)));

            //clear javadocs before write new
            final List children = new ArrayList(orderEntry.getChildren(EclipseXml.ATTRIBUTES_TAG));
            for (Object o : children) {
              ((Element)o).detach();
            }
            final String[] docUrls = libraryOrderEntry.getUrls(JavadocOrderRootType.getInstance());
            for (final String docUrl : docUrls) {
              setJavadocPath(orderEntry, docUrl);
            }

            setExported(orderEntry, libraryOrderEntry);
          }
        }

      }
      else {
        final Element orderEntry;
        if (Comparing.strEqual(libraryName, IdeaXml.ECLIPSE_LIBRARY)) {
          orderEntry = addOrderEntry(EclipseXml.CON_KIND, EclipseXml.ECLIPSE_PLATFORM, classpathRoot, oldRoot);
        }
        else {
          orderEntry = addOrderEntry(EclipseXml.CON_KIND, EclipseXml.USER_LIBRARY + "/" + libraryName, classpathRoot, oldRoot);
        }
        setExported(orderEntry, libraryOrderEntry);
      }
    }
    else if (entry instanceof JdkOrderEntry) {
      if (entry instanceof InheritedJdkOrderEntry) {
        addOrderEntry(EclipseXml.CON_KIND, EclipseXml.JRE_CONTAINER, classpathRoot, oldRoot);
      }
      else {
        final Sdk jdk = ((JdkOrderEntry)entry).getJdk();
        addOrderEntry(EclipseXml.CON_KIND, jdk == null ? EclipseXml.JRE_CONTAINER : EclipseXml.JRE_CONTAINER + "/" + jdk.getName(), classpathRoot,
                      oldRoot);
      }
    }
    else {
      throw new ConversionException("Unknown EclipseProjectModel.ClasspathEntry: " + entry.getClass());
    }
  }

  private String getRelativePath(String srcFile, String [] kind) {
    return getRelativePath(srcFile, kind, true);
  }

  private String getRelativePath(String url) {
    return getRelativePath(url, new String[1]);
  }

  private String getRelativePath(final String url, String[] kind, boolean replaceVars) {
    final Module module = myModel.getModule();

    final VirtualFile projectBaseDir = module.getProject().getBaseDir();  //todo
    assert projectBaseDir != null;

    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file != null) {
      if (file.getFileSystem() instanceof JarFileSystem) {
        file = JarFileSystem.getInstance().getVirtualFileForJar(file);
      }
      assert file != null;
      final VirtualFile contentRoot = getContentRoot();
      if (contentRoot != null) {
        if (VfsUtil.isAncestor(contentRoot, file, false)) {
          return VfsUtil.getRelativePath(file, contentRoot, '/');
        }
      }
      if (VfsUtil.isAncestor(projectBaseDir, file, false)) {
        return "/" + VfsUtil.getRelativePath(file, projectBaseDir, '/');
      }
      else {
        return replaceVars ? stripIDEASpecificPrefix(url, kind) : ProjectRootManagerImpl.extractLocalPath(url);
      }
    } else {
      final VirtualFile contentRoot = getContentRoot();
      if (contentRoot != null) {
        final String rootUrl = contentRoot.getUrl();
        if (url.startsWith(rootUrl) && url.length() > rootUrl.length()) {
          return url.substring(rootUrl.length() + 1); //without leading /
        }
      }
      final String projectUrl = projectBaseDir.getUrl();
      if (url.startsWith(projectUrl)) {
        return url.substring(projectUrl.length()); //leading /
      }

      return replaceVars ? stripIDEASpecificPrefix(url, kind) :ProjectRootManagerImpl.extractLocalPath(url);
    }
  }

  @Nullable
  private VirtualFile getContentRoot() {
    final ContentEntry[] entries = myModel.getContentEntries();
    if (entries.length > 0) {
      return entries[0].getFile();
    }
    return null;
  }

  private void setJavadocPath(final Element element, String javadocPath) {
    if (javadocPath != null) {
      Element child =  new Element(EclipseXml.ATTRIBUTES_TAG);
      element.addContent(child);

      Element attrElement = child.getChild(EclipseXml.ATTRIBUTE_TAG);
      if (attrElement == null) {
        attrElement = new Element(EclipseXml.ATTRIBUTE_TAG);
        child.addContent(attrElement);
      }

      attrElement.setAttribute("name", "javadoc_location");

      final String protocol = VirtualFileManager.extractProtocol(javadocPath);
      if (!Comparing.strEqual(protocol, HttpFileSystem.getInstance().getProtocol())) {
        final String path = VfsUtil.urlToPath(javadocPath);
        if (new File(path).exists()) {
          javadocPath = EclipseXml.FILE_PROTOCOL + path;
        } else if (Comparing.strEqual(protocol, JarFileSystem.getInstance().getProtocol())){
          final VirtualFile javadocFile = JarFileSystem.getInstance().getVirtualFileForJar(VirtualFileManager.getInstance().findFileByUrl(javadocPath));
          if (javadocFile != null && VfsUtil.isAncestor(myModel.getProject().getBaseDir(), javadocFile,  false)) {
            javadocPath = EclipseXml.JAR_PREFIX +
                          EclipseXml.PLATFORM_PROTOCOL + "resources/" + VfsUtil.getRelativePath(javadocFile, myModel.getProject().getBaseDir(), '/') + javadocPath.substring(javadocFile.getUrl().length()) ;
          } else {
            javadocPath = EclipseXml.JAR_PREFIX + EclipseXml.FILE_PROTOCOL + path;
          }
        }
      }

      attrElement.setAttribute("value", javadocPath);      
    }
  }

  private String stripIDEASpecificPrefix(String path, String [] kind) {
    String stripped = StringUtil.strip(ProjectRootManagerImpl.extractLocalPath(PathMacroManager.getInstance(myModel.getModule()).collapsePath(path)),
                                             new CharFilter() {
                                               public boolean accept(final char ch) {
                                                 return ch != '$';
                                               }
                                             });
    boolean leaveLeadingSlash = false;
    if (!Comparing.strEqual(stripped, ProjectRootManagerImpl.extractLocalPath(path))) {
      leaveLeadingSlash = kind[0] == null;
      kind[0] = EclipseXml.VAR_KIND;
    }
    return (leaveLeadingSlash ? "/" : "") + stripped;
  }

  public boolean writeIDEASpecificClasspath(final Element root) throws WriteExternalException {

    boolean isModified = false;

    final CompilerModuleExtension compilerModuleExtension = myModel.getModuleExtension(CompilerModuleExtension.class);

    if (compilerModuleExtension.getCompilerOutputPathForTests() != null) {
      final Element pathElement = new Element(IdeaXml.OUTPUT_TEST_TAG);
      pathElement.setAttribute(IdeaXml.URL_ATTR, compilerModuleExtension.getCompilerOutputUrlForTests());
      root.addContent(pathElement);
      isModified = true;
    }
    if (compilerModuleExtension.isCompilerOutputPathInherited()) {
      root.setAttribute(IdeaXml.INHERIT_COMPILER_OUTPUT_ATTR, String.valueOf(true));
      isModified = true;
    }
    if (compilerModuleExtension.isExcludeOutput()) {
      root.addContent(new Element(IdeaXml.EXCLUDE_OUTPUT_TAG));
      isModified = true;
    }

    final LanguageLevelModuleExtension languageLevelModuleExtension = myModel.getModuleExtension(LanguageLevelModuleExtension.class);
    final LanguageLevel languageLevel = languageLevelModuleExtension.getLanguageLevel();
    if (languageLevel != null) {
      languageLevelModuleExtension.writeExternal(root);
      isModified = true;
    }

    for (ContentEntry entry : myModel.getContentEntries()) {
      for (SourceFolder sourceFolder : entry.getSourceFolders()) {
        if (sourceFolder.isTestSource()) {
          Element element = new Element(IdeaXml.TEST_FOLDER_TAG);
          root.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, sourceFolder.getUrl());
          isModified = true;
        }
      }

      for (ExcludeFolder excludeFolder : entry.getExcludeFolders()) {
        Element element = new Element(IdeaXml.EXCLUDE_FOLDER_TAG);
        root.addContent(element);
        element.setAttribute(IdeaXml.URL_ATTR, excludeFolder.getUrl());
        isModified = true;
      }
    }

    for (OrderEntry entry : myModel.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry && ((LibraryOrderEntry)entry).isModuleLevel()) {
        final String[] urls = entry.getUrls(OrderRootType.SOURCES);
        if (urls.length > 1) {
          final Element element = new Element("lib");
          element.setAttribute("name", entry.getPresentableName());
          for(int i = 0; i < urls.length - 1; i++) {
            Element srcElement = new Element("srcroot");
            srcElement.setAttribute("url", urls[i]);
            element.addContent(srcElement);
          }
          root.addContent(element);
        }
      }
    }

    PathMacroManager.getInstance(myModel.getModule()).collapsePaths(root);

    return isModified;
  }


  private static Element addOrderEntry(String kind, String path, Element classpathRoot, Element oldRoot) {
    if (oldRoot != null) {
      for (Object o : oldRoot.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
        final Element oldChild = (Element)o;
        final String oldKind = oldChild.getAttributeValue(EclipseXml.KIND_ATTR);
        final String oldPath = oldChild.getAttributeValue(EclipseXml.PATH_ATTR);
        if (Comparing.strEqual(kind, oldKind) && Comparing.strEqual(path, oldPath)) {
          final Element element = (Element)oldChild.clone();
          classpathRoot.addContent(element);
          return element;
        }
      }
    }
    Element orderEntry = new Element(EclipseXml.CLASSPATHENTRY_TAG);
    orderEntry.setAttribute(EclipseXml.KIND_ATTR, kind);
    if (path != null) {
      orderEntry.setAttribute(EclipseXml.PATH_ATTR, path);
    }
    classpathRoot.addContent(orderEntry);
    return orderEntry;
  }

  private static void setExported(Element orderEntry, ExportableOrderEntry dependency) {
    setOrRemoveAttribute(orderEntry, EclipseXml.EXPORTED_ATTR, dependency.isExported() ? EclipseXml.TRUE_VALUE : null);
  }

  private static void setOrRemoveAttribute(Element element, String name, String value) {
    if (value != null) {
      element.setAttribute(name, value);
    }
    else {
      element.removeAttribute(name);
    }
  }

  private static void setAttributeIfAbsent(Element element, String name, String value) {
    if (element.getAttribute(name) == null) {
      element.setAttribute(name, value);
    }
  }

}