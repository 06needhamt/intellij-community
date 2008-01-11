package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.watcher.OrderEntryProperties;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author dsl
 */
public class RootModelImpl implements ModifiableRootModel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.RootModelImpl");

  private TreeSet<ContentEntry> myContent = new TreeSet<ContentEntry>(ContentComparator.INSTANCE);

  private List<OrderEntry> myOrderEntries = new Order();
  // cleared by myOrderEntries modification, see Order
  private OrderEntry[] myCachedOrderEntries;

  private final ModuleLibraryTable myModuleLibraryTable;
  final ModuleRootManagerImpl myModuleRootManager;
  private boolean myWritable;
  private VirtualFilePointerListener myVirtualFilePointerListener;
  private VirtualFilePointerManager myFilePointerManager;
  VirtualFilePointer myExplodedDirectoryPointer;


  private String myExplodedDirectory;

  ArrayList<RootModelComponentBase> myComponents = new ArrayList<RootModelComponentBase>();
  private List<VirtualFilePointer> myPointersToDispose = new ArrayList<VirtualFilePointer>();


  private boolean myExcludeExploded;



  @NonNls private static final String EXPLODED_TAG = "exploded";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String URL_ATTR = ATTRIBUTE_URL;

  @NonNls private static final String EXCLUDE_EXPLODED_TAG = "exclude-exploded";

  private boolean myDisposed = false;
  private final OrderEntryProperties myOrderEntryProperties;

  private final Set<ModuleExtension> myExtensions = new HashSet<ModuleExtension>();

  private final Map<OrderRootType, VirtualFilePointerContainer> myOrderRootPointerContainers = new HashMap<OrderRootType, VirtualFilePointerContainer>();

  private VirtualFilePointerFactory myVirtualFilePointerFactory = new VirtualFilePointerFactory() {
    public VirtualFilePointer create(VirtualFile file) {
      final VirtualFilePointer pointer = myFilePointerManager.create(file, getFileListener());
      annotatePointer(pointer);
      return pointer;
    }

    public VirtualFilePointer create(String url) {
      final VirtualFilePointer pointer = myFilePointerManager.create(url, getFileListener());
      annotatePointer(pointer);
      return pointer;
    }

    public VirtualFilePointer duplicate(VirtualFilePointer virtualFilePointer) {
      final VirtualFilePointer pointer = myFilePointerManager.duplicate(virtualFilePointer, getFileListener());
      annotatePointer(pointer);
      return pointer;
    }
  };
  @NonNls private static final String PROPERTIES_CHILD_NAME = "orderEntryProperties";
  @NonNls private static final String ROOT_ELEMENT = "root";
  private ProjectRootManagerImpl myProjectRootManager;

  RootModelImpl(ModuleRootManagerImpl moduleRootManager,
                ProjectRootManagerImpl projectRootManager,
                VirtualFilePointerManager filePointerManager,
                final VirtualFilePointerListener listener) {
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;

    myWritable = false;
    myVirtualFilePointerListener = listener;
    addSourceOrderEntries();
    myOrderEntryProperties = new OrderEntryProperties();
    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager, myFilePointerManager);

    for (ModuleExtension extension : Extensions.getExtensions(ModuleExtension.EP_NAME, moduleRootManager.getModule())) {
      myExtensions.add(extension.getModifiableModel(false));
    }
  }

  private void addSourceOrderEntries() {
    myOrderEntries.add(new ModuleSourceOrderEntryImpl(this));
  }

  RootModelImpl(Element element,
                ModuleRootManagerImpl moduleRootManager,
                ProjectRootManagerImpl projectRootManager,
                VirtualFilePointerManager filePointerManager) throws InvalidDataException {
    myProjectRootManager = projectRootManager;
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager, myFilePointerManager);

    myVirtualFilePointerListener = null;
    final List contentChildren = element.getChildren(ContentEntryImpl.ELEMENT_NAME);
    for (Object aContentChildren : contentChildren) {
      Element child = (Element)aContentChildren;
      ContentEntryImpl contentEntry = new ContentEntryImpl(child, this);
      myContent.add(contentEntry);
    }

    final List orderElements = element.getChildren(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME);
    boolean moduleSourceAdded = false;
    for (Object orderElement : orderElements) {
      Element child = (Element)orderElement;
      final OrderEntry orderEntry = OrderEntryFactory.createOrderEntryByElement(child, this, myProjectRootManager, myFilePointerManager);
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        if (moduleSourceAdded) continue;
        moduleSourceAdded = true;
      }
      myOrderEntries.add(orderEntry);
    }

    if (!moduleSourceAdded) {
      myOrderEntries.add(new ModuleSourceOrderEntryImpl(this));
    }


    myExcludeExploded = element.getChild(EXCLUDE_EXPLODED_TAG) != null;


    myExplodedDirectoryPointer = getOutputPathValue(element, EXPLODED_TAG, true);
    myExplodedDirectory = getOutputPathValue(element, EXCLUDE_EXPLODED_TAG);

    myWritable = false;
    myOrderEntryProperties = new OrderEntryProperties();
    final Element propertiesChild = element.getChild(PROPERTIES_CHILD_NAME);
    if (propertiesChild != null) {
      myOrderEntryProperties.readExternal(propertiesChild);
    }

    for(OrderRootType orderRootType: OrderRootType.getAllTypes()) {
      String paths = orderRootType.getModulePathsName();
      if (paths != null) {
        final Element pathsElement = element.getChild(paths);
        if (pathsElement != null) {
          VirtualFilePointerContainer container = myFilePointerManager.createContainer(myVirtualFilePointerFactory);
          myOrderRootPointerContainers.put(orderRootType, container);
          container.readExternal(pathsElement, ROOT_ELEMENT);
        }
      }
    }

    for (ModuleExtension extension : Extensions.getExtensions(ModuleExtension.EP_NAME, moduleRootManager.getModule())) {
      extension.readExternal(element);
      myExtensions.add(extension.getModifiableModel(false));
    }
  }

  public boolean isWritable() {
    return myWritable;
  }

  RootModelImpl(RootModelImpl rootModel,
                ModuleRootManagerImpl moduleRootManager,
                final boolean writable,
                final VirtualFilePointerListener virtualFilePointerListener,
                VirtualFilePointerManager filePointerManager,
                ProjectRootManagerImpl projectRootManager) {
    myFilePointerManager = filePointerManager;
    myModuleRootManager = moduleRootManager;
    myProjectRootManager = projectRootManager;

    myModuleLibraryTable = new ModuleLibraryTable(this, myProjectRootManager, myFilePointerManager);

    myWritable = writable;
    LOG.assertTrue(!writable || virtualFilePointerListener == null);
    myVirtualFilePointerListener = virtualFilePointerListener;

    if (rootModel.myExplodedDirectoryPointer != null) {
      myExplodedDirectoryPointer = pointerFactory().duplicate(rootModel.myExplodedDirectoryPointer);
    }
    myExplodedDirectory = rootModel.myExplodedDirectory;

    myExcludeExploded = rootModel.myExcludeExploded;

    final TreeSet<ContentEntry> thatContent = rootModel.myContent;
    for (ContentEntry contentEntry : thatContent) {
      if (contentEntry instanceof ClonableContentEntry) {
        myContent.add(((ClonableContentEntry)contentEntry).cloneEntry(this));
      }
    }

    final List<OrderEntry> order = rootModel.myOrderEntries;
    for (OrderEntry orderEntry : order) {
      if (orderEntry instanceof ClonableOrderEntry) {
        myOrderEntries.add(((ClonableOrderEntry)orderEntry).cloneEntry(this, myProjectRootManager, myFilePointerManager));
      }
    }
    myOrderEntryProperties = rootModel.myOrderEntryProperties.copy(this);
    for(OrderRootType orderRootType: OrderRootType.getAllTypes()) {
      final VirtualFilePointerContainer otherContainer = rootModel.getOrderRootContainer(orderRootType);
      if (otherContainer != null) {
        VirtualFilePointerContainer container = myFilePointerManager.createContainer(myVirtualFilePointerFactory);
        container.addAll(otherContainer);
        myOrderRootPointerContainers.put(orderRootType, container);
      }
    }

   

    for (ModuleExtension extension : rootModel.myExtensions) {
      myExtensions.add(writable ? extension.getModifiableModel(writable) : extension);
    }
  }

  @Nullable
  VirtualFilePointerContainer getOrderRootContainer(OrderRootType orderRootType) {
    return myOrderRootPointerContainers.get(orderRootType);
  }

  @NotNull
  public VirtualFile[] getOrderedRoots(OrderRootType type) {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

    for (OrderEntry orderEntry : getOrderEntries()) {
      result.addAll(Arrays.asList(orderEntry.getFiles(type)));
    }
    return ContainerUtil.toArray(result, new VirtualFile[result.size()]);
  }

  @NotNull
  public String[] getOrderedRootUrls(OrderRootType type) {
    final ArrayList<String> result = new ArrayList<String>();

    for (OrderEntry orderEntry : getOrderEntries()) {
      result.addAll(Arrays.asList(orderEntry.getUrls(type)));
    }
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @NotNull
  public VirtualFile[] getContentRoots() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();

    for (ContentEntry contentEntry : myContent) {
      final VirtualFile file = contentEntry.getFile();
      if (file != null) {
        result.add(file);
      }
    }
    return ContainerUtil.toArray(result, new VirtualFile[result.size()]);
  }

  @NotNull
  public String[] getContentRootUrls() {
    final ArrayList<String> result = new ArrayList<String>();

    for (ContentEntry contentEntry : myContent) {
      result.add(contentEntry.getUrl());
    }
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @NotNull
  public String[] getExcludeRootUrls() {
    final ArrayList<String> result = new ArrayList<String>();
    for (ContentEntry contentEntry : myContent) {
      final ExcludeFolder[] excludeFolders = contentEntry.getExcludeFolders();
      for (ExcludeFolder excludeFolder : excludeFolders) {
        result.add(excludeFolder.getUrl());
      }
    }
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @NotNull
  public VirtualFile[] getExcludeRoots() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (ContentEntry contentEntry : myContent) {
      final ExcludeFolder[] excludeFolders = contentEntry.getExcludeFolders();
      for (ExcludeFolder excludeFolder : excludeFolders) {
        final VirtualFile file = excludeFolder.getFile();
        if (file != null) {
          result.add(file);
        }
      }
    }
    return ContainerUtil.toArray(result, new VirtualFile[result.size()]);
  }


  @NotNull
  public String[] getSourceRootUrls() {
    final ArrayList<String> result = new ArrayList<String>();
    for (ContentEntry contentEntry : myContent) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        result.add(sourceFolder.getUrl());
      }
    }
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  private String[] getSourceRootUrls(boolean testFlagValue) {
    final ArrayList<String> result = new ArrayList<String>();
    for (ContentEntry contentEntry : myContent) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        if (sourceFolder.isTestSource() == testFlagValue) {
          result.add(sourceFolder.getUrl());
        }
      }
    }
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @NotNull
  public VirtualFile[] getSourceRoots() {
    final ArrayList<VirtualFile> result = new ArrayList<VirtualFile>();
    for (ContentEntry contentEntry : myContent) {
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      for (SourceFolder sourceFolder : sourceFolders) {
        final VirtualFile file = sourceFolder.getFile();
        if (file != null) {
          result.add(file);
        }
      }
    }
    return ContainerUtil.toArray(result, new VirtualFile[result.size()]);
  }

  public ContentEntry[] getContentEntries() {
    return myContent.toArray(new ContentEntry[myContent.size()]);
  }

  @NotNull
  public OrderEntry[] getOrderEntries() {
    OrderEntry[] cachedOrderEntries = myCachedOrderEntries;
    if (cachedOrderEntries == null) {
      myCachedOrderEntries = cachedOrderEntries = myOrderEntries.toArray(new OrderEntry[myOrderEntries.size()]);
    }
    return cachedOrderEntries;
  }

  Iterator<OrderEntry> getOrderIterator() {
    return Collections.unmodifiableList(myOrderEntries).iterator();
  }

  public void removeContentEntry(ContentEntry entry) {
    assertWritable();
    LOG.assertTrue(myContent.contains(entry));
    myContent.remove(entry);
    if (myComponents.contains(entry)) {
      ((RootModelComponentBase)entry).dispose();
    }
  }

  public void addOrderEntry(OrderEntry entry) {
    assertWritable();
    LOG.assertTrue(!myOrderEntries.contains(entry));
    myOrderEntries.add(entry);
  }

  public LibraryOrderEntry addLibraryEntry(Library library) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(library, this, myProjectRootManager, myFilePointerManager);
    myOrderEntries.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  public LibraryOrderEntry addInvalidLibrary(String name, String level) {
    assertWritable();
    final LibraryOrderEntry libraryOrderEntry = new LibraryOrderEntryImpl(name, level, this, myProjectRootManager, myFilePointerManager);
    myOrderEntries.add(libraryOrderEntry);
    return libraryOrderEntry;
  }

  public ModuleOrderEntry addModuleOrderEntry(Module module) {
    assertWritable();
    LOG.assertTrue(!module.equals(getModule()));
    LOG.assertTrue(Comparing.equal(myModuleRootManager.getModule().getProject(), module.getProject()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(module, this);
    myOrderEntries.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  public ModuleOrderEntry addInvalidModuleEntry(String name) {
    assertWritable();
    LOG.assertTrue(!name.equals(getModule().getName()));
    final ModuleOrderEntryImpl moduleOrderEntry = new ModuleOrderEntryImpl(name, this);
    myOrderEntries.add(moduleOrderEntry);
    return moduleOrderEntry;
  }

  public LibraryOrderEntry findLibraryOrderEntry(Library library) {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry && library.equals(((LibraryOrderEntry)orderEntry).getLibrary())) {
        return (LibraryOrderEntry)orderEntry;
      }
    }
    return null;
  }

  public void removeOrderEntry(OrderEntry entry) {
    assertWritable();
    removeOrderEntryInternal(entry);
  }

  private void removeOrderEntryInternal(OrderEntry entry) {
    LOG.assertTrue(myOrderEntries.contains(entry));
    myOrderEntries.remove(entry);
  }

  public void rearrangeOrderEntries(OrderEntry[] newEntries) {
    assertWritable();
    assertValidRearrangement(newEntries);
    myOrderEntries.clear();
    for (OrderEntry newEntry : newEntries) {
      myOrderEntries.add(newEntry);
    }
  }

  private void assertValidRearrangement(OrderEntry[] newEntries) {
    LOG.assertTrue(newEntries.length == myOrderEntries.size(), "Invalid rearranged order");
    Set<OrderEntry> set = new HashSet<OrderEntry>();
    for (OrderEntry newEntry : newEntries) {
      LOG.assertTrue(myOrderEntries.contains(newEntry), "Invalid rearranged order");
      LOG.assertTrue(!set.contains(newEntry), "Invalid rearranged order");
      set.add(newEntry);
    }
  }

  public void clear() {
    final Sdk jdk = getJdk();
    myContent.clear();
    myOrderEntries.clear();
    setJdk(jdk);
    addSourceOrderEntries();
  }

  public void commit() {
    for (ModuleExtension extension : myExtensions) {
      extension.commit();
    }
    myModuleRootManager.commitModel(this);
    myWritable = false;
  }

  @NotNull
  public LibraryTable getModuleLibraryTable() {
    return myModuleLibraryTable;
  }

  public <R> R processOrder(RootPolicy<R> policy, R initialValue) {
    R result = initialValue;
    for (OrderEntry orderEntry : getOrderEntries()) {
      result = orderEntry.accept(policy, result);
    }
    return result;
  }

  public Project getProject() {
    return myProjectRootManager.getProject();
  }

  @NotNull
  public ContentEntry addContentEntry(VirtualFile file) {
    return addContentEntry(new ContentEntryImpl(file, this));
  }

  @NotNull
  public ContentEntry addContentEntry(String url) {
    return addContentEntry(new ContentEntryImpl(url, this));
  }

  private ContentEntry addContentEntry(ContentEntry e) {
    myContent.add(e);
    return e;
  }



  public void writeExternal(Element element) throws WriteExternalException {
    for (ModuleExtension extension : myExtensions) {
      extension.writeExternal(element);
    }




    if (myExplodedDirectory != null) {
      final Element pathElement = new Element(EXPLODED_TAG);
      pathElement.setAttribute(URL_ATTR, myExplodedDirectory);
      element.addContent(pathElement);
    }

    if (myExcludeExploded) {
      element.addContent(new Element(EXCLUDE_EXPLODED_TAG));
    }



    for (ContentEntry contentEntry : myContent) {
      if (contentEntry instanceof ContentEntryImpl) {
        final Element subElement = new Element(ContentEntryImpl.ELEMENT_NAME);
        ((ContentEntryImpl)contentEntry).writeExternal(subElement);
        element.addContent(subElement);
      }
    }

    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof WritableOrderEntry) {
        ((WritableOrderEntry)orderEntry).writeExternal(element);
      }
    }

    final Element propertiesChild = new Element(PROPERTIES_CHILD_NAME);
    myOrderEntryProperties.writeExternal(propertiesChild, this);
    element.addContent(propertiesChild);

    for(OrderRootType orderRootType: myOrderRootPointerContainers.keySet()) {
      VirtualFilePointerContainer container = myOrderRootPointerContainers.get(orderRootType);
      if (container != null && container.size() > 0) {
        final Element javaDocPaths = new Element(orderRootType.getModulePathsName());
        container.writeExternal(javaDocPaths, ROOT_ELEMENT);
        element.addContent(javaDocPaths);
      }
    }
  }

  public void setJdk(Sdk jdk) {
    assertWritable();
    final JdkOrderEntry jdkLibraryEntry;
    if (jdk != null) {
      jdkLibraryEntry = new ModuleJdkOrderEntryImpl(jdk, this, myProjectRootManager, myFilePointerManager);
    }
    else {
      jdkLibraryEntry = null;
    }
    replaceEntryOfType(JdkOrderEntry.class, jdkLibraryEntry);

  }

  public <T extends OrderEntry> void replaceEntryOfType(Class<T> entryClass, final T entry) {
    assertWritable();
    for (int i = 0; i < myOrderEntries.size(); i++) {
      OrderEntry orderEntry = myOrderEntries.get(i);
      if (entryClass.isInstance(orderEntry)) {
        myOrderEntries.remove(i);
        if (entry != null) {
          myOrderEntries.add(i, entry);
        }
        return;
      }
    }

    if (entry != null) {
      myOrderEntries.add(0, entry);
    }
  }

  public Sdk getJdk() {
    for (OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof JdkOrderEntry) {
        return ((JdkOrderEntry)orderEntry).getJdk();
      }
    }
    return null;
  }

  public void assertWritable() {
    LOG.assertTrue(myWritable);
  }

  public boolean isDependsOn(final Module module) {
    for (OrderEntry entry : getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final Module module1 = ((ModuleOrderEntry)entry).getModule();
        if (module1 == module) {
          return true;
        }
      }
    }
    return false;
  }

  private static class ContentComparator implements Comparator<ContentEntry> {
    public static final ContentComparator INSTANCE = new ContentComparator();

    public int compare(final ContentEntry o1, final ContentEntry o2) {
      return o1.getUrl().compareTo(o2.getUrl());
    }
  }


  public VirtualFile getExplodedDirectory() {
    if (myExplodedDirectoryPointer == null) {
      return null;
    }
    else {
      return myExplodedDirectoryPointer.getFile();
    }
  }



  public void setExplodedDirectory(VirtualFile file) {
    assertWritable();
    if (file != null) {
      myExplodedDirectoryPointer = pointerFactory().create(file);
      myExplodedDirectory = file.getUrl();
    }
    else {
      myExplodedDirectoryPointer = null;
    }
  }

  public void setExplodedDirectory(String url) {
    assertWritable();
    if (url != null) {
      myExplodedDirectoryPointer = pointerFactory().create(url);
      myExplodedDirectory = url;
    }
    else {
      myExplodedDirectoryPointer = null;
    }
  }

  @NotNull
  public Module getModule() {
    return myModuleRootManager.getModule();
  }

  private VirtualFilePointerListener getFileListener() {
    return myVirtualFilePointerListener;
  }



  public String getExplodedDirectoryUrl() {
    if (myExplodedDirectoryPointer == null) {
      return null;
    }
    else {
      return myExplodedDirectoryPointer.getUrl();
    }
  }

  private static boolean vptrEqual(VirtualFilePointer p1, VirtualFilePointer p2) {
    if (p1 == null && p2 == null) return true;
    if (p1 == null || p2 == null) return false;
    return Comparing.equal(p1.getUrl(), p2.getUrl());
  }


  public boolean isChanged() {
    if (!myWritable) return false;
//    if (myJdkChanged) return true;

    if (!vptrEqual(myExplodedDirectoryPointer, getSourceModel().myExplodedDirectoryPointer)) {
      return true;
    }

    for (ModuleExtension moduleExtension : myExtensions) {
      if (moduleExtension.isChanged()) return true;
    }

    if (myExcludeExploded != getSourceModel().myExcludeExploded) return true;

    OrderEntry[] orderEntries = getOrderEntries();
    OrderEntry[] sourceOrderEntries = getSourceModel().getOrderEntries();
    if (orderEntries.length != sourceOrderEntries.length) return true;
    for (int i = 0; i < orderEntries.length; i++) {
      OrderEntry orderEntry = orderEntries[i];
      OrderEntry sourceOrderEntry = sourceOrderEntries[i];
      if (!orderEntriesEquals(orderEntry, sourceOrderEntry)) {
        return true;
      }
    }

    final String[] contentRootUrls = getContentRootUrls();
    final String[] thatContentRootUrls = getSourceModel().getContentRootUrls();
    if (!Arrays.equals(contentRootUrls, thatContentRootUrls)) return true;

    final String[] excludeRootUrls = getExcludeRootUrls();
    final String[] thatExcludeRootUrls = getSourceModel().getExcludeRootUrls();
    if (!Arrays.equals(excludeRootUrls, thatExcludeRootUrls)) return true;

    final String[] sourceRootForMainUrls = getSourceRootUrls(false);
    final String[] thatSourceRootForMainUrls = getSourceModel().getSourceRootUrls(false);
    if (!Arrays.equals(sourceRootForMainUrls, thatSourceRootForMainUrls)) return true;

    final String[] sourceRootForTestUrls = getSourceRootUrls(true);
    final String[] thatSourceRootForTestUrls = getSourceModel().getSourceRootUrls(true);
    if (!Arrays.equals(sourceRootForTestUrls, thatSourceRootForTestUrls)) return true;

    final ContentEntry[] contentEntries = getContentEntries();
    final ContentEntry[] thatContentEntries = getSourceModel().getContentEntries();
    if (contentEntries.length != thatContentEntries.length) return true;
    for (int i = 0; i < contentEntries.length; i++) {
      final ContentEntry contentEntry = contentEntries[i];
      final ContentEntry thatContentEntry = thatContentEntries[i];
      final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
      final SourceFolder[] thatSourceFolders = thatContentEntry.getSourceFolders();
      if (sourceFolders.length != thatSourceFolders.length) return true;
      for (int j = 0; j < sourceFolders.length; j++) {
        final SourceFolder sourceFolder = sourceFolders[j];
        final SourceFolder thatSourceFolder = thatSourceFolders[j];
        if (!sourceFolder.getUrl().equals(thatSourceFolder.getUrl())
            || !sourceFolder.getPackagePrefix().equals(thatSourceFolder.getPackagePrefix())) {
          return true;
        }
      }
    }
    return !myOrderRootPointerContainers.equals(getSourceModel().myOrderRootPointerContainers);
  }

  void addExportedUrs(OrderRootType type, List<String> result, Set<Module> processed) {
    for (final OrderEntry orderEntry : getOrderEntries()) {
      if (orderEntry instanceof ModuleSourceOrderEntryImpl) {
        ((ModuleSourceOrderEntryImpl)orderEntry).addExportedUrls(type, result);
      }
      else if (orderEntry instanceof ExportableOrderEntry && ((ExportableOrderEntry)orderEntry).isExported()) {
        if (orderEntry instanceof ModuleOrderEntryImpl) {
          result.addAll(Arrays.asList(((ModuleOrderEntryImpl)orderEntry).getUrls(type, processed)));
        }
        else {
          result.addAll(Arrays.asList(orderEntry.getUrls(type)));
        }
      }
    }
  }

  private static boolean orderEntriesEquals(OrderEntry orderEntry1, OrderEntry orderEntry2) {
    if (!((OrderEntryBaseImpl)orderEntry1).sameType(orderEntry2)) return false;
    if (orderEntry1 instanceof JdkOrderEntry) {
      if (!(orderEntry2 instanceof JdkOrderEntry)) return false;
      if (orderEntry1 instanceof InheritedJdkOrderEntry && orderEntry2 instanceof ModuleJdkOrderEntry) {
        return false;
      }
      if (orderEntry2 instanceof InheritedJdkOrderEntry && orderEntry1 instanceof ModuleJdkOrderEntry) {
        return false;
      }
      if (orderEntry1 instanceof ModuleJdkOrderEntry && orderEntry2 instanceof ModuleJdkOrderEntry) {
        String name1 = ((ModuleJdkOrderEntry)orderEntry1).getJdkName();
        String name2 = ((ModuleJdkOrderEntry)orderEntry2).getJdkName();
        if (!Comparing.strEqual(name1, name2)) {
          return false;
        }
      }
    }
    if (orderEntry1 instanceof ExportableOrderEntry) {
      if (!(((ExportableOrderEntry)orderEntry1).isExported() == ((ExportableOrderEntry)orderEntry2).isExported())) {
        return false;
      }
    }
    if (orderEntry1 instanceof ModuleOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof ModuleOrderEntryImpl);
      final String name1 = ((ModuleOrderEntryImpl)orderEntry1).getModuleName();
      final String name2 = ((ModuleOrderEntryImpl)orderEntry2).getModuleName();
      return Comparing.equal(name1, name2);
    }

    if (orderEntry1 instanceof LibraryOrderEntry) {
      LOG.assertTrue(orderEntry2 instanceof LibraryOrderEntry);
      LibraryOrderEntry libraryOrderEntry1 = (LibraryOrderEntry)orderEntry1;
      LibraryOrderEntry libraryOrderEntry2 = (LibraryOrderEntry)orderEntry2;
      boolean equal = Comparing.equal(libraryOrderEntry1.getLibraryName(), libraryOrderEntry2.getLibraryName())
                      && Comparing.equal(libraryOrderEntry1.getLibraryLevel(), libraryOrderEntry2.getLibraryLevel());
      if (!equal) return false;
    }

    final OrderRootType[] allTypes = OrderRootType.getAllTypes();
    for (OrderRootType type : allTypes) {
      final String[] orderedRootUrls1 = orderEntry1.getUrls(type);
      final String[] orderedRootUrls2 = orderEntry2.getUrls(type);
      if (!Arrays.equals(orderedRootUrls1, orderedRootUrls2)) {
        return false;
      }
    }
    return true;
  }

  void fireBeforeExternalChange() {
    if (myWritable || myDisposed) return;
    myModuleRootManager.fireBeforeRootsChange();
  }

  void fireAfterExternalChange() {
    if (myWritable || myDisposed) return;
    myModuleRootManager.fireRootsChanged();
  }

  public void dispose() {
    assertWritable();
    for (ModuleExtension extension : myExtensions) {
      Disposer.dispose(extension);
    }
    disposeModel();
    myWritable = false;
    myExtensions.clear();
  }

  void disposeModel() {
    final RootModelComponentBase[] rootModelComponentBases = myComponents.toArray(
      new RootModelComponentBase[myComponents.size()]);
    for (RootModelComponentBase rootModelComponentBase : rootModelComponentBases) {
      rootModelComponentBase.dispose();
    }

    for (VirtualFilePointer pointer : myPointersToDispose) {
      myFilePointerManager.kill(pointer, myVirtualFilePointerListener);
    }
    myPointersToDispose.clear();
    myVirtualFilePointerListener = null;
    myDisposed = true;
  }

  public boolean isExcludeExplodedDirectory() { return myExcludeExploded; }

  public void setExcludeExplodedDirectory(boolean excludeExplodedDir) { myExcludeExploded = excludeExplodedDir; }

  private void annotatePointer(VirtualFilePointer pointer) {
    myPointersToDispose.add(pointer);
  }

  private class Order extends ArrayList<OrderEntry> {
    public void clear() {
      super.clear();
      clearCachedEntries();
    }

    public OrderEntry set(int i, OrderEntry orderEntry) {
      super.set(i, orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(i);
      clearCachedEntries();
      return orderEntry;
    }

    public boolean add(OrderEntry orderEntry) {
      super.add(orderEntry);
      ((OrderEntryBaseImpl)orderEntry).setIndex(size() - 1);
      clearCachedEntries();
      return true;
    }

    public void add(int i, OrderEntry orderEntry) {
      super.add(i, orderEntry);
      clearCachedEntries();
      setIndicies(i);
    }

    public OrderEntry remove(int i) {
      OrderEntry entry = super.remove(i);
      setIndicies(i);
      if (myComponents.contains(entry)) {
        ((RootModelComponentBase)entry).dispose();
      }
      clearCachedEntries();
      return entry;
    }

    public boolean remove(Object o) {
      int index = indexOf(o);
      if (index < 0) return false;
      remove(index);
      clearCachedEntries();
      return true;
    }

    public boolean addAll(Collection<? extends OrderEntry> collection) {
      int startSize = size();
      boolean result = super.addAll(collection);
      setIndicies(startSize);
      clearCachedEntries();
      return result;
    }

    public boolean addAll(int i, Collection<? extends OrderEntry> collection) {
      boolean result = super.addAll(i, collection);
      setIndicies(i);
      clearCachedEntries();
      return result;
    }

    public void removeRange(int i, int i1) {
      super.removeRange(i, i1);
      clearCachedEntries();
      setIndicies(i);
    }

    public boolean removeAll(Collection<?> collection) {
      boolean result = super.removeAll(collection);
      setIndicies(0);
      clearCachedEntries();
      return result;
    }

    public boolean retainAll(Collection<?> collection) {
      boolean result = super.retainAll(collection);
      setIndicies(0);
      clearCachedEntries();
      return result;
    }

    private void clearCachedEntries() {
      myCachedOrderEntries = null;
    }
    private void setIndicies(int startIndex) {
      for (int j = startIndex; j < size(); j++) {
        ((OrderEntryBaseImpl)get(j)).setIndex(j);
      }
    }
  }

  @NotNull
  public String[] getDependencyModuleNames() {
    List<String> result = processOrder(new CollectDependentModules(), new ArrayList<String>());
    return ContainerUtil.toArray(result, new String[result.size()]);
  }

  @NotNull
  public VirtualFile[] getRootPaths(final OrderRootType rootType) {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(rootType);
    if (container != null) return container.getFiles();
    return VirtualFile.EMPTY_ARRAY;
  }

  @NotNull
  public String[] getRootUrls(final OrderRootType rootType) {
    final VirtualFilePointerContainer container = myOrderRootPointerContainers.get(rootType);
    if (container != null) return container.getUrls();
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @NotNull
  public Module[] getModuleDependencies() {
    final List<Module> result = new ArrayList<Module>();

    for (OrderEntry entry : getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final Module module1 = ((ModuleOrderEntry)entry).getModule();
        if (module1 != null) {
          result.add(module1);
        }
      }
    }

    return ContainerUtil.toArray(result, new Module[result.size()]);
  }

  VirtualFilePointerFactory pointerFactory() {
    return myVirtualFilePointerFactory;
  }

  private RootModelImpl getSourceModel() {
    assertWritable();
    return myModuleRootManager.getRootModel();
  }


  private static class CollectDependentModules extends RootPolicy<List<String>> {
    public List<String> visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, List<String> arrayList) {
      arrayList.add(moduleOrderEntry.getModuleName());
      return arrayList;
    }
  }



  public void setRootUrls(final OrderRootType orderRootType, final String[] urls) {
    assertWritable();
    VirtualFilePointerContainer container = myOrderRootPointerContainers.get(orderRootType);
    if (container == null) {
      container = myFilePointerManager.createContainer(myVirtualFilePointerFactory);
      myOrderRootPointerContainers.put(orderRootType, container);
    }
    container.clear();
    for (final String url : urls) {
      container.add(url);
    }
  }

  @Nullable
  protected VirtualFilePointer getOutputPathValue(Element element, String tag, final boolean createPointer) {
    final Element outputPathChild = element.getChild(tag);
    VirtualFilePointer vptr = null;
    if (outputPathChild != null && createPointer) {
      String outputPath = outputPathChild.getAttributeValue(ATTRIBUTE_URL);
      vptr = pointerFactory().create(outputPath);
    }
    return vptr;
  }

  @Nullable
  protected String getOutputPathValue(Element element, String tag) {
    final Element outputPathChild = element.getChild(tag);
    if (outputPathChild != null) {
      return outputPathChild.getAttributeValue(ATTRIBUTE_URL);
    }
    return null;
  }

  public <T> T getModuleExtension(final Class<T> klass) {
    for (ModuleExtension extension : myExtensions) {
      if (klass.isAssignableFrom(extension.getClass())) return (T)extension;
    }
    return null;
  }
}

