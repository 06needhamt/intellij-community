package com.intellij.ide.bookmarks;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class BookmarkManager implements JDOMExternalizable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.bookmarks.BookmarkManager");
  private final Project myProject;
  private final BookmarksCollection.ForEditors myEditorBookmarks = new BookmarksCollection.ForEditors();
  private final BookmarksCollection.ForPsiElements myCommanderBookmarks = new BookmarksCollection.ForPsiElements();
  private final MyEditorMouseListener myEditorMouseListener = new MyEditorMouseListener();
  public static final int MAX_AUTO_DESCRIPTION_SIZE = 50;

  public static BookmarkManager getInstance(Project project) {
    return project.getComponent(BookmarkManager.class);
  }

  public BookmarkManager(Project project) {
    myProject = project;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectOpened() {
    EditorEventMulticasterEx eventMulticaster = (EditorEventMulticasterEx)EditorFactory.getInstance()
      .getEventMulticaster();
    eventMulticaster.addEditorMouseListener(myEditorMouseListener);
  }

  public void projectClosed() {
    EditorEventMulticasterEx eventMulticaster = (EditorEventMulticasterEx)EditorFactory.getInstance()
      .getEventMulticaster();
    eventMulticaster.removeEditorMouseListener(myEditorMouseListener);
  }

  public Project getProject() {
    return myProject;
  }



  public void addEditorBookmark(Editor editor, int lineIndex, int number) {
    Document document = editor.getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null) return;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return;
    String name = virtualFile.getPath();
    if (name == null) return;
    RangeHighlighter lineMarker = ((MarkupModelEx)document.getMarkupModel(myProject)).addPersistentLineHighlighter(
      lineIndex, HighlighterLayer.ERROR + 1, null);
    if (lineMarker == null) return;
    myEditorBookmarks.addBookmark(createEditorBookmark(editor, lineIndex, number, document, lineMarker));
  }

  protected EditorBookmark createEditorBookmark(final Editor editor, final int lineIndex, final int number, final Document document,
                                              final RangeHighlighter lineMarker) {
    return new EditorBookmark(document, myProject, lineMarker, getAutoDescription(editor, lineIndex), number);
  }

  public static String getAutoDescription(final Editor editor, final int lineIndex) {
    String autoDescription = editor.getSelectionModel().getSelectedText();
    if ( autoDescription == null ) {
      Document document = editor.getDocument();
      autoDescription = document.getCharsSequence()
        .subSequence(document.getLineStartOffset(lineIndex), document.getLineEndOffset(lineIndex)).toString().trim();
    }
    if ( autoDescription.length () > MAX_AUTO_DESCRIPTION_SIZE) {
      return autoDescription.substring(0, MAX_AUTO_DESCRIPTION_SIZE)+"...";
    }
    return autoDescription;
  }

  public void addCommanderBookmark(PsiElement element) {
    addCommanderBookmark(element, "", false);
  }

  public void addCommanderBookmark(PsiElement element, final String description, boolean last) {
    if (element == null) return;
    if (myCommanderBookmarks.findByPsiElement(element) != null) return;

    final CommanderBookmark bookmark = createCommanderBookmark(element, description);
    if (bookmark != null) {
      if (last) {
        myCommanderBookmarks.addLast(bookmark);
      }
      else {
        myCommanderBookmarks.addBookmark(bookmark);
      }
    }
  }

  @Nullable
  protected CommanderBookmark createCommanderBookmark(final PsiElement element, final String description) {
    if (element instanceof PsiDirectory) {
      return new CommanderBookmark(myProject, element, description);
    }
    return null;
  }

  @Nullable
  protected CommanderBookmark createCommanderBookmark(String type, String url, String description) {
    final CommanderBookmark commanderBookmark = new CommanderBookmark(myProject, type, url, description);
    if (commanderBookmark.getPsiElement() != null) return commanderBookmark;
    return null;
  }

  public List<EditorBookmark> getValidEditorBookmarks() {
    return myEditorBookmarks.getValidBookmarks();
  }

  public List<? extends Bookmark> getValidCommanderBookmarks() {
    return myCommanderBookmarks.getValidBookmarks();
  }

  public EditorBookmark findEditorBookmark(Document document, int lineIndex) {
    myEditorBookmarks.removeInvalidBookmarks();
    return myEditorBookmarks.findByDocumnetLine(document, lineIndex);
  }

  public EditorBookmark findNumberedEditorBookmark(int number) {
    myEditorBookmarks.removeInvalidBookmarks();
    return myEditorBookmarks.findByNumber(number);
  }

  public CommanderBookmark findCommanderBookmark(PsiElement element) {
    myCommanderBookmarks.removeInvalidBookmarks();
    return myCommanderBookmarks.findByPsiElement(element);
  }

  public void removeBookmark(Bookmark bookmark) {
    if (bookmark == null) return;
    selectBookmarksOfSameType(bookmark).removeBookmark(bookmark);
  }

  public void readExternal(Element element) throws InvalidDataException {
    for (final Object o : element.getChildren()) {
      Element bookmarkElement = (Element)o;

      if (BookmarksCollection.ForEditors.ELEMENT_NAME.equals(bookmarkElement.getName())) {
        myEditorBookmarks.readBookmark(bookmarkElement, myProject);
      }
      else if (BookmarksCollection.ForPsiElements.ELEMENT_NAME.equals(bookmarkElement.getName())) {
        myCommanderBookmarks.readBookmark(bookmarkElement, this);
      }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myEditorBookmarks.writeBookmarks(element);
    myCommanderBookmarks.writeBookmarks(element);
  }

  /**
   * Try to move bookmark one position up in the list
   *
   * @return bookmark list after moving
   */
  public List<Bookmark> moveBookmarkUp(Bookmark bookmark) {
    BookmarksCollection bookmarks = selectBookmarksOfSameType(bookmark);
    return bookmarks.moveUp(bookmark);
  }

  private BookmarksCollection selectBookmarksOfSameType(Bookmark bookmark) {
    if (bookmark instanceof EditorBookmark) return myEditorBookmarks;
    if (bookmark instanceof CommanderBookmark) return myCommanderBookmarks;
    if (bookmark == null) {
      LOG.error("null");
    }
    else {
      LOG.error(bookmark.getClass().getName());
    }
    return null;
  }

  /**
   * Try to move bookmark one position down in the list
   *
   * @return bookmark list after moving
   */
  public List<Bookmark> moveBookmarkDown(Bookmark bookmark) {
    BookmarksCollection bookmarks = selectBookmarksOfSameType(bookmark);
    return bookmarks.moveDown(bookmark);
  }

  public EditorBookmark getNextBookmark(Editor editor, boolean isWrapped) {
    EditorBookmark[] bookmarksForDocument = getBookmarksForDocument(editor.getDocument());
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    for (EditorBookmark bookmark : bookmarksForDocument) {
      if (bookmark.getLineIndex() > lineNumber) return bookmark;
    }
    if (isWrapped && bookmarksForDocument.length > 0) {
      return bookmarksForDocument[0];
    }
    return null;
  }

  public EditorBookmark getPreviousBookmark(Editor editor, boolean isWrapped) {
    EditorBookmark[] bookmarksForDocument = getBookmarksForDocument(editor.getDocument());
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    for (int i = bookmarksForDocument.length - 1; i >= 0; i--) {
      EditorBookmark bookmark = bookmarksForDocument[i];
      if (bookmark.getLineIndex() < lineNumber) return bookmark;
    }
    if (isWrapped && bookmarksForDocument.length > 0) {
      return bookmarksForDocument[bookmarksForDocument.length - 1];
    }
    return null;
  }

  private EditorBookmark[] getBookmarksForDocument(Document document) {
    ArrayList<EditorBookmark> bookmarksVector = new ArrayList<EditorBookmark>();
    List<EditorBookmark> validEditorBookmarks = getValidEditorBookmarks();
    for (EditorBookmark bookmark : validEditorBookmarks) {
      if (document.equals(bookmark.getDocument())) {
        bookmarksVector.add(bookmark);
      }
    }
    EditorBookmark[] bookmarks = bookmarksVector.toArray(new EditorBookmark[bookmarksVector.size()]);
    Arrays.sort(bookmarks, new Comparator<EditorBookmark>() {
      public int compare(final EditorBookmark o1, final EditorBookmark o2) {
        return o1.getLineIndex() - o2.getLineIndex();
      }
    });
    return bookmarks;
  }

  private class MyEditorMouseListener extends EditorMouseAdapter {
    public void mouseClicked(final EditorMouseEvent e) {
      if (e.getArea() != EditorMouseEventArea.LINE_MARKERS_AREA) return;
      if (e.getMouseEvent().isPopupTrigger()) return;
      if ((e.getMouseEvent().getModifiers() & InputEvent.CTRL_MASK) == 0) return;

      Editor editor = e.getEditor();
      int line = editor.xyToLogicalPosition(new Point(e.getMouseEvent().getX(), e.getMouseEvent().getY())).line;
      if (line < 0) return;

      Document document = editor.getDocument();

      EditorBookmark bookmark = findEditorBookmark(document, line);
      if (bookmark == null) {
        addEditorBookmark(editor, line, EditorBookmark.NOT_NUMBERED);
      }
      else {
        removeBookmark(bookmark);
      }
      e.consume();
    }
  }

  @NotNull
  public String getComponentName() {
    return "BookmarkManager";
  }
}

