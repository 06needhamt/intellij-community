package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.wc.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * @author max
 */
public class SvnChangeProvider implements ChangeProvider {
  private SvnVcs myVcs;

  public SvnChangeProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, ProgressIndicator progress) throws VcsException {
    try {
      final SVNStatusClient client = myVcs.createStatusClient();
      for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
        processFile(path, client, builder, true);
      }

      for (FilePath path : dirtyScope.getDirtyFiles()) {
        processFile(path, client, builder, false);
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  private static void processFile(FilePath path, SVNStatusClient stClient, final ChangelistBuilder builder, final boolean recursively)
    throws SVNException {
    try {
      if (path.isDirectory()) {
        stClient.doStatus(path.getIOFile(), recursively, false, false, true, new ISVNStatusHandler() {
          public void handleStatus(SVNStatus status) throws SVNException {
            FilePath path = VcsUtil.getFilePath(status.getFile(), status.getKind().equals(SVNNodeKind.DIR));
            processStatus(path, status, builder);
            if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED && path.isDirectory()) {
              // process children of this file with another client.
              SVNStatusClient client = new SVNStatusClient(null, null);
              if (recursively && path.isDirectory()) {
                VirtualFile[] children = path.getVirtualFile().getChildren();
                for (VirtualFile aChildren : children) {
                  FilePath filePath = VcsUtil.getFilePath(aChildren.getPath(), aChildren.isDirectory());
                  processFile(filePath, client, builder, recursively);
                }
              }
            }
          }
        });
      } else {
        processFile(path, stClient, builder);
      }
    } catch (SVNException e) {
      if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_DIRECTORY) {
        final VirtualFile virtualFile = path.getVirtualFile();
        builder.processUnversionedFile(virtualFile);
        // process children recursively!
        if (recursively && path.isDirectory() && virtualFile != null) {
          VirtualFile[] children = virtualFile.getChildren();
          for (VirtualFile child : children) {
            FilePath filePath = VcsUtil.getFilePath(child.getPath(), child.isDirectory());
            processFile(filePath, stClient, builder, recursively);
          }
        }
      }
      else {
        throw e;
      }
    }
  }

  private static void processFile(FilePath filePath, SVNStatusClient stClient, ChangelistBuilder builder) throws SVNException {
    SVNStatus status = stClient.doStatus(filePath.getIOFile(), false, false);
    processStatus(filePath, status, builder);
  }

  private static void processStatus(final FilePath filePath, final SVNStatus status, final ChangelistBuilder builder) throws SVNException {
    loadEntriesFile(filePath);
    if (status != null) {
      FileStatus fStatus = convertStatus(status, filePath.getIOFile());

      final SVNStatusType statusType = status.getContentsStatus();
      final SVNStatusType propStatus = status.getPropertiesStatus();
      if (statusType == SVNStatusType.STATUS_UNVERSIONED || statusType == SVNStatusType.UNKNOWN) {
        builder.processUnversionedFile(filePath.getVirtualFile());
      }
      else if (statusType == SVNStatusType.STATUS_CONFLICTED ||
               statusType == SVNStatusType.STATUS_MODIFIED ||
               statusType == SVNStatusType.STATUS_REPLACED ||
               propStatus == SVNStatusType.STATUS_MODIFIED) {
        builder.processChange(new Change(new SvnUpToDateRevision(filePath, status.getRevision()), new CurrentContentRevision(filePath), fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_ADDED) {
        builder.processChange(new Change(null, new CurrentContentRevision(filePath), fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_DELETED) {
        builder.processChange(new Change(new SvnUpToDateRevision(filePath, status.getRevision()), null, fStatus));
      }
      else if (statusType == SVNStatusType.STATUS_MISSING) {
        builder.processLocallyDeletedFile(filePath);
      }
      else if (statusType == SVNStatusType.STATUS_IGNORED) {
        builder.processIgnoredFile(filePath.getVirtualFile());
      }
      else if (fStatus == FileStatus.NOT_CHANGED) {
        VirtualFile file = filePath.getVirtualFile();
        if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
          builder.processChange(new Change(new SvnUpToDateRevision(filePath, status.getRevision()), new CurrentContentRevision(filePath), FileStatus.MODIFIED));
        }
      }
    }
  }

  private static String getLastUpToDateContentFor(final File file, final String charset) {
    SVNWCClient wcClient = new SVNWCClient(null, null);
    try {
      File lock = new File(file.getParentFile(), SvnUtil.PATH_TO_LOCK_FILE);
      if (lock.exists()) {
        return null;
      }
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      wcClient.doGetFileContents(file, SVNRevision.UNDEFINED, SVNRevision.BASE, true, buffer);
      buffer.close();
      return new String(buffer.toByteArray(), charset);
    }
    catch (SVNException e) {
      return null;
    }
    catch (UnsupportedEncodingException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

  private static FileStatus convertStatus(final SVNStatus status, final File file) throws SVNException {
    if (status == null) {
      return FileStatus.UNKNOWN;
    }
    if (status.getContentsStatus() == SVNStatusType.STATUS_UNVERSIONED) {
      return FileStatus.UNKNOWN;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MISSING) {
      return FileStatus.DELETED_FROM_FS;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_EXTERNAL) {
      return SvnFileStatus.EXTERNAL;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_OBSTRUCTED) {
      return SvnFileStatus.OBSTRUCTED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_IGNORED) {
      return FileStatus.IGNORED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
      return FileStatus.ADDED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_DELETED) {
      return FileStatus.DELETED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_REPLACED) {
      return SvnFileStatus.REPLACED;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_CONFLICTED ||
             status.getPropertiesStatus() == SVNStatusType.STATUS_CONFLICTED) {
      return FileStatus.MERGED_WITH_CONFLICTS;
    }
    else if (status.getContentsStatus() == SVNStatusType.STATUS_MODIFIED ||
             status.getPropertiesStatus() == SVNStatusType.STATUS_MODIFIED) {
      return FileStatus.MODIFIED;
    }
    else if (status.isSwitched()) {
      return SvnFileStatus.SWITCHED;
    }
    else if (status.isCopied()) {
      return FileStatus.ADDED;
    }
    if (file.isDirectory() && file.getParentFile() != null) {
      String childURL = null;
      String parentURL = null;
      SVNWCAccess wcAccess = SVNWCAccess.newInstance(null);
      try {
        wcAccess.open(file, false, 0);
        SVNAdminArea parentDir = wcAccess.open(file.getParentFile(), false, 0);
        if (wcAccess.getEntry(file, false) != null) {
          childURL = wcAccess.getEntry(file, false).getURL();
        }
        if (parentDir != null) {
          parentURL = parentDir.getEntry("", false).getURL();
        }
      }
      finally {
        try {
          wcAccess.close();
        } catch (SVNException e) {
          //
        }
      }
        try {
            if (parentURL != null && !SVNWCUtil.isWorkingCopyRoot(file)) {
              parentURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(file.getName()));
              if (childURL != null && !parentURL.equals(childURL)) {
                return SvnFileStatus.SWITCHED;
              }
            }
        } catch (SVNException e) {
            //
        }
    }
    return FileStatus.NOT_CHANGED;
  }

  public static void loadEntriesFile(final FilePath filePath) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            if (filePath.getParentPath() == null) {
              return;
            }
            File svnSubdirectory = new File(filePath.getParentPath().getIOFile(), SvnUtil.SVN_ADMIN_DIR_NAME);
            LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
            VirtualFile file = localFileSystem.refreshAndFindFileByIoFile(svnSubdirectory);
            if (file != null) {
              localFileSystem.refreshAndFindFileByIoFile(new File(svnSubdirectory, SvnUtil.ENTRIES_FILE_NAME));
            }
            if (filePath.isDirectory()) {
              svnSubdirectory = new File(filePath.getPath(), SvnUtil.SVN_ADMIN_DIR_NAME);
              file = localFileSystem.refreshAndFindFileByIoFile(svnSubdirectory);
              if (file != null) {
                localFileSystem.refreshAndFindFileByIoFile(new File(svnSubdirectory, SvnUtil.ENTRIES_FILE_NAME));
              }
            }
          }
        });
      }
    });
  }

  private static class SvnUpToDateRevision implements ContentRevision {
    private final FilePath myFile;
    private String myContent = null;
    private VcsRevisionNumber myRevNumber;

    public SvnUpToDateRevision(@NotNull final FilePath file, final SVNRevision revision) {
      myFile = file;
      myRevNumber = new SvnRevisionNumber(revision);
    }

    @Nullable
    public String getContent() {
      if (myContent == null) {
        myContent = getLastUpToDateContentFor(myFile.getIOFile(), myFile.getCharset().name());
      }
      return myContent;
    }

    @NotNull
    public FilePath getFile() {
      return myFile;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return myRevNumber;
    }
  }
}
