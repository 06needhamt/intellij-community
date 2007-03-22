package com.intellij.localvcsintegr;


import com.intellij.localvcs.Entry;
import com.intellij.localvcs.IContentStorage;
import com.intellij.localvcs.Paths;
import com.intellij.openapi.vfs.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

public class FileListeningTest extends IntegrationTestCase {
  public void testCreatingFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    Entry e = getVcs().findEntry(f.getPath());
    assertNotNull(e);
    assertFalse(e.isDirectory());
  }

  public void testCreatingDirectories() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "dir");

    Entry e = getVcs().findEntry(f.getPath());
    assertNotNull(e);
    assertTrue(e.isDirectory());
  }

  public void testIgnoringFilteredFileTypes() throws Exception {
    VirtualFile f = root.createChildData(null, "file.class");
    assertFalse(hasVcsEntry(f));
  }

  public void testIgnoringDirectories() throws Exception {
    VirtualFile f = root.createChildDirectory(null, EXCLUDED_DIR_NAME);
    assertFalse(hasVcsEntry(f));
  }

  public void testChangingFileContent() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");

    f.setBinaryContent(new byte[]{1});
    assertEquals(1, getVcsContentOf(f)[0]);

    f.setBinaryContent(new byte[]{2});
    assertEquals(2, getVcsContentOf(f)[0]);
  }

  public void testChangingFileContentOnlyAfterContentChangedEvent() throws Exception {
    final VirtualFile f = root.createChildData(null, "file.java");
    f.setBinaryContent("before".getBytes());

    ContentChangesListener l = new ContentChangesListener(f);
    addFileListenerDuring(l, new Callable() {
      public Object call() throws Exception {
        f.setBinaryContent("after".getBytes());
        return null;
      }
    });

    assertEquals("before", l.getContentBefore());
    assertEquals("after", l.getContentAfter());
  }

  public void testRenamingFile() throws Exception {
    VirtualFile f = root.createChildData(null, "file.java");
    f.rename(null, "file2.java");

    assertFalse(hasVcsEntry(Paths.renamed(f.getPath(), "file.java")));
    assertTrue(hasVcsEntry(f));
  }

  public void testRenamingFileOnlyAfterRenamedEvent() throws Exception {
    final VirtualFile f = root.createChildData(null, "old.java");
    final boolean[] log = new boolean[4];
    final String oldPath = Paths.appended(root.getPath(), "old.java");
    final String newPath = Paths.appended(root.getPath(), "new.java");

    VirtualFileListener l = new VirtualFileAdapter() {
      public void beforePropertyChange(VirtualFilePropertyEvent e) {
        log[0] = hasVcsEntry(oldPath);
        log[1] = hasVcsEntry(newPath);
      }

      public void propertyChanged(VirtualFilePropertyEvent e) {
        log[2] = hasVcsEntry(oldPath);
        log[3] = hasVcsEntry(newPath);
      }
    };

    addFileListenerDuring(l, new Callable() {
      public Object call() throws Exception {
        f.rename(null, "new.java");
        return null;
      }
    });

    assertEquals(true, log[0]);
    assertEquals(false, log[1]);
    assertEquals(false, log[2]);
    assertEquals(true, log[3]);
  }

  public void testRenamingFilteredFiles() throws Exception {
    VirtualFile f = root.createChildData(null, "file.class");
    assertFalse(hasVcsEntry(f));
    f.rename(null, "file.java");
    assertTrue(hasVcsEntry(f));
  }

  public void testRenamingFilteredDirectoriesToNonFiltered() throws Exception {
    VirtualFile f = root.createChildDirectory(null, EXCLUDED_DIR_NAME);

    String filtered = Paths.appended(root.getPath(), EXCLUDED_DIR_NAME);
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertFalse(hasVcsEntry(filtered));
    f.rename(null, "not_filtered");

    assertFalse(hasVcsEntry(filtered));
    assertTrue(hasVcsEntry(notFiltered));
  }

  public void testRenamingNonFilteredDirectoriesToFiltered() throws Exception {
    VirtualFile f = root.createChildDirectory(null, "not_filtered");

    String filtered = Paths.appended(root.getPath(), EXCLUDED_DIR_NAME);
    String notFiltered = Paths.appended(root.getPath(), "not_filtered");

    assertTrue(hasVcsEntry(notFiltered));
    f.rename(null, EXCLUDED_DIR_NAME);

    assertFalse(hasVcsEntry(notFiltered));
    assertFalse(hasVcsEntry(filtered));
  }

  public void testDeletionOfFilteredDirectoryDoesNotThrowsException() throws Exception {
    VirtualFile f = root.createChildDirectory(null, EXCLUDED_DIR_NAME);

    String filtered = Paths.appended(root.getPath(), EXCLUDED_DIR_NAME);

    assertFalse(hasVcsEntry(filtered));
    f.delete(null);

    assertFalse(hasVcsEntry(filtered));
  }

  public void testDeletingBigFiles() throws Exception {
    File tempDir = createTempDirectory();
    File tempFile = new File(tempDir, "bigFile.java");
    OutputStream s = new FileOutputStream(tempFile);
    s.write(new byte[IContentStorage.MAX_CONTENT_LENGTH + 1]);
    s.close();

    VirtualFile f = LocalFileSystem.getInstance().findFileByIoFile(tempFile);

    f.move(null, root);
    assertTrue(hasVcsEntry(f));

    f.delete(null);
    assertFalse(hasVcsEntry(f));
  }
}