package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class EditorTabbedContainer implements Disposable {
  private final EditorWindow myWindow;
  private final Project myProject;
  private JBTabs myTabs;

  @NonNls public static final String HELP_ID = "ideaInterface.editor";

  EditorTabbedContainer(final EditorWindow window, Project project, int tabPlacement) {
    myWindow = window;
    myProject = project;
    final ActionManager actionManager = ActionManager.getInstance();
    myTabs = new JBTabsImpl(project, actionManager, IdeFocusManager.getInstance(project), this);
    myTabs.setDataProvider(new DataProvider() {
      public Object getData(@NonNls final String dataId) {
        if (DataConstants.PROJECT.equals(dataId)) {
          return myProject;
        }
        if (DataConstants.VIRTUAL_FILE.equals(dataId)) {
          final VirtualFile selectedFile = myWindow.getSelectedFile();
          return selectedFile != null && selectedFile.isValid() ? selectedFile : null;
        }
        if (DataConstantsEx.EDITOR_WINDOW.equals(dataId)) {
          return myWindow;
        }
        if (DataConstants.HELP_ID.equals(dataId)) {
          return HELP_ID;
        }
        return null;
      }
    }).setPopupGroup(new Getter<ActionGroup>() {
      public ActionGroup get() {
        return (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_TAB_POPUP);
      }
    }, ActionPlaces.EDITOR_POPUP, false).addTabMouseListener(new MouseAdapter() {
      public void mousePressed(final MouseEvent e) {
        if (UIUtil.isCloseClick(e)) {
          final TabInfo info = myTabs.findInfo(e);
          if (info != null) {
            myWindow.closeFile((VirtualFile)info.getObject());
          }
        }
      }

      @Override
      public void mouseClicked(final MouseEvent e) {
        if (UIUtil.isActionClick(e) && (e.getClickCount() % 2) == 0) {
          ActionUtil.execute("HideAllWindows", e, null, ActionPlaces.UNKNOWN, 0);
        }
        else if (UIUtil.isActionClick(e) && (e.isMetaDown() || (!SystemInfo.isMac && e.isControlDown()))) {
          final TabInfo info = myTabs.findInfo(e);
          if (info != null && info.getObject() != null) {
            final VirtualFile vFile = (VirtualFile)info.getObject();
            ShowFilePathAction.show(vFile, e);
          }
        }
      }
    }).getPresentation().setUiDecorator(new UiDecorator() {
      @NotNull
      public UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(1, 6, 1, 6));
      }
    }).setTabLabelActionsMouseDeadzone(TimedDeadzone.NULL)
        .setGhostsAlwaysVisible(true)
        .setTabLabelActionsAutoHide(false)
        .setActiveTabFillIn(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground())
        .setPaintFocus(false).getJBTabs().addListener(new TabsListener.Adapter() {
      public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
        final FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
        final FileEditor oldEditor = editorManager.getSelectedEditor((VirtualFile)oldSelection.getObject());
        if (oldEditor != null) {
          oldEditor.deselectNotify();
        }

        final FileEditor newEditor = editorManager.getSelectedEditor((VirtualFile)newSelection.getObject());
        if (newEditor != null) {
          newEditor.selectNotify();
        }
      }
    });

    updateTabBorder();

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      public void stateChanged() {
        updateTabBorder();
      }

      public void toolWindowRegistered(final String id) {
        updateTabBorder();
      }
    });

    Disposer.register(project, this);
  }

  public int getTabCount() {
    return myTabs.getTabCount();
  }

  public ActionCallback setSelectedIndex(final int indexToSelect) {
    return setSelectedIndex(indexToSelect, true);
  }

  public ActionCallback setSelectedIndex(final int indexToSelect, boolean focusEditor) {
    return myTabs.select(myTabs.getTabAt(indexToSelect), focusEditor);
  }

  private void updateTabBorder() {
    if (!myProject.isOpen()) return;

    myTabs.getComponent().setBorder(new EmptyBorder(1, 0, 0, 0));
    final List<String> rightIds = ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).getIdsOn(ToolWindowAnchor.RIGHT);
    myTabs.getPresentation().setPaintBorder(-1, -1, rightIds.size() > 0 ? 1 : 0, -1).setTabSidePaintBorder(5);
  }

  public Component getComponent() {
    return myTabs.getComponent();
  }

  public ActionCallback removeTabAt(final int componentIndex, int indexToSelect) {
    TabInfo toSelect = indexToSelect >=0 && indexToSelect < myTabs.getTabCount() ? myTabs.getTabAt(indexToSelect) : null;
    final ActionCallback callback = myTabs.removeTab(myTabs.getTabAt(componentIndex), toSelect);
    return myProject.isOpen() ? callback : new ActionCallback.Done();
  }

  public int getSelectedIndex() {
    return myTabs.getIndexOf(myTabs.getSelectedInfo());
  }

  public void setForegroundAt(final int index, final Color color) {
    myTabs.getTabAt(index).setDefaultForeground(color);
  }

  public void setWaveColor(final int index, @Nullable final Color color) {
    final TabInfo tab = myTabs.getTabAt(index);
    tab.setDefaultStyle(color == null ? SimpleTextAttributes.STYLE_PLAIN : SimpleTextAttributes.STYLE_WAVED);
    tab.setDefaultWaveColor(color);
  }

  public void setIconAt(final int index, final Icon icon) {
    myTabs.getTabAt(index).setIcon(icon);
  }

  public void setTitleAt(final int index, final String text) {
    myTabs.getTabAt(index).setText(text);
  }

  public void setToolTipTextAt(final int index, final String text) {
    myTabs.getTabAt(index).setTooltipText(text);
  }

  public void setTabLayoutPolicy(final int policy) {
    switch (policy) {
      case JTabbedPane.SCROLL_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(true);
        break;
      case JTabbedPane.WRAP_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(false);
        break;
      default:
        throw new IllegalArgumentException("Unsupported tab layout policy: " + policy);
    }
  }

  public void setTabPlacement(final int tabPlacement) {
    switch (tabPlacement) {
      case SwingConstants.TOP:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.top);
        break;
      case SwingConstants.BOTTOM:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.bottom);
        break;
      case SwingConstants.LEFT:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.left);
        break;
      case SwingConstants.RIGHT:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.right);
        break;
      default:
        throw new IllegalArgumentException("Unknown tab placement code=" + tabPlacement);
    }
  }

  @Nullable
  public Object getSelectedComponent() {
    final TabInfo info = myTabs.getTargetInfo();
    return info != null ? info.getComponent() : null;
  }

  public void insertTab(final VirtualFile file, final Icon icon, final JComponent comp, final String tooltip, final int indexToInsert) {

    TabInfo tab = myTabs.findInfo(file);
    if (tab != null) return;


    tab = new TabInfo(comp).setText(calcTabTitle(myProject, file)).setIcon(icon).setTooltipText(tooltip).setObject(file);

    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTab(comp, tab));

    tab.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    myTabs.addTab(tab, indexToInsert);
  }

  public static String calcTabTitle(final Project project, final VirtualFile file) {
    for (EditorTabTitleProvider provider : Extensions.getExtensions(EditorTabTitleProvider.EP_NAME)) {
      final String result = provider.getEditorTabTitle(project, file);
      if (result != null) {
        return result;
      }
    }

    return file.getPresentableName();
  }

  public Component getComponentAt(final int i) {
    final TabInfo tab = myTabs.getTabAt(i);
    return tab.getComponent();
  }

  public void dispose() {

  }

  private class CloseTab extends AnAction {

    ShadowAction myShadow;
    private TabInfo myTabInfo;

    public CloseTab(JComponent c, TabInfo info) {
      myTabInfo = info;
      myShadow = new ShadowAction(this, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_EDITOR), c);
    }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setIcon(IconLoader.getIcon("/actions/close.png"));
      e.getPresentation().setHoveredIcon(IconLoader.getIcon("/actions/closeHovered.png"));
      e.getPresentation().setVisible(UISettings.getInstance().SHOW_CLOSE_BUTTON);
    }

    public void actionPerformed(final AnActionEvent e) {
      myWindow.closeFile((VirtualFile)myTabInfo.getObject());
    }
  }
}
