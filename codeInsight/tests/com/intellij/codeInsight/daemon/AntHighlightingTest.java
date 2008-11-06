/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jul 13, 2006
 * Time: 12:55:30 AM
 */
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.idea.IdeaTestUtil;
import com.intellij.lang.ant.validation.AntDuplicateTargetsInspection;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.Collections;

/**
 * @by Maxim.Mossienko
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class AntHighlightingTest extends DaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/ant";
  private boolean myIgnoreInfos;

  private void doTest() throws Exception {
    doTest(BASE_PATH + "/" + getTestName(false) + ".xml", false, false);
  }

  public void testEntity() throws Exception {
    configureByFiles(null, new VirtualFile[]{getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".xml"),
      getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".ent")});
    doDoTest(true, false);
  }

  public void testSanity() throws Exception {
    doTest();
  }

  public void testSanity2() throws Exception {
    doTest();
  }

  public void testRefid() throws Exception {
    doTest();
  }

  public void testExternalValidator() throws Exception {
    doTest();
  }

  public void testProperties() throws Exception {
    configureByFiles(null, new VirtualFile[]{getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".xml"),
      getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".properties")});
    doDoTest(true, false);
  }

  public void testProperties2() throws Exception {
    configureByFiles(null, new VirtualFile[]{getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".xml"), getVirtualFile(BASE_PATH + "/" + "yguard.jar")});
    doDoTest(true, false);
  }

  public void testEscapedProperties() throws Exception {
    configureByFiles(null, new VirtualFile[]{getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".xml")});
    doDoTest(true, false);
  }

  public void testPropertiesFromFile() throws Exception {
    doTest();
  }

  public void testAntFileProperties() throws Exception {
    doTest();
  }

  public void testBigFile() throws Exception {
    configureByFiles(null, new VirtualFile[]{getVirtualFile(BASE_PATH + "/" + getTestName(false) + ".xml"),
      getVirtualFile(BASE_PATH + "/" + "buildserver.xml"), getVirtualFile(BASE_PATH + "/" + "buildserver.properties")});

    try {
      myIgnoreInfos = true;
      IdeaTestUtil.assertTiming("Should be quite performant !", 3500, new Runnable() {
        public void run() {
          doDoTest(true, false);
        }
      });
    }
    finally {
      myIgnoreInfos = false;
    }
  }


  protected Collection<HighlightInfo> doHighlighting() {
    final Collection<HighlightInfo> infos = super.doHighlighting();
    if (!myIgnoreInfos) {
      return infos;
    }
    return Collections.emptyList();
  }

  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new AntDuplicateTargetsInspection()};
  }
}
