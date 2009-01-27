package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.StreamProvider;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.UniqueFileNamesProvider;
import com.intellij.util.io.fs.FileSystem;
import com.intellij.util.io.fs.IFile;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

/**
 * @author mike
 */
public class StorageUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.components.impl.stores.StorageUtil");
  private static boolean ourDumpChangedComponentStates = "true".equals(System.getProperty("log.externally.changed.component.states"));

  private StorageUtil() {
  }

  static void save(final IFile file, final byte[] text) throws StateStorage.StateStorageException {
    final String filePath = file.getCanonicalPath();
    try {
      final Ref<IOException> refIOException = Ref.create(null);

      if (file.exists()) {
        final byte[] bytes = file.loadBytes();
        if (Arrays.equals(bytes, text)) return;
        IFile backupFile = deleteBackup(filePath);
        file.renameTo(backupFile);
      }

      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          if (!file.exists()) {
            file.createParentDirs();
          }

          try {
            getOrCreateVirtualFile(file, file).setBinaryContent(text);
          }
          catch (IOException e) {
            refIOException.set(e);
          }

          deleteBackup(filePath);
        }
      });
      if (refIOException.get() != null) {
        throw new StateStorage.StateStorageException(refIOException.get());
      }
    }
    catch (StateStorage.StateStorageException e) {
      throw new StateStorage.StateStorageException(e);
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  static IFile deleteBackup(final String path) {
    IFile backupFile = FileSystem.FILE_SYSTEM.createFile(path + "~");
    if (backupFile.exists()) {
      backupFile.delete();
    }
    return backupFile;
  }

  static VirtualFile getOrCreateVirtualFile(Object requestor, IFile ioFile) throws IOException {
    VirtualFile vFile = getVirtualFile(ioFile);

    if (vFile == null) {
      vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    }

    if (vFile == null) {
      final IFile parentFile = ioFile.getParentFile();
      final VirtualFile parentVFile =
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentFile); // need refresh if the directory has just been created
      if (parentVFile == null) {
        throw new IOException(ProjectBundle.message("project.configuration.save.file.not.found", parentFile.getPath()));
      }
      vFile = parentVFile.createChildData(requestor, ioFile.getName());
    }

    return vFile;
  }

  @Nullable
  static VirtualFile getVirtualFile(final IFile ioFile) {
    return LocalFileSystem.getInstance().findFileByIoFile(ioFile);
  }

  public static byte[] printDocument(final Document document) throws StateStorage.StateStorageException {
    try {
      return JDOMUtil.writeDocument(document, SystemProperties.getLineSeparator()).getBytes(CharsetToolkit.UTF8);
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  static byte[] printElement(Element element) throws StateStorage.StateStorageException {
    try {
      return JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()).getBytes(CharsetToolkit.UTF8);
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }

  static void save(IFile file, Element element) throws StateStorage.StateStorageException {
    try {
      save(file, JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()).getBytes(CharsetToolkit.UTF8));
    }
    catch (IOException e) {
      throw new StateStorage.StateStorageException(e);
    }
  }


  @Nullable
  public static Document loadDocument(final byte[] bytes) {
    try {
      return (bytes == null || bytes.length == 0) ? null : JDOMUtil.loadDocument(new ByteArrayInputStream(bytes));
    }
    catch (JDOMException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
  }

  @Nullable
  public static Document loadDocument(final InputStream stream) {
    if (stream == null) return null;

    try {
      return JDOMUtil.loadDocument(stream);
    }
    catch (JDOMException e) {
      return null;
    }
    catch (IOException e) {
      return null;
    }
    finally {
      try {
        stream.close();
      }
      catch (IOException e) {
        //ignore
      }
    }
  }

  public static void sendContent(final StreamProvider streamProvider, final String fileSpec, final Document copy, final RoamingType roamingType, boolean async)
      throws IOException {
    byte[] content = printDocument(copy);
    ByteArrayInputStream in = new ByteArrayInputStream(content);
    try {
      if (streamProvider.isEnabled()) {
        streamProvider.saveContent(fileSpec, in, content.length, roamingType, async);
      }
    }
    finally {
      in.close();
    }

  }

  public static void logStateDiffInfo(Set<Pair<VirtualFile, StateStorage>> changedFiles, Set<String> componentNames) throws IOException {

    if (!ApplicationManagerEx.getApplicationEx().isInternal() && !ourDumpChangedComponentStates) return;

    try {
      File logDirectory = createLogDirectory();

      logDirectory.mkdirs();

      for (String componentName : componentNames) {
        for (Pair<VirtualFile, StateStorage> pair : changedFiles) {
          StateStorage storage = pair.second;
          if ((storage instanceof XmlElementStorage)) {
            Element state = ((XmlElementStorage)storage).getState(componentName);
            if (state != null) {
              File logFile = new File(logDirectory, "prev_" + componentName + ".xml");
              FileUtil.writeToFile(logFile, JDOMUtil.writeElement(state, "\n").getBytes());
            }
          }
        }

      }

      for (Pair<VirtualFile, StateStorage> changedFile : changedFiles) {
        File logFile = new File(logDirectory, "new_" + changedFile.first.getName());

        FileUtil.copy(new File(changedFile.first.getPath()), logFile);
      }
    }
    catch (Throwable e) {
      LOG.info(e);
    }
  }

  static File createLogDirectory() {
    UniqueFileNamesProvider namesProvider = new UniqueFileNamesProvider();

    File statesDir = new File(PathManager.getSystemPath(), "log/componentStates");
    File[] children = statesDir.listFiles();
    if (children != null) {
      if (children.length > 10) {
        File childToDelete = null;

        for (File child : children) {
          if (childToDelete == null || childToDelete.lastModified() > child.lastModified()) {
            childToDelete = child;
          }
        }

        if (childToDelete != null) {
          FileUtil.delete(childToDelete);
        }
      }


      for (File child : children) {
        namesProvider.reserveFileName(child.getName());
      }
    }

    return new File(statesDir, namesProvider.suggestName("state-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())
                        + "-" + ApplicationInfo.getInstance().getBuildNumber()));
  }
}
