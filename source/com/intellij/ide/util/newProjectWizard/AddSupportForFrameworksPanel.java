/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.util.newProjectWizard;

import com.intellij.facet.impl.ui.FacetEditorContextBase;
import com.intellij.facet.impl.ui.FacetTypeFrameworkSupportProvider;
import com.intellij.facet.impl.ui.libraries.LibraryDownloader;
import com.intellij.facet.impl.ui.libraries.RequiredLibrariesInfo;
import com.intellij.facet.impl.ui.libraries.LibraryDownloadingMirrorsMap;
import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.facet.ui.libraries.RemoteRepositoryInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author nik
 */
public class AddSupportForFrameworksPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.newProjectWizard.AddSupportForFrameworksStep");
  private static final int INDENT = 20;
  private static final int SPACE_AFTER_TITLE = 5;
  private static final int VERTICAL_SPACE = 5;
  private JPanel myMainPanel;
  private JPanel myFrameworksTreePanel;
  private JButton myChangeButton;
  private JPanel myDownloadingOptionsPanel;
  private List<FrameworkSupportSettings> myRoots;
  private final Computable<String> myBaseDirForLibrariesGetter;
  private List<FrameworkSupportProvider> myProviders;
  private LibraryDownloadingMirrorsMap myMirrorsMap;

  public AddSupportForFrameworksPanel(final List<FrameworkSupportProvider> providers, Computable<String> baseDirForLibrariesGetter) {
    myBaseDirForLibrariesGetter = baseDirForLibrariesGetter;
    myProviders = providers;
    createNodes();
    myMirrorsMap = creatMirrorsMap();

    final JPanel treePanel = new JPanel(new GridBagLayout());
    addSettingsComponents(myRoots, treePanel, 0);
    myFrameworksTreePanel.add(treePanel, BorderLayout.WEST);
    myChangeButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        HashSet<RemoteRepositoryInfo> repositories = new HashSet<RemoteRepositoryInfo>(getRemoteRepositories(true));
        new LibraryDownloadingSettingsDialog(myMainPanel, getFrameworkWithLibraries(), myMirrorsMap, repositories).show();
        updateDownloadingOptionsPanel();
      }
    });
    updateDownloadingOptionsPanel();
  }

  private LibraryDownloadingMirrorsMap creatMirrorsMap() {
    List<RemoteRepositoryInfo> repositoryInfos = getRemoteRepositories(false);
    return new LibraryDownloadingMirrorsMap(repositoryInfos.toArray(new RemoteRepositoryInfo[repositoryInfos.size()]));
  }

  private List<RemoteRepositoryInfo> getRemoteRepositories(final boolean fromSelectedOnly) {
    List<RemoteRepositoryInfo> repositoryInfos = new ArrayList<RemoteRepositoryInfo>();
    List<FrameworkSupportSettings> frameworksSettingsList = getFrameworksSettingsList(fromSelectedOnly);
    for (FrameworkSupportSettings settings : frameworksSettingsList) {
      LibraryInfo[] libraries = settings.getConfigurable().getLibraries();
      for (LibraryInfo library : libraries) {
        LibraryDownloadInfo downloadInfo = library.getDownloadingInfo();
        if (downloadInfo != null) {
          RemoteRepositoryInfo repository = downloadInfo.getRemoteRepository();
          if (repository != null) {
            repositoryInfos.add(repository);
          }
        }
      }
    }
    return repositoryInfos;
  }

  private void updateDownloadingOptionsPanel() {
    @NonNls String card = getFrameworkWithLibraries().isEmpty() ? "empty" : "options";
    ((CardLayout)myDownloadingOptionsPanel.getLayout()).show(myDownloadingOptionsPanel, card);
  }

  private List<FrameworkSupportSettings> getFrameworkWithLibraries() {
    List<FrameworkSupportSettings> frameworkLibrariesInfos = new ArrayList<FrameworkSupportSettings>();
    List<FrameworkSupportSettings> selected = getFrameworksSettingsList(true);
    for (FrameworkSupportSettings settings : selected) {
      LibraryInfo[] libraries = settings.getConfigurable().getLibraries();
      if (libraries.length > 0) {
        frameworkLibrariesInfos.add(settings);
      }
    }
    return frameworkLibrariesInfos;
  }

  public boolean downloadLibraries() {
    List<FrameworkSupportSettings> list = getFrameworkWithLibraries();
    for (FrameworkSupportSettings settings : list) {
      if (settings.isDownloadLibraries()) {
        RequiredLibrariesInfo requiredLibraries = new RequiredLibrariesInfo(settings.getConfigurable().getLibraries());
        RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = requiredLibraries.checkLibraries(settings.getAddedJars().toArray(new VirtualFile[settings.getAddedJars().size()]));
        if (info != null) {
          LibraryDownloadInfo[] downloadingInfos = LibraryDownloader.getDownloadingInfos(info.getLibraryInfos());
          if (downloadingInfos.length > 0) {
            String libraryName = settings.getConfigurable().getLibraryName();
            if (FrameworkSupportConfigurable.DEFAULT_LIBRARY_NAME.equals(libraryName)) {
              libraryName = null;
            }

            LibraryDownloader downloader = new LibraryDownloader(downloadingInfos, null, myMainPanel,
                                                                 settings.getDirectoryForDownloadedLibrariesPath(),
                                                                 libraryName, myMirrorsMap);
            VirtualFile[] files = downloader.download();
            if (files.length != downloadingInfos.length) {
              return false;
            }
            settings.myAddedJars.addAll(Arrays.asList(files));
          }
        }
      }
    }
    return true;
  }

  private JPanel addSettingsComponents(final List<FrameworkSupportSettings> list, JPanel treePanel, int level) {
    for (FrameworkSupportSettings root : list) {
      addSettingsComponents(root, treePanel, level);
    }
    return treePanel;
  }

  private void addSettingsComponents(final FrameworkSupportSettings frameworkSupport, JPanel parentPanel, int level) {
    if (frameworkSupport.getParentNode() != null) {
      frameworkSupport.setEnabled(false);
    }
    JComponent configurableComponent = frameworkSupport.getConfigurable().getComponent();
    int gridwidth = configurableComponent != null ? 1 : GridBagConstraints.REMAINDER;
    parentPanel.add(frameworkSupport.myCheckBox, createConstraints(0, GridBagConstraints.RELATIVE, gridwidth, 1,
                                                                   new Insets(0, INDENT * level, VERTICAL_SPACE, SPACE_AFTER_TITLE)));
    if (configurableComponent != null) {
      parentPanel.add(configurableComponent, createConstraints(1, GridBagConstraints.RELATIVE, GridBagConstraints.REMAINDER, 1,
                                                               new Insets(0, 0, VERTICAL_SPACE, 0)));
    }

    if (frameworkSupport.myChildren.isEmpty()) {
      return;
    }

    addSettingsComponents(frameworkSupport.myChildren, parentPanel, level + 1);
  }

  private static GridBagConstraints createConstraints(final int gridx, final int gridy, final int gridwidth, final int gridheight,
                                               final Insets insets) {
    return new GridBagConstraints(gridx, gridy, gridwidth, gridheight, 1, 1, GridBagConstraints.WEST,
                                                                        GridBagConstraints.NONE, insets, 0, 0);
  }

  private void createNodes() {
    Map<String, FrameworkSupportSettings> nodes = new HashMap<String, FrameworkSupportSettings>();
    for (FrameworkSupportProvider frameworkSupport : myProviders) {
      createNode(frameworkSupport, nodes);
    }

    myRoots = new ArrayList<FrameworkSupportSettings>();
    for (FrameworkSupportSettings settings : nodes.values()) {
      if (settings.getParentNode() == null) {
        myRoots.add(settings);
      }
    }

    DFSTBuilder<FrameworkSupportProvider> builder = new DFSTBuilder<FrameworkSupportProvider>(GraphGenerator.create(CachingSemiGraph.create(new ProvidersGraph(myProviders))));
    if (!builder.isAcyclic()) {
      Pair<FrameworkSupportProvider,FrameworkSupportProvider> pair = builder.getCircularDependency();
      LOG.error("Circular dependency between providers '" + pair.getFirst().getId() + "' and '" + pair.getSecond().getId() + "' was found.");
    }

    final Comparator<FrameworkSupportProvider> comparator = builder.comparator();
    sortNodes(myRoots, new Comparator<FrameworkSupportSettings>() {
      public int compare(final FrameworkSupportSettings o1, final FrameworkSupportSettings o2) {
        return comparator.compare(o1.getProvider(), o2.getProvider());
      }
    });
  }

  private static void sortNodes(final List<FrameworkSupportSettings> list, final Comparator<FrameworkSupportSettings> comparator) {
    Collections.sort(list, comparator);
    for (FrameworkSupportSettings frameworkSupportSettings : list) {
      sortNodes(frameworkSupportSettings.myChildren, comparator);
    }
  }

  @Nullable
  private FrameworkSupportSettings createNode(final FrameworkSupportProvider provider, final Map<String, FrameworkSupportSettings> nodes) {
    FrameworkSupportSettings node = nodes.get(provider.getId());
    if (node == null) {
      String underlyingFrameworkId = provider.getUnderlyingFrameworkId();
      FrameworkSupportSettings parentNode = null;
      if (underlyingFrameworkId != null) {
        FrameworkSupportProvider parentProvider = findProvider(underlyingFrameworkId);
        if (parentProvider == null) {
          LOG.info("Cannot find id = " + underlyingFrameworkId);
          return null;
        }
        parentNode = createNode(parentProvider, nodes);
      }
      node = new FrameworkSupportSettings(provider, parentNode);
      nodes.put(provider.getId(), node);
    }
    return node;
  }

  private String getBaseModuleDirectoryPath() {
    return myBaseDirForLibrariesGetter.compute();
  }

  @Nullable
  private FrameworkSupportProvider findProvider(@NotNull String id) {
    for (FrameworkSupportProvider provider : myProviders) {
      if (id.equals(provider.getId())) {
        return provider;
      }
    }
    LOG.info("Cannot find framework support provider '" + id + "'");
    return null;
  }

  private static void setDescendantsEnabled(FrameworkSupportSettings frameworkSupport, final boolean enable) {
    for (FrameworkSupportSettings child : frameworkSupport.myChildren) {
      child.setEnabled(enable);
      setDescendantsEnabled(child, enable);
    }
  }

  public JComponent getMainPanel() {
    return myMainPanel;
  }

  private List<FrameworkSupportSettings> getFrameworksSettingsList(final boolean selectedOnly) {
    ArrayList<FrameworkSupportSettings> list = new ArrayList<FrameworkSupportSettings>();
    if (myRoots != null) {
      addChildFrameworks(myRoots, list, selectedOnly);
    }
    return list;
  }

  private static void addChildFrameworks(final List<FrameworkSupportSettings> list, final ArrayList<FrameworkSupportSettings> selected,
                                         final boolean selectedOnly) {
    for (FrameworkSupportSettings settings : list) {
      if (!selectedOnly || settings.myCheckBox.isSelected()) {
        selected.add(settings);
        addChildFrameworks(settings.myChildren, selected, selectedOnly);
      }
    }
  }

  public void addSupport(final Module module, final ModifiableRootModel rootModel) {
    List<Library> addedLibraries = new ArrayList<Library>();
    List<FrameworkSupportSettings> selectedFrameworks = getFrameworksSettingsList(true);
    for (FrameworkSupportSettings settings : selectedFrameworks) {
      Library library = null;
      FrameworkSupportConfigurable configurable = settings.getConfigurable();
      if (!settings.myAddedJars.isEmpty()) {
        VirtualFile[] roots = settings.myAddedJars.toArray(new VirtualFile[settings.myAddedJars.size()]);
        LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(module.getProject());
        library = FacetEditorContextBase.createLibraryInTable(configurable.getLibraryName(), roots, VirtualFile.EMPTY_ARRAY, table);
        addedLibraries.add(library);
        rootModel.addLibraryEntry(library);
      }
      configurable.addSupport(module, rootModel, library);
    }
    for (FrameworkSupportSettings settings : selectedFrameworks) {
      FrameworkSupportProvider provider = settings.myProvider;
      if (provider instanceof FacetTypeFrameworkSupportProvider) {
        ((FacetTypeFrameworkSupportProvider)provider).processAddedLibraries(module, addedLibraries);
      }
    }
  }

  public class FrameworkSupportSettings {
    private final FrameworkSupportProvider myProvider;
    private final FrameworkSupportSettings myParentNode;
    private final FrameworkSupportConfigurable myConfigurable;
    private JCheckBox myCheckBox;
    private List<FrameworkSupportSettings> myChildren = new ArrayList<FrameworkSupportSettings>();
    private @NonNls String myDirectoryForDownloadedLibrariesPath;
    private Set<VirtualFile> myAddedJars = new LinkedHashSet<VirtualFile>();
    private boolean myDownloadLibraries = true;

    private FrameworkSupportSettings(final FrameworkSupportProvider provider, final FrameworkSupportSettings parentNode) {
      myProvider = provider;
      myParentNode = parentNode;
      myConfigurable = provider.createConfigurable();
      myCheckBox = new JCheckBox(provider.getTitle());
      if (parentNode != null) {
        parentNode.myChildren.add(this);
      }

      myCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          setConfigurableComponentEnabled(myCheckBox.isSelected());
          setDescendantsEnabled(FrameworkSupportSettings.this, myCheckBox.isSelected());
        }
      });

      setConfigurableComponentEnabled(false);
    }

    public void setEnabled(final boolean enable) {
      myCheckBox.setEnabled(enable);
      if (!enable) {
        myCheckBox.setSelected(false);
        setConfigurableComponentEnabled(false);
      }
    }

    private void setConfigurableComponentEnabled(final boolean enable) {
      JComponent component = getConfigurable().getComponent();
      if (component != null) {
        UIUtil.setEnabled(component, enable, true);
      }
      updateDownloadingOptionsPanel();
    }

    public FrameworkSupportProvider getProvider() {
      return myProvider;
    }

    public FrameworkSupportSettings getParentNode() {
      return myParentNode;
    }

    public FrameworkSupportConfigurable getConfigurable() {
      return myConfigurable;
    }

    public Set<VirtualFile> getAddedJars() {
      return myAddedJars;
    }

    public void setAddedJars(final List<VirtualFile> addedJars) {
      myAddedJars = new LinkedHashSet<VirtualFile>(addedJars);
    }

    public String getDirectoryForDownloadedLibrariesPath() {
      if (myDirectoryForDownloadedLibrariesPath == null) {
        myDirectoryForDownloadedLibrariesPath = getModuleDirectoryPath() + "/lib";
      }
      return myDirectoryForDownloadedLibrariesPath;
    }

    public String getModuleDirectoryPath() {
      return getBaseModuleDirectoryPath();
    }

    public void setDirectoryForDownloadedLibrariesPath(final String directoryForDownloadedLibrariesPath) {
      myDirectoryForDownloadedLibrariesPath = directoryForDownloadedLibrariesPath;
    }

    public void setDownloadLibraries(final boolean downloadLibraries) {
      myDownloadLibraries = downloadLibraries;
    }

    public boolean isDownloadLibraries() {
      return myDownloadLibraries;
    }
  }

  private class ProvidersGraph implements GraphGenerator.SemiGraph<FrameworkSupportProvider> {
    private final List<FrameworkSupportProvider> myFrameworkSupportProviders;

    public ProvidersGraph(final List<FrameworkSupportProvider> frameworkSupportProviders) {
      myFrameworkSupportProviders = frameworkSupportProviders;
    }

    public Collection<FrameworkSupportProvider> getNodes() {
      return myFrameworkSupportProviders;
    }

    public Iterator<FrameworkSupportProvider> getIn(final FrameworkSupportProvider provider) {
      String[] ids = provider.getPrecedingFrameworkProviderIds();
      List<FrameworkSupportProvider> dependencies = new ArrayList<FrameworkSupportProvider>();
      String underlyingId = provider.getUnderlyingFrameworkId();
      if (underlyingId != null) {
        FrameworkSupportProvider underlyingProvider = findProvider(underlyingId);
        if (underlyingProvider != null) {
          dependencies.add(underlyingProvider);
        }
      }
      for (String id : ids) {
        FrameworkSupportProvider dependency = findProvider(id);
        if (dependency != null) {
          dependencies.add(dependency);
        }
      }
      return dependencies.iterator();
    }
  }
}
