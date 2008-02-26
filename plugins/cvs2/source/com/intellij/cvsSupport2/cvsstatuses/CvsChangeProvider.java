package com.intellij.cvsSupport2.cvsstatuses;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.application.CvsInfo;
import com.intellij.cvsSupport2.checkinProject.DirectoryContent;
import com.intellij.cvsSupport2.checkinProject.VirtualFileEntry;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetFileContentOperation;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import com.intellij.cvsSupport2.errorHandling.CannotFindCvsRootException;
import com.intellij.cvsSupport2.history.CvsRevisionNumber;
import com.intellij.cvsSupport2.util.CvsVfsUtil;
import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.admin.Entry;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Collection;
import java.util.Date;

/**
 * @author max
 */
public class CvsChangeProvider implements ChangeProvider {
  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.cvsstatuses.CvsChangeProvider");

  private final CvsVcs2 myVcs;
  private final CvsEntriesManager myEntriesManager;

  public CvsChangeProvider(final CvsVcs2 vcs, CvsEntriesManager entriesManager) {
    myVcs = vcs;
    myEntriesManager = entriesManager;
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Processing changes for scope " + dirtyScope);
    }
    for (FilePath path : dirtyScope.getRecursivelyDirtyDirectories()) {
      final VirtualFile dir = path.getVirtualFile();
      if (dir != null) {
        processEntriesIn(dir, dirtyScope, builder, true);
      }
      else {
        processFile(path, builder);
      }
    }

    for (FilePath path : dirtyScope.getDirtyFiles()) {
      if (path.isDirectory()) {
        final VirtualFile dir = path.getVirtualFile();
        if (dir != null) {
          processEntriesIn(dir, dirtyScope, builder, false);
        }
        else {
          processFile(path, builder);
        }
      }
      else {
        processFile(path, builder);
      }
    }
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return true;
  }

  private void processEntriesIn(@NotNull VirtualFile dir, VcsDirtyScope scope, ChangelistBuilder builder, boolean recursively) {
    final FilePath path = VcsContextFactory.SERVICE.getInstance().createFilePathOn(dir);
    if (!scope.belongsTo(path)) return;
    final DirectoryContent dirContent = getDirectoryContent(dir);

    for (VirtualFile file : dirContent.getUnknownFiles()) {
      builder.processUnversionedFile(file);
    }
    for (VirtualFile file : dirContent.getIgnoredFiles()) {
      builder.processIgnoredFile(file);
    }

    for (Entry entry : dirContent.getDeletedDirectories()) {
      builder.processLocallyDeletedFile(VcsUtil.getFilePath(CvsVfsUtil.getFileFor(dir, entry.getFileName()), true));
    }

    for (Entry entry : dirContent.getDeletedFiles()) {
      builder.processLocallyDeletedFile(VcsUtil.getFilePath(CvsVfsUtil.getFileFor(dir, entry.getFileName()), false));
    }

    /*
    final Collection<VirtualFile> unknownDirs = dirContent.getUnknownDirectories();
    for (VirtualFile file : unknownDirs) {
      builder.processUnversionedFile(file);
    }
    */

    checkSwitchedDir(dir, builder, scope);

    if (CvsUtil.fileIsUnderCvs(dir) && dir.getChildren().length == 1 /* admin dir */ &&
        dirContent.getDeletedFiles().isEmpty() && hasRemovedFiles(dirContent.getFiles())) {
      // directory is going to be deleted
      builder.processChange(new Change(CurrentContentRevision.create(path), CurrentContentRevision.create(path), FileStatus.DELETED));
    }
    for (VirtualFileEntry fileEntry : dirContent.getFiles()) {
      processFile(dir, fileEntry.getVirtualFile(), fileEntry.getEntry(), builder);
    }

    if (recursively) {
      final VirtualFile[] children = CvsVfsUtil.getChildrenOf(dir);
      if (children != null) {
        for (VirtualFile file : children) {
          if (file.isDirectory() && !ProjectRootManager.getInstance(myVcs.getProject()).getFileIndex().isIgnored(file)) {
            processEntriesIn(file, scope, builder, true);
          }
        }
      }
    }
  }

  private static boolean hasRemovedFiles(final Collection<VirtualFileEntry> files) {
    for(VirtualFileEntry e: files) {
      if (e.getEntry().isRemoved()) {
        return true;
      }
    }
    return false;
  }


  private void processFile(final FilePath filePath, final ChangelistBuilder builder) {
    final VirtualFile dir = filePath.getVirtualFileParent();
    if (dir == null) return;

    final Entry entry = CvsEntriesManager.getInstance().getEntryFor(dir, filePath.getName());
    final FileStatus status = CvsStatusProvider.getStatus(filePath.getVirtualFile(), entry);
    VcsRevisionNumber number = entry != null ? new CvsRevisionNumber(entry.getRevision()) : VcsRevisionNumber.NULL;
    processStatus(filePath, dir.findChild(filePath.getName()), status, number, entry != null && entry.isBinary(), builder);
    checkSwitchedFile(filePath, builder, dir, entry);
  }

  private void processFile(final VirtualFile dir, @Nullable VirtualFile file, Entry entry, final ChangelistBuilder builder) {
    final FilePath filePath = VcsContextFactory.SERVICE.getInstance().createFilePathOn(dir, entry.getFileName());
    final FileStatus status = CvsStatusProvider.getStatus(file, entry);
    final VcsRevisionNumber number = new CvsRevisionNumber(entry.getRevision());
    processStatus(filePath, file, status, number, entry.isBinary(), builder);
    checkSwitchedFile(filePath, builder, dir, entry);
  }

  private void checkSwitchedDir(final VirtualFile dir, final ChangelistBuilder builder, final VcsDirtyScope scope) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myVcs.getProject()).getFileIndex();
    VirtualFile parentDir = dir.getParent();
    if (parentDir == null || !fileIndex.isInContent(parentDir)) {
      return;
    }
    final CvsInfo info = CvsEntriesManager.getInstance().getCvsInfoFor(dir);
    if (info.getRepository() == null) {
      // don't report unversioned directories as switched (IDEADEV-17178)
      builder.processUnversionedFile(dir);
      return;
    }
    final String dirTag = info.getStickyTag();
    final CvsInfo parentInfo = CvsEntriesManager.getInstance().getCvsInfoFor(parentDir);
    final String parentDirTag = parentInfo.getStickyTag();
    if (!Comparing.equal(dirTag, parentDirTag)) {
      if (dirTag == null) {
        builder.processSwitchedFile(dir, CvsUtil.HEAD, true);
      }
      else if (CvsUtil.isNonDateTag(dirTag)) {
        final String tag = dirTag.substring(1);
        // a switch between a branch tag and a non-branch tag is not a switch
        if (parentDirTag != null && CvsUtil.isNonDateTag(parentDirTag)) {
          String parentTag = parentDirTag.substring(1);
          if (tag.equals(parentTag)) {
            return;
          }
        }
        builder.processSwitchedFile(dir, CvsBundle.message("switched.tag.format", tag), true);
      }
      else if (dirTag.startsWith(CvsUtil.STICKY_DATE_PREFIX)) {
        try {
          Date date = Entry.STICKY_DATE_FORMAT.parse(dirTag.substring(1));
          builder.processSwitchedFile(dir, CvsBundle.message("switched.date.format", date), true);
        }
        catch (ParseException e) {
          builder.processSwitchedFile(dir, CvsBundle.message("switched.date.format", dirTag.substring(1)), true);
        }
      }
    }
    else if (!scope.belongsTo(VcsContextFactory.SERVICE.getInstance().createFilePathOn(parentDir))) {
      // check if we're doing a partial refresh below a switched dir (IDEADEV-16611)
      final String parentBranch = ChangeListManager.getInstance(myVcs.getProject()).getSwitchedBranch(parentDir);
      if (parentBranch != null) {
        builder.processSwitchedFile(dir, parentBranch, true);
      }
    }
  }

  private void checkSwitchedFile(final FilePath filePath, final ChangelistBuilder builder, final VirtualFile dir, final Entry entry) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myVcs.getProject()).getFileIndex();
    // if content root itself is switched, ignore
    if (!fileIndex.isInContent(dir)) {
      return;
    }
    final String dirTag = CvsEntriesManager.getInstance().getCvsInfoFor(dir).getStickyTag();
    String dirStickyInfo = getStickyInfo(dirTag);
    if (entry != null && !Comparing.equal(entry.getStickyInformation(), dirStickyInfo)) {
      VirtualFile file = filePath.getVirtualFile();
      if (file != null) {
        if (entry.getStickyTag() != null) {
          builder.processSwitchedFile(file, CvsBundle.message("switched.tag.format", entry.getStickyTag()), false);
        }
        else if (entry.getStickyDate() != null) {
          builder.processSwitchedFile(file, CvsBundle.message("switched.date.format", entry.getStickyDate()), false);
        }
        else if (entry.getStickyRevision() != null) {
          builder.processSwitchedFile(file, CvsBundle.message("switched.revision.format", entry.getStickyRevision()), false);
        }
        else {
          builder.processSwitchedFile(file, CvsUtil.HEAD, false);
        }
      }
    }
  }

  @Nullable
  private static String getStickyInfo(final String dirTag) {
    return (dirTag != null && dirTag.length() > 1) ? dirTag.substring(1) : null;
  }

  private void processStatus(final FilePath filePath,
                             final VirtualFile file,
                             final FileStatus status,
                             final VcsRevisionNumber number,
                             final boolean isBinary,
                             final ChangelistBuilder builder) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("processStatus: filePath=" + filePath + " status=" + status);
    }
    if (status == FileStatus.NOT_CHANGED) {
      if (file != null && FileDocumentManager.getInstance().isFileModified(file)) {
        builder.processChange(
          new Change(createCvsRevision(filePath, number, isBinary), CurrentContentRevision.create(filePath), FileStatus.MODIFIED));
      }
      return;
    }
    if (status == FileStatus.MODIFIED || status == FileStatus.MERGE || status == FileStatus.MERGED_WITH_CONFLICTS) {
      builder.processChange(new Change(createCvsRevision(filePath, number, isBinary), CurrentContentRevision.create(filePath), status));
    }
    else if (status == FileStatus.ADDED) {
      builder.processChange(new Change(null, CurrentContentRevision.create(filePath), status));
    }
    else if (status == FileStatus.DELETED) {
      builder.processChange(new Change(createCvsRevision(filePath, number, isBinary), null, status));
    }
    else if (status == FileStatus.DELETED_FROM_FS) {
      builder.processLocallyDeletedFile(filePath);
    }
    else if (status == FileStatus.UNKNOWN) {
      builder.processUnversionedFile(filePath.getVirtualFile());
    }
    else if (status == FileStatus.IGNORED) {
      builder.processIgnoredFile(filePath.getVirtualFile());
    }
  }

  @Nullable
  public byte[] getLastUpToDateContentFor(@NotNull final VirtualFile f) {
    final long upToDateTimestamp = getUpToDateTimeForFile(f);
    FileRevisionTimestampComparator c = new FileRevisionTimestampComparator() {
      public boolean isSuitable(long fileTimestamp, long revisionTimestamp) {
        return CvsStatusProvider.timeStampsAreEqual(upToDateTimestamp, fileTimestamp);
      }
    };
    return LocalHistory.getByteContent(myVcs.getProject(), f, c);
  }

  public long getUpToDateTimeForFile(@NotNull VirtualFile vFile) {
    Entry entry = myEntriesManager.getEntryFor(vFile.getParent(), vFile.getName());
    if (entry == null) return -1;
    if (entry.isResultOfMerge()) {
      long resultForMerge = CvsUtil.getUpToDateDateForFile(vFile);
      if (resultForMerge > 0) {
        return resultForMerge;
      }
    }

    Date lastModified = entry.getLastModified();
    if (lastModified == null) return -1;
    return lastModified.getTime();
  }

  private CvsUpToDateRevision createCvsRevision(FilePath path, VcsRevisionNumber revisionNumber, boolean isBinary) {
    if (isBinary) {
      return new CvsUpToDateBinaryRevision(path, revisionNumber);
    }
    return new CvsUpToDateRevision(path, revisionNumber);
  }

  private static boolean isInContent(VirtualFile file) {
    return file == null || !FileTypeManager.getInstance().isFileIgnored(file.getName());
  }

  private static DirectoryContent getDirectoryContent(VirtualFile directory) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Retrieving directory content for " + directory);
    }
    CvsInfo cvsInfo = CvsEntriesManager.getInstance().getCvsInfoFor(directory);
    DirectoryContent result = new DirectoryContent(cvsInfo);

    VirtualFile[] children = CvsVfsUtil.getChildrenOf(directory);
    if (children == null) children = VirtualFile.EMPTY_ARRAY;

    Collection<Entry> entries = cvsInfo.getEntries();

    HashMap<String, VirtualFile> nameToFileMap = new HashMap<String, VirtualFile>();
    for (VirtualFile child : children) {
      nameToFileMap.put(child.getName(), child);
    }

    for (final Entry entry : entries) {
      String fileName = entry.getFileName();
      if (entry.isDirectory()) {
        if (nameToFileMap.containsKey(fileName)) {
          VirtualFile virtualFile = nameToFileMap.get(fileName);
          if (isInContent(virtualFile)) {
            result.addDirectory(new VirtualFileEntry(virtualFile, entry));
          }
        }
        else if (!entry.isRemoved() && !FileTypeManager.getInstance().isFileIgnored(fileName)) {
          result.addDeletedDirectory(entry);
        }
      }
      else {
        if (nameToFileMap.containsKey(fileName) || entry.isRemoved()) {
          VirtualFile virtualFile = nameToFileMap.get(fileName);
          if (isInContent(virtualFile)) {
            result.addFile(new VirtualFileEntry(virtualFile, entry));
          }
        }
        else if (!entry.isAddedFile()) {
          result.addDeletedFile(entry);
        }
      }
      nameToFileMap.remove(fileName);
    }

    for (final String name : nameToFileMap.keySet()) {
      VirtualFile unknown = nameToFileMap.get(name);
      if (unknown.isDirectory()) {
        if (isInContent(unknown)) {
          result.addUnknownDirectory(unknown);
        }
      }
      else {
        if (isInContent(unknown)) {
          boolean isIgnored = result.getCvsInfo().getIgnoreFilter().shouldBeIgnored(unknown.getName());
          if (isIgnored) {
            result.addIgnoredFile(unknown);
          }
          else {
            result.addUnknownFile(unknown);
          }
        }
      }
    }

    return result;
  }

  private class CvsUpToDateRevision implements ContentRevision {
    protected final FilePath myPath;
    private final VcsRevisionNumber myRevisionNumber;
    private String myContent;

    protected CvsUpToDateRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
      myRevisionNumber = revisionNumber;
      myPath = path;
    }

    @Nullable
    public String getContent() throws VcsException {
      if (myContent == null) {
        try {
          byte[] fileBytes = getUpToDateBinaryContent();
          myContent = fileBytes == null ? null : new String(fileBytes, myPath.getCharset().name());
        }
        catch (CannotFindCvsRootException e) {
          myContent = null;
        }
        catch (UnsupportedEncodingException e) {
          myContent = null;
        }
      }
      return myContent;
    }

    @Nullable
    protected byte[] getUpToDateBinaryContent() throws CannotFindCvsRootException {
      VirtualFile virtualFile = myPath.getVirtualFile();
      byte[] result = null;
      if (virtualFile != null) {
        result = getLastUpToDateContentFor(virtualFile);
      }
      if (result == null) {
        final GetFileContentOperation operation;
        if (virtualFile != null) {
          operation = GetFileContentOperation.createForFile(virtualFile, SimpleRevision.createForTheSameVersionOf(virtualFile));
        }
        else {
          operation = GetFileContentOperation.createForFile(myPath);
        }
        if (operation.getRoot().isOffline()) return null;
        CvsVcs2.executeQuietOperation(CvsBundle.message("operation.name.get.file.content"), operation, myVcs.getProject());
        result = operation.tryGetFileBytes();
      }
      return result;
    }

    @NotNull
    public FilePath getFile() {
      return myPath;
    }

    @NotNull
    public VcsRevisionNumber getRevisionNumber() {
      return myRevisionNumber;
    }

    @NonNls
    public String toString() {
      return "CvsUpToDateRevision:" + myPath; 
    }
  }

  private class CvsUpToDateBinaryRevision extends CvsUpToDateRevision implements BinaryContentRevision {
    private byte[] myBinaryContent;

    public CvsUpToDateBinaryRevision(final FilePath path, final VcsRevisionNumber revisionNumber) {
      super(path, revisionNumber);
    }

    @Nullable
    public byte[] getBinaryContent() throws VcsException {
      if (myBinaryContent == null) {
        try {
          myBinaryContent = getUpToDateBinaryContent();
        }
        catch (CannotFindCvsRootException e) {
          throw new VcsException(e);
        }
      }
      return myBinaryContent;
    }

    @NonNls
    public String toString() {
      return "CvsUpToDateBinaryRevision:" + myPath;
    }
  }
}
