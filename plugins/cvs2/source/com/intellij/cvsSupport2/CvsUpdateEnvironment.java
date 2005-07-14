/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.actions.merge.CvsMergeAction;
import com.intellij.cvsSupport2.actions.update.UpdateSettingsOnCvsConfiguration;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutor;
import com.intellij.cvsSupport2.cvsExecution.CvsOperationExecutorCallback;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.cvshandlers.CommandCvsHandler;
import com.intellij.cvsSupport2.cvshandlers.CvsUpdatePolicy;
import com.intellij.cvsSupport2.cvshandlers.UpdateHandler;
import com.intellij.cvsSupport2.updateinfo.UpdatedFilesProcessor;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.cvsIntegration.CvsResult;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.merge.AbstractMergeAction;
import com.intellij.openapi.vcs.update.*;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.*;

public class CvsUpdateEnvironment implements UpdateEnvironment {
  private final Project myProject;

  public CvsUpdateEnvironment(Project project) {
    myProject = project;

  }

  public void fillGroups(UpdatedFiles updatedFiles) {
    CvsUpdatePolicy.fillGroups(updatedFiles);
  }

  public UpdateSession updateDirectories(FilePath[] contentRoots, final UpdatedFiles updatedFiles, ProgressIndicator progressIndicator) {
    CvsConfiguration cvsConfiguration = CvsConfiguration.getInstance(myProject);
    if (!CvsVcs2.getInstance(myProject).getUpdateOptions().getValue()) {
      cvsConfiguration.CLEAN_COPY = false;
      cvsConfiguration.RESET_STICKY = false;
    }

    try {
      final UpdateSettingsOnCvsConfiguration updateSettings = new UpdateSettingsOnCvsConfiguration(cvsConfiguration,
                                                                                                   cvsConfiguration.CLEAN_COPY,
                                                                                                   cvsConfiguration.RESET_STICKY);
      final UpdateHandler handler = CommandCvsHandler.createUpdateHandler(contentRoots,
                                                                          updateSettings, myProject, updatedFiles);
      handler.addCvsListener(new UpdatedFilesProcessor(updatedFiles));
      CvsOperationExecutor cvsOperationExecutor = new CvsOperationExecutor(true, myProject, ModalityState.defaultModalityState());
      cvsOperationExecutor.setShowErrors(false);
      cvsOperationExecutor.performActionSync(handler, new CvsOperationExecutorCallback() {
        public void executionFinished(boolean successfully) {

        }

        public void executionFinishedSuccessfully() {

        }

        public void executeInProgressAfterAction(ModalityContext modaityContext) {

        }
      });
      final CvsResult result = cvsOperationExecutor.getResult();
      return new UpdateSessionAdapter(result.getErrorsAndWarnings(), result.isCanceled() || !result.isLoggedIn()) {
        public void onRefreshFilesCompleted() {
          if (!updatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID).isEmpty()) {
            ApplicationManager.getApplication().invokeAndWait(new Runnable() {
              public void run() {
                invokeManualMerging(updatedFiles.getGroupById(FileGroup.MERGED_WITH_CONFLICT_ID), myProject);
              }
            }, ModalityState.defaultModalityState());
          }
        }
      };
    }
    finally {
      cvsConfiguration.CLEAN_COPY = false;
      cvsConfiguration.RESET_STICKY = false;                    
    }
  }

  private void invokeManualMerging(FileGroup mergedWithConflict, Project project) {
    Collection<String> paths = mergedWithConflict.getFiles();
    Map<VirtualFile, List<String>> fileToRevisions = new LinkedHashMap<VirtualFile, List<String>>();
    for (final String path : paths) {
      VirtualFile virtualFile = CvsVfsUtil.findFileByIoFile(new File(path));
      if (virtualFile != null) {
        final List<String> allRevisionsForFile = CvsUtil.getAllRevisionsForFile(virtualFile);
        if (!allRevisionsForFile.isEmpty()) fileToRevisions.put(virtualFile, allRevisionsForFile);
      }
    }

    if (!fileToRevisions.isEmpty()) {
      final List<VirtualFile> mergedFiles = new ArrayList<VirtualFile>(fileToRevisions.keySet());
      new CvsMergeAction(mergedFiles.get(0), project, fileToRevisions, new AbstractMergeAction.FileValueHolder()).execute(null);
    }

  }


  public Configurable createConfigurable(Collection<FilePath> files) {
    CvsConfiguration.getInstance(myProject).CLEAN_COPY = false;
    CvsConfiguration.getInstance(myProject).RESET_STICKY = false;
    return new UpdateConfigurable(myProject, files);
  }
}
