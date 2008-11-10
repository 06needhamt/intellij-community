package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.containers.WeakHashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HighlightingSettingsPerFile implements JDOMExternalizable, ProjectComponent {
  @NonNls private static final String SETTING_TAG = "setting";
  @NonNls private static final String ROOT_ATT_PREFIX = "root";
  @NonNls private static final String FILE_ATT = "file";

  public static HighlightingSettingsPerFile getInstance(Project progect){
    return progect.getComponent(HighlightingSettingsPerFile.class);
  }

  private final Map<VirtualFile, FileHighlighingSetting[]> myHighlightSettings = new HashMap<VirtualFile, FileHighlighingSetting[]>();

  private final Map<PsiFile, InspectionProfile> myProfileSettings = new WeakHashMap<PsiFile, InspectionProfile>();

  public FileHighlighingSetting getHighlightingSettingForRoot(PsiElement root){
    final PsiFile containingFile = root.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    FileHighlighingSetting[] fileHighlighingSettings = myHighlightSettings.get(virtualFile);
    final int index = PsiUtilBase.getRootIndex(root);

    if(fileHighlighingSettings == null || fileHighlighingSettings.length <= index) {
      return FileHighlighingSetting.FORCE_HIGHLIGHTING;
    }
    return fileHighlighingSettings[index];
  }

  public static FileHighlighingSetting[] getDefaults(PsiFile file){
    final int rootsCount = file.getViewProvider().getLanguages().size();
    final FileHighlighingSetting[] fileHighlighingSettings = new FileHighlighingSetting[rootsCount];
    for (int i = 0; i < fileHighlighingSettings.length; i++) {
      fileHighlighingSettings[i] = FileHighlighingSetting.FORCE_HIGHLIGHTING;
    }
    return fileHighlighingSettings;
  }

  public void setHighlightingSettingForRoot(PsiElement root, FileHighlighingSetting setting) {
    final PsiFile containingFile = root.getContainingFile();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return;
    FileHighlighingSetting[] defaults = myHighlightSettings.get(virtualFile);
    int rootIndex = PsiUtilBase.getRootIndex(root);
    if (defaults != null && rootIndex >= defaults.length) defaults = null;
    if (defaults == null) defaults = getDefaults(containingFile);
    defaults[rootIndex] = setting;
    boolean toRemove = true;
    for (FileHighlighingSetting aDefault : defaults) {
      if (aDefault != FileHighlighingSetting.NONE) toRemove = false;
    }
    if (!toRemove) {
      myHighlightSettings.put(virtualFile, defaults);
    }
    else {
      myHighlightSettings.remove(virtualFile);
    }
  }

  public void projectOpened() {}
  public void projectClosed() {}
  @NotNull public String getComponentName() {
    return "HighlightingSettingsPerFile";
  }

  public void initComponent() {}
  public void disposeComponent() {}

  public void readExternal(Element element) throws InvalidDataException {
    List children = element.getChildren(SETTING_TAG);
    for (final Object aChildren : children) {
      final Element child = (Element)aChildren;
      final String url = child.getAttributeValue(FILE_ATT);
      if (url == null) continue;
      final VirtualFile fileByUrl = VirtualFileManager.getInstance().findFileByUrl(url);
      if (fileByUrl != null) {
        final List<FileHighlighingSetting> settings = new ArrayList<FileHighlighingSetting>();
        int index = 0;
        while (child.getAttributeValue(ROOT_ATT_PREFIX + index) != null) {
          final String attributeValue = child.getAttributeValue(ROOT_ATT_PREFIX + index++);
          settings.add(Enum.valueOf(FileHighlighingSetting.class, attributeValue));
        }
        myHighlightSettings.put(fileByUrl, settings.toArray(new FileHighlighingSetting[settings.size()]));
      }
    }
  }


  public void writeExternal(Element element) throws WriteExternalException {
    if (myHighlightSettings.isEmpty()) throw new WriteExternalException();
    for (Map.Entry<VirtualFile, FileHighlighingSetting[]> entry : myHighlightSettings.entrySet()) {
      final Element child = new Element(SETTING_TAG);

      final VirtualFile vFile = entry.getKey();
      if (!vFile.isValid()) continue;
      child.setAttribute(FILE_ATT, vFile.getUrl());
      for (int i = 0; i < entry.getValue().length; i++) {
        final FileHighlighingSetting fileHighlighingSetting = entry.getValue()[i];
        child.setAttribute(ROOT_ATT_PREFIX + i, fileHighlighingSetting.toString());
      }
      element.addContent(child);
    }
  }

  public synchronized void cleanProfileSettings() {
    myProfileSettings.clear();
  }

  public synchronized InspectionProfile getInspectionProfile(final PsiFile file) {
    return myProfileSettings.get(file);
  }

  public synchronized void addProfileSettingForFile(final PsiFile file, final InspectionProfile profile) {
    myProfileSettings.put(file, profile);
  }

}
