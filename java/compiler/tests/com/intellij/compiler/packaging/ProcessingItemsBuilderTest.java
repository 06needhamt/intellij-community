/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.compiler.packaging;

import com.intellij.compiler.impl.packagingCompiler.DestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.JarDestinationInfo;
import com.intellij.compiler.impl.packagingCompiler.JarInfo;
import com.intellij.compiler.impl.packagingCompiler.PackagingProcessingItem;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public class ProcessingItemsBuilderTest extends IncrementalPackagingTestCase {

  public void testCopyFileToExploded() throws Exception {
    doTest(true, false, start().copy("a.jsp", "/a.jsp"));
  }

  public void testCopyDirToExploded() throws Exception {
    doTest(true, false, start().copy("dir", "/out", "a.jsp", "b.jsp"));
  }

  public void testCopyFileToJar() throws Exception {
    doTest(false, true, start().copy("a.jsp", "/a.jsp"));
  }

  public void testCopyDirToJar() throws Exception {
    doTest(false, true, start().copy("dir", "/out", "a.jsp", "b.jsp"));
  }

  public void testJarDirectory() throws Exception {
    doTest(true, true, start().jar("dir", "/path/to/inner.jar", "a.html", "b.html"));
  }

  public void testCopyFileToParentExternal() throws Exception {
    doTest(new MockBuildConfiguration(true, true, "/out/exploded", "/out2/my.jar"), start()
      .copy("a.jar", "../a.jar"));
  }

  private File getExpectedFile(final String expectedFileName) {
    return new File(PathManagerEx.getTestDataPath(), "compiler" + File.separator + "packaging" +
                                                     File.separator + expectedFileName);
  }

  private void doTest(final boolean explodedEnabled, final boolean jarEnabled, BuildRecipeInfo info) throws IOException {
    doTest(new MockBuildConfiguration(explodedEnabled, jarEnabled), info);
  }
  private void doTest(final MockBuildConfiguration mockBuildConfiguration, BuildRecipeInfo info) throws IOException {
    final PackagingProcessingItem[] items = buildItems(info.myBuildRecipe, mockBuildConfiguration);
    final String s = printItems(items);
    final File file = getExpectedFile(getTestName(true) + ".txt");
    String expected = loadText(file);
    assertEquals(expected, s);
  }

  private String printItems(PackagingProcessingItem[] items) {

    List<Pair<JarInfo, String>> jarContent = getJarsContent(items);

    Map<JarInfo, Integer> jar2Num = new HashMap<JarInfo, Integer>();
    for (int i = 0; i < jarContent.size(); i++) {
      Pair<JarInfo, String> pair = jarContent.get(i);
      jar2Num.put(pair.getFirst(), i);
    }

    List<String> output = new ArrayList<String>();
    for (PackagingProcessingItem item : items) {
      for (DestinationInfo destination : item.getDestinations()) {
        String o = item.getFile().getPath() + " -> " + destination.getOutputPath();
        final String d = printDestination(jar2Num, destination, false);
        output.add(o + (d.length() > 0 ? " (" + d + ")" : ""));
      }
    }
    Collections.sort(output);

    final StringBuilder builder = new StringBuilder();
    for (String s : output) {
      builder.append(s).append("\n");
    }

    for (Pair<JarInfo, String> pair : jarContent) {
      builder.append("\n");
      JarInfo jarInfo = pair.getFirst();
      builder.append("jar#").append(jar2Num.get(jarInfo));
      builder.append("\n");

      builder.append(pair.getSecond()).append("->").append("\n");

      List<String> to = new ArrayList<String>();
      for (DestinationInfo destinationInfo : jarInfo.getAllDestinations()) {
        to.add(printDestination(jar2Num, destinationInfo, true));
      }
      Collections.sort(to);

      for (String s : to) {
        builder.append("  ").append(s).append(";\n");
      }
    }
    return builder.toString();
  }

  private String printDestination(final Map<JarInfo, Integer> jar2Num, final DestinationInfo destination, boolean detailed) {
    String s = "";
    if (destination instanceof JarDestinationInfo) {
      final JarDestinationInfo jarDestination = (JarDestinationInfo)destination;
      s = "jar#" + jar2Num.get(jarDestination.getJarInfo()) + jarDestination.getPathInJar() +
          (detailed ? " in " + jarDestination.getOutputFile().getPath() : "") + "";
    }
    else if (detailed) {
      final VirtualFile outputFile = destination.getOutputFile();
      assertNotNull(outputFile);
      assertEquals(destination.getOutputPath(), outputFile.getPath());
      s = destination.getOutputPath();
    }
    return s;
  }

}
