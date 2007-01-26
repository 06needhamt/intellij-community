package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditorTracker implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.EditorTracker");

  private final Project myProject;
  private WindowManager myWindowManager;
  private EditorFactory myEditorFactory;
  private FileEditorManager myFileEditorManager;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"}) private ToolWindowManager myToolwindowManager;

  private Map<Window, List<Editor>> myWindowToEditorsMap = new HashMap<Window, List<Editor>>();
  private Map<Window, WindowFocusListener> myWindowToWindowFocusListenerMap = new HashMap<Window, WindowFocusListener>();
  private Map<Editor, Window> myEditorToWindowMap = new HashMap<Editor, Window>();
  private static final Editor[] EMPTY_EDITOR_ARRAY = new Editor[0];
  private Editor[] myActiveEditors = EMPTY_EDITOR_ARRAY;

  private MyEditorFactoryListener myEditorFactoryListener;
  private EventDispatcher<EditorTrackerListener> myDispatcher = EventDispatcher.create(EditorTrackerListener.class);

  private IdeFrameImpl myIdeFrame;
  private Window myActiveWindow = null;
  private final WindowFocusListener myIdeFrameFocusListener = new WindowFocusListener() {
    public void windowGainedFocus(WindowEvent e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("windowGainedFocus for IdeFrame");
      }
      setActiveWindow(myIdeFrame);
    }

    public void windowLostFocus(WindowEvent e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("windowLostFocus for IdeFrame");
      }
      setActiveWindow(null);
    }
  };

  //todo:
  //toolwindow manager is unfortunately needed since
  //it actually initializes frame in WindowManager
  public EditorTracker(Project project, final WindowManager windowManager, final EditorFactory editorFactory,
                       final FileEditorManager fileEditorManager, ToolWindowManager toolwindowManager) {
    myProject = project;
    myWindowManager = windowManager;
    myEditorFactory = editorFactory;
    myFileEditorManager = fileEditorManager;
    myToolwindowManager = toolwindowManager;

  }


  public void projectOpened() {
    myIdeFrame = ((WindowManagerEx)myWindowManager).getFrame(myProject);
    myFileEditorManager.addFileEditorManagerListener(new FileEditorManagerAdapter() {
      public void selectionChanged(FileEditorManagerEvent event) {
        if (myIdeFrame.getFocusOwner() == null) return;
        setActiveWindow(myIdeFrame);
      }
    });
    if (myIdeFrame != null) {
      myIdeFrame.addWindowFocusListener(myIdeFrameFocusListener);
    }

    myEditorFactoryListener = new MyEditorFactoryListener(myProject);
    myEditorFactory.addEditorFactoryListener(myEditorFactoryListener);
  }

  public void projectClosed() {
    if (myEditorFactoryListener != null) {
      myEditorFactoryListener.dispose(null);
    }
    myEditorFactory.removeEditorFactoryListener(myEditorFactoryListener);
    if (myIdeFrame != null) {
      myIdeFrame.removeWindowFocusListener(myIdeFrameFocusListener);
    }
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "EditorTracker";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private void editorFocused(Editor editor) {
    Window window = myEditorToWindowMap.get(editor);
    if (window == null) return;

    List<Editor> list = myWindowToEditorsMap.get(window);
    int index = list.indexOf(editor);
    LOG.assertTrue(index >= 0);
    if (list.isEmpty()) return;

    for (int i = index - 1; i >= 0; i--) {
      list.set(i + 1, list.get(i));
    }
    list.set(0, editor);

    setActiveWindow(window);
  }

  private void registerEditor(Editor editor) {
    unregisterEditor(editor);

    final Window window = windowByEditor(editor);
    if (window == null) return;

    myEditorToWindowMap.put(editor, window);
    List<Editor> list = myWindowToEditorsMap.get(window);
    if (list == null) {
      list = new ArrayList<Editor>();
      myWindowToEditorsMap.put(window, list);

      if (!(window instanceof IdeFrameImpl)) {
        WindowFocusListener listener =  new WindowFocusListener() {
          public void windowGainedFocus(WindowEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("windowGainedFocus:" + window);
            }

            setActiveWindow(window);
          }

          public void windowLostFocus(WindowEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("windowLostFocus:" + window);
            }

            setActiveWindow(null);
          }
        };
        myWindowToWindowFocusListenerMap.put(window, listener);
        window.addWindowFocusListener(listener);
      }
    }
    list.add(editor);

    if (myActiveWindow == window) {
      setActiveWindow(window); // to fire event
    }
  }

  private void unregisterEditor(Editor editor) {
    Window oldWindow = myEditorToWindowMap.get(editor);
    if (oldWindow != null) {
      myEditorToWindowMap.remove(editor);
      List<Editor> editorsList = myWindowToEditorsMap.get(oldWindow);
      boolean removed = editorsList.remove(editor);
      LOG.assertTrue(removed);
      
      if (editorsList.isEmpty()) {
        myWindowToEditorsMap.remove(oldWindow);
        final WindowFocusListener listener = myWindowToWindowFocusListenerMap.remove(oldWindow);
        if (listener != null) oldWindow.removeWindowFocusListener(listener);
      }
    }
  }

  private Window windowByEditor(Editor editor) {
    Window window = SwingUtilities.windowForComponent(editor.getComponent());
    if (window instanceof IdeFrameImpl) {
      if (window != myIdeFrame) return null;
    }
    return window;
  }

  public Editor[] getActiveEditors() {
    return myActiveEditors;
  }

  private void setActiveWindow(Window window) {
    myActiveWindow = window;
    Editor[] editors = editorsByWindow(myActiveWindow);
    setActiveEditors(editors);
  }

  private Editor[] editorsByWindow(Window window) {
    List<Editor> list = myWindowToEditorsMap.get(window);
    if (list == null) return EMPTY_EDITOR_ARRAY;
    List<Editor> filtered = new ArrayList<Editor>();
    for (Editor editor : list) {
      if (editor.getContentComponent().isShowing()) {
        filtered.add(editor);
      }
    }
    return filtered.toArray(new Editor[filtered.size()]);
  }

  private void setActiveEditors(Editor[] editors) {
    myActiveEditors = editors;

    if (LOG.isDebugEnabled()) {
      LOG.debug("active editors changed:");
      if (editors.length > 0) {
        for (Editor editor : editors) {
          PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
          LOG.debug("    " + psiFile);
        }
      }
      else {
        LOG.debug("    <none>");
      }
    }

    myDispatcher.getMulticaster().activeEditorsChanged(editors);
  }

  public void addEditorTrackerListener(EditorTrackerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeEditorTrackerListener(EditorTrackerListener listener) {
    myDispatcher.removeListener(listener);
  }

  private class MyEditorFactoryListener implements EditorFactoryListener {
    private Map<Editor, Runnable> myExecuteOnEditorRelease = new HashMap<Editor, Runnable>();
    private final Project myProject;

    public MyEditorFactoryListener(final Project project) {
      myProject = project;
    }

    public void editorCreated(EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
      if (psiFile == null) return;

      final JComponent component = editor.getComponent();
      final JComponent contentComponent = editor.getContentComponent();

      final HierarchyListener hierarchyListener = new HierarchyListener() {
        public void hierarchyChanged(HierarchyEvent e) {
          registerEditor(editor);
        }
      };
      component.addHierarchyListener(hierarchyListener);

      final FocusListener focusListener = new FocusListener() {
        public void focusGained(FocusEvent e) {
          editorFocused(editor);
        }

        public void focusLost(FocusEvent e) {
        }
      };
      contentComponent.addFocusListener(focusListener);

      myExecuteOnEditorRelease.put(event.getEditor(), new Runnable() {
        public void run() {
          component.removeHierarchyListener(hierarchyListener);
          contentComponent.removeFocusListener(focusListener);
        }
      });
    }

    public void editorReleased(EditorFactoryEvent event) {
      final Editor editor = event.getEditor();
      unregisterEditor(editor);
      dispose(editor);
    }

    private void dispose(Editor editor) {
      if (editor == null) {
        for (Runnable r : myExecuteOnEditorRelease.values()) {
          r.run();
        }
        myExecuteOnEditorRelease.clear();
      }
      else {
        final Runnable runnable = myExecuteOnEditorRelease.get(editor);
        if (runnable != null) {
          runnable.run();
          myExecuteOnEditorRelease.remove(editor);
        }
      }
    }
  }
}
