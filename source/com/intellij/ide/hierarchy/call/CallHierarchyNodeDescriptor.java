package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.pom.Navigatable;
import com.intellij.codeInsight.highlighting.HighlightManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

public final class CallHierarchyNodeDescriptor extends HierarchyNodeDescriptor implements Navigatable {
  private int myUsageCount = 1;
  private final static Class[] ourEnclosingElementClasses = new Class[]{PsiMethod.class, PsiClass.class, JspFile.class};
  private ArrayList<PsiReference> myReferences = new ArrayList<PsiReference>();

  public CallHierarchyNodeDescriptor(
    final Project project,
    final HierarchyNodeDescriptor parentDescriptor,
    final PsiElement element,
    final boolean isBase
  ){
    super(project, parentDescriptor, element, isBase);
  }

  /**
   * @return PsiMethod or PsiClass or JspFile
   */
  public final PsiElement getEnclosingElement(){
    return getEnclosingElement(myElement);
  }

  static PsiElement getEnclosingElement(final PsiElement element){
    return PsiTreeUtil.getParentOfType(element, ourEnclosingElementClasses, false);
  }

  public final void incrementUsageCount(){
    myUsageCount++;
  }

  /**
   * Element for OpenFileDescriptor
   */
  public final PsiElement getTargetElement(){
    return myElement;
  }

  public final boolean isValid(){
    final PsiElement element = getEnclosingElement();
    return element != null && element.isValid();
  }

  public final boolean update(){
    final CompositeAppearance oldText = myHighlightedText;
    final Icon oldOpenIcon = myOpenIcon;

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    boolean changes = super.update();

    final PsiElement enclosingElement = getEnclosingElement();

    if (enclosingElement == null) {
      final String invalidPrefix = "[Invalid] ";
      if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
        myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
      }
      return true;
    }

    myOpenIcon = enclosingElement.getIcon(flags);
    if (changes && myIsBase) {
      final LayeredIcon icon = new LayeredIcon(2);
      icon.setIcon(myOpenIcon, 0);
      icon.setIcon(BASE_POINTER_ICON, 1, -BASE_POINTER_ICON.getIconWidth() / 2, 0);
      myOpenIcon = icon;
    }
    myClosedIcon = myOpenIcon;

    myHighlightedText = new CompositeAppearance();
    TextAttributes mainTextAttributes = null;
    if (myColor != null) {
      mainTextAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
    }
    if (enclosingElement instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)enclosingElement;
      final StringBuffer buffer = new StringBuffer(128);
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
        buffer.append('.');
      }
      final String methodText = PsiFormatUtil.formatMethod(
        method,
        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
        PsiFormatUtil.SHOW_TYPE
      );
      buffer.append(methodText);

      myHighlightedText.getEnding().addText(buffer.toString(), mainTextAttributes);
    }
    else if (enclosingElement instanceof JspFile) {
      final JspFile file = (JspFile)enclosingElement;
      myHighlightedText.getEnding().addText(file.getName(), mainTextAttributes);
    }
    else {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass((PsiClass)enclosingElement, false), mainTextAttributes);
    }
    if (myUsageCount > 1) {
      myHighlightedText.getEnding().addText("  (" + myUsageCount + " usages)", HierarchyNodeDescriptor.getUsageCountPrefixAttributes());
    }
    if (!(enclosingElement instanceof JspFile)) {
      final String packageName = getPackageName(enclosingElement instanceof PsiMethod ? ((PsiMethod)enclosingElement).getContainingClass() : (PsiClass)enclosingElement);
      myHighlightedText.getEnding().addText("  (" + packageName + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
    }
    myName = myHighlightedText.getText();

    if (
      !Comparing.equal(myHighlightedText, oldText) ||
      !Comparing.equal(myOpenIcon, oldOpenIcon)
    ){
      changes = true;
    }
    return changes;
  }

  public void addReference(final PsiReference reference) {
    myReferences.add(reference);
  }

  public void navigate(boolean requestFocus) {
    final PsiReference firstReference = myReferences.get(0);
    final PsiElement callElement = firstReference.getElement().getParent();
    if (callElement instanceof Navigatable) {
      ((Navigatable)callElement).navigate(requestFocus);
    } else {
      FileEditorManager.getInstance(myProject).openFile(callElement.getContainingFile().getVirtualFile(), requestFocus);
    }

    Editor editor = getEditor(callElement);

    if (editor != null) {

      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      EditorColorsManager colorManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
      for (Iterator<PsiReference> iterator = myReferences.iterator(); iterator.hasNext();) {
        PsiReference psiReference = iterator.next();
        final PsiElement eachMethidCall = psiReference.getElement().getParent();
        final TextRange textRange = eachMethidCall.getTextRange();
        highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, false, highlighters);
      }
    }
  }

  private Editor getEditor(final PsiElement callElement) {
    final FileEditor editor = FileEditorManager.getInstance(myProject).getSelectedEditor(callElement.getContainingFile().getVirtualFile());
    if (editor instanceof TextEditor) {
      return ((TextEditor)editor).getEditor();
    } else {
      return null;
    }
  }

  public boolean canNavigate() {
    return !myReferences.isEmpty();
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }
}
