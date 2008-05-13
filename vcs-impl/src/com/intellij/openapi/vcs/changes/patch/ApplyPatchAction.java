/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 17.11.2006
 * Time: 17:08:11
 */
package com.intellij.openapi.vcs.changes.patch;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.ActionButtonPresentation;
import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.impl.patch.*;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApplyPatchAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.patch.ApplyPatchAction");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final ApplyPatchDialog dialog = new ApplyPatchDialog(project);
    final VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (vFile != null && vFile.getFileType() == StdFileTypes.PATCH) {
      dialog.setFileName(vFile.getPresentableUrl());
    }
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }
    applyPatch(project, dialog.getPatches(), dialog.getApplyPatchContext(), dialog.getSelectedChangeList());
  }

  public static void applyPatch(final Project project, final List<? extends FilePatch> patches,
                                final ApplyPatchContext context, final LocalChangeList targetChangeList) {
    applyPatch(project, patches, context, targetChangeList, null);
  }

  public static void applyPatch(final Project project, final List<? extends FilePatch> patches,
                                final ApplyPatchContext context, final LocalChangeList targetChangeList,
                                final IndirectlyModifiedPathsGetter indirectGetter) {
    List<VirtualFile> filesToMakeWritable = new ArrayList<VirtualFile>();
    if (!prepareFiles(project, patches, context, filesToMakeWritable)) {
      return;
    }
    final VirtualFile[] fileArray = filesToMakeWritable.toArray(new VirtualFile[filesToMakeWritable.size()]);
    final ReadonlyStatusHandler.OperationStatus readonlyStatus = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(fileArray);
    if (readonlyStatus.hasReadonlyFiles()) {
      return;
    }
    applyFilePatches(project, patches, context);
    moveChangesToList(project, context.getAffectedFiles(), targetChangeList, indirectGetter);
  }

  public static ApplyPatchStatus applyFilePatches(final Project project, final List<? extends FilePatch> patches,
                                                  final ApplyPatchContext context) {
    final Ref<ApplyPatchStatus> statusRef = new Ref<ApplyPatchStatus>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(project, new Runnable() {
          public void run() {
            ApplyPatchStatus status = null;
            for(FilePatch patch: patches) {
              final ApplyPatchStatus patchStatus = applySinglePatch(project, patch, context);
              status = ApplyPatchStatus.and(status, patchStatus);
            }
            try {
              context.applyPendingRenames();
            }
            catch (IOException e) {
              Messages.showErrorDialog(project, "Error renaming directories: " + e.getMessage(),
                                       VcsBundle.message("patch.apply.dialog.title"));
            }
            if (status == ApplyPatchStatus.ALREADY_APPLIED) {
              Messages.showInfoMessage(project, VcsBundle.message("patch.apply.already.applied"),
                                       VcsBundle.message("patch.apply.dialog.title"));
            }
            else if (status == ApplyPatchStatus.PARTIAL) {
              Messages.showInfoMessage(project, VcsBundle.message("patch.apply.partially.applied"),
                                       VcsBundle.message("patch.apply.dialog.title"));
            }
            statusRef.set(status);
          }
        }, VcsBundle.message("patch.apply.command"), null);
      }
    });
    return statusRef.get();
  }

  public static boolean prepareFiles(final Project project, final List<? extends FilePatch> patches,
                                     final ApplyPatchContext context,
                                     final List<VirtualFile> filesToMakeWritable) {
    for(FilePatch patch: patches) {
      VirtualFile fileToPatch;
      try {
        fileToPatch = patch.findFileToPatch(context.getPrepareContext());
      }
      catch (IOException e) {
        Messages.showErrorDialog(project, "Error when searching for file to patch: " + patch.getBeforeName() + ": " + e.getMessage(),
                                 VcsBundle.message("patch.apply.dialog.title"));
        return false;
      }
      // security check to avoid overwriting system files with a patch
      if (fileToPatch != null && !ExcludedFileIndex.getInstance(project).isInContent(fileToPatch) &&
          ProjectLevelVcsManager.getInstance(project).getVcsRootFor(fileToPatch) == null) {
        Messages.showErrorDialog(project, "File to patch found outside content root: " + patch.getBeforeName(),
                                 VcsBundle.message("patch.apply.dialog.title"));
        return false;
      }
      if (fileToPatch != null && !fileToPatch.isDirectory()) {
        filesToMakeWritable.add(fileToPatch);
        FileType fileType = fileToPatch.getFileType();
        if (fileType == FileTypes.UNKNOWN && patch instanceof TextFilePatch) {
          fileType = FileTypeChooser.associateFileType(fileToPatch.getPresentableName());
          if (fileType == null) {
            return false;
          }
        }
      }
      else if (patch.isNewFile()) {
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(patch.getBeforeFileName());
        if (fileType == FileTypes.UNKNOWN && patch instanceof TextFilePatch) {
          fileType = FileTypeChooser.associateFileType(patch.getBeforeFileName());
          if (fileType == null) {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static ApplyPatchStatus applySinglePatch(final Project project, final FilePatch patch,
                                                   final ApplyPatchContext context) {
    VirtualFile file;
    try {
      file = patch.findFileToPatch(context);
    }
    catch (IOException e) {
      Messages.showErrorDialog(project, "Error when searching for file to patch: " + patch.getBeforeName() + ": " + e.getMessage(),
                               VcsBundle.message("patch.apply.dialog.title"));
      return ApplyPatchStatus.FAILURE;
    }
    if (file == null) {
      Messages.showErrorDialog(project, "Cannot find file to patch: " + patch.getBeforeName(),
                               VcsBundle.message("patch.apply.dialog.title"));
      return ApplyPatchStatus.FAILURE;
    }
    if (file.isDirectory() && !patch.isNewFile() && !patch.isDeletedFile()) {
      Messages.showErrorDialog(project, "Expected to find a file but found a directory: " + patch.getBeforeName(),
                               VcsBundle.message("patch.apply.dialog.title"));
      return ApplyPatchStatus.FAILURE;
    }

    try {
      return patch.apply(file, context, project);
    }
    catch(ApplyPatchException ex) {
      if (!patch.isNewFile() && !patch.isDeletedFile() && patch instanceof TextFilePatch) {
        ApplyPatchStatus mergeStatus = mergeAgainstBaseVersion(project, file, context, (TextFilePatch) patch,
                                                               ApplyPatchMergeRequestFactory.INSTANCE);
        if (mergeStatus != null) {
          return mergeStatus;
        }
      }
      Messages.showErrorDialog(project, VcsBundle.message("patch.apply.error", patch.getBeforeName(), ex.getMessage()),
                               VcsBundle.message("patch.apply.dialog.title"));
    }
    catch (Exception ex) {
      LOG.error(ex);
    }
    return ApplyPatchStatus.FAILURE;
  }

  @Nullable
  public static ApplyPatchStatus mergeAgainstBaseVersion(Project project, VirtualFile file, ApplyPatchContext context,
                                                         final TextFilePatch patch,
                                                         final PatchMergeRequestFactory mergeRequestFactory) {
    FilePath pathBeforeRename = context.getPathBeforeRename(file);
    final DefaultPatchBaseVersionProvider provider = new DefaultPatchBaseVersionProvider(project);
    if (provider.canProvideContent(file, patch.getBeforeVersionId())) {
      final StringBuilder newText = new StringBuilder();
      final Ref<CharSequence> contentRef = new Ref<CharSequence>();
      final Ref<ApplyPatchStatus> statusRef = new Ref<ApplyPatchStatus>();
      try {
        provider.getBaseVersionContent(file, pathBeforeRename, patch.getBeforeVersionId(), new Processor<CharSequence>() {
          public boolean process(final CharSequence text) {
            newText.setLength(0);
            try {
              statusRef.set(patch.applyModifications(text, newText));
            }
            catch(ApplyPatchException ex) {
              return true;  // continue to older versions
            }
            contentRef.set(text);
            return false;
          }
        });
      }
      catch (VcsException vcsEx) {
        Messages.showErrorDialog(project, VcsBundle.message("patch.load.base.revision.error", patch.getBeforeName(), vcsEx.getMessage()),
                                 VcsBundle.message("patch.apply.dialog.title"));
        return ApplyPatchStatus.FAILURE;
      }
      ApplyPatchStatus status = statusRef.get();
      if (status != null) {
        if (status != ApplyPatchStatus.ALREADY_APPLIED) {
          return showMergeDialog(project, file, contentRef.get(), newText.toString(), mergeRequestFactory);
        }
        else {
          return status;
        }
      }
    }
    return null;
  }

  private static ApplyPatchStatus showMergeDialog(Project project, VirtualFile file, CharSequence content, final String patchedContent,
                                                  final PatchMergeRequestFactory mergeRequestFactory) {
    CharSequence fileContent = LoadTextUtil.loadText(file);

    final MergeRequest request = mergeRequestFactory.createMergeRequest(fileContent.toString(), patchedContent, content.toString(), file,
                                                      project);
    DiffManager.getInstance().getDiffTool().show(request);
    if (request.getResult() == DialogWrapper.OK_EXIT_CODE) {
      return ApplyPatchStatus.SUCCESS;
    }
    request.restoreOriginalContent();
    return ApplyPatchStatus.FAILURE;
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (e.getPlace().equals(ActionPlaces.PROJECT_VIEW_POPUP)) {
      VirtualFile vFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
      e.getPresentation().setVisible(project != null && vFile != null && vFile.getFileType() == StdFileTypes.PATCH);
    }
    else {
      e.getPresentation().setEnabled(project != null);
    }
  }

  public static void moveChangesToList(final Project project, final List<FilePath> files, final LocalChangeList targetChangeList) {
    moveChangesToList(project, files, targetChangeList, null);
  }

  public static void moveChangesToList(final Project project, final List<FilePath> files, final LocalChangeList targetChangeList,
                                       final IndirectlyModifiedPathsGetter indirectGetter) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (targetChangeList != changeListManager.getDefaultChangeList()) {
      changeListManager.invokeAfterUpdate(new Runnable() {
        public void run() {
          List<Change> changes = new ArrayList<Change>();
          for(FilePath file: files) {
            final Change change = changeListManager.getChange(file);
            if (change != null) {
              changes.add(change);
            }
          }

          if (indirectGetter != null) {
            indirectGetter.appendPaths(changeListManager, changes);
          }

          changeListManager.moveChangesTo(targetChangeList, changes.toArray(new Change[changes.size()]));
        }
      });
    }
  }

  public interface IndirectlyModifiedPathsGetter {
    void appendPaths(final ChangeListManager changeListManager, final List<Change> changes);
  }

  private static class ApplyPatchMergeRequestFactory implements PatchMergeRequestFactory {
    public static final ApplyPatchMergeRequestFactory INSTANCE = new ApplyPatchMergeRequestFactory();

    public MergeRequest createMergeRequest(final String leftText, final String rightText, final String originalContent, @NotNull final VirtualFile file,
                                           final Project project) {
      MergeRequest request = DiffRequestFactory.getInstance().createMergeRequest(leftText, rightText, originalContent,
                                                                                 file, project, ActionButtonPresentation.createApplyButton());
      request.setVersionTitles(new String[] {
        VcsBundle.message("patch.apply.conflict.local.version"),
        VcsBundle.message("patch.apply.conflict.merged.version"),
        VcsBundle.message("patch.apply.conflict.patched.version")
      });
      request.setWindowTitle(VcsBundle.message("patch.apply.conflict.title", file.getPresentableUrl()));
      return request;
    }
  }
}