package com.intellij.ide.structureView.impl;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.impl.StructureViewSelectInTarget;
import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import org.jdom.Element;

import java.util.*;

/**
 * @author Eugene Belyaev
 */
public final class StructureViewFactoryImpl extends StructureViewFactoryEx implements JDOMExternalizable, ProjectComponent {
  public boolean AUTOSCROLL_MODE = true;
  public boolean AUTOSCROLL_FROM_SOURCE = false;

  public String ACTIVE_ACTIONS = "";

  private Project myProject;
  private StructureViewWrapperImpl myStructureViewWrapperImpl;

  private final MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension> myExtensions = new MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension>();

  public StructureViewFactoryImpl(Project project) {
    myProject = project;
  }

  public StructureViewWrapper getStructureViewWrapper() {
    return myStructureViewWrapperImpl;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectOpened() {
    myStructureViewWrapperImpl = new StructureViewWrapperImpl(myProject);
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run(){
        ToolWindowManager toolWindowManager=ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow=toolWindowManager.registerToolWindow(ToolWindowId.STRUCTURE_VIEW,myStructureViewWrapperImpl.getComponent(),ToolWindowAnchor.LEFT);
        toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowStructure.png"));
        SelectInManager.getInstance(myProject).addTarget(new StructureViewSelectInTarget(myProject));
      }
    });
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.STRUCTURE_VIEW);
    myStructureViewWrapperImpl.dispose();
    myStructureViewWrapperImpl=null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public String getComponentName() {
    return "StructureViewFactory";
  }


  public void registerExtension(Class<? extends PsiElement> type, StructureViewExtension extension) {
    myExtensions.put(type, extension);
  }

  public void unregisterExtension(Class<? extends PsiElement> type, StructureViewExtension extension) {
    myExtensions.remove(type, extension);
  }

  public List<StructureViewExtension> getAllExtensions(Class<? extends PsiElement> type) {
    ArrayList<StructureViewExtension> result = new ArrayList<StructureViewExtension>();

    for (Iterator<Class<? extends PsiElement>> iterator = myExtensions.keySet().iterator(); iterator.hasNext();) {
      Class<? extends PsiElement> registeregType = iterator.next();
      if (registeregType.isAssignableFrom(type)) result.addAll(myExtensions.get(registeregType));
    }
    return result;
  }

  public void setActiveAction(final String name, final boolean state) {

    Collection<String> activeActions = collectActiveActions();

    if (state) {
      activeActions.add(name);
    } else {
      activeActions.remove(name);
    }

    ACTIVE_ACTIONS = toString(activeActions);
  }

  private String toString(final Collection<String> activeActions) {
    final StringBuffer result = new StringBuffer();
    for (Iterator<String> iterator = activeActions.iterator(); iterator.hasNext();) {
      final String actionName = iterator.next();
      if (actionName.trim().length() > 0) {
        result.append(actionName);
        if (iterator.hasNext()) {
          result.append(",");
        }
      }
    }
    return result.toString();
  }

  private Collection<String> collectActiveActions() {
    final String[] strings = ACTIVE_ACTIONS.split(",");
    return new HashSet<String>(Arrays.asList(strings));
  }

  public boolean isActionActive(final String name) {
    return collectActiveActions().contains(name);
  }
}