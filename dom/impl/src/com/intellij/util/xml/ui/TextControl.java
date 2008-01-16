/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PlainTextTokenTypes;
import com.intellij.psi.impl.source.PsiCodeFragmentImpl;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.UIBundle;
import com.intellij.util.Function;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class TextControl extends EditorTextFieldControl<TextPanel> {

  public TextControl(final DomWrapper<String> domWrapper) {
    super(domWrapper);
  }

  public TextControl(final DomWrapper<String> domWrapper, final boolean commitOnEveryChange) {
    super(domWrapper, commitOnEveryChange);
  }

  protected EditorTextField getEditorTextField(@NotNull final TextPanel panel) {
    final Component component = panel.getComponent(0);
    if (component instanceof ReferenceEditorWithBrowseButton) {
      return ((ReferenceEditorWithBrowseButton)component).getEditorTextField();
    }
    return (EditorTextField)component;
  }

  protected TextPanel createMainComponent(TextPanel boundedComponent, final Project project) {
    if (boundedComponent == null) {
      boundedComponent = new TextPanel();
    }
    boundedComponent.removeAll();
    final Function<String, Document> factory = new Function<String, Document>() {
      public Document fun(final String s) {
        return PsiDocumentManager.getInstance(project).getDocument(new PsiCodeFragmentImpl(project, PlainTextTokenTypes.PLAIN_TEXT, true, "a.txt", s));
      }
    };
    final TextPanel boundedComponent1 = boundedComponent;
    final EditorTextField editorTextField = new EditorTextField(factory.fun(""), project, FileTypes.PLAIN_TEXT) {
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        return boundedComponent1 instanceof MultiLineTextPanel ? makeBigEditor(editor, ((MultiLineTextPanel)boundedComponent1).getRowCount()) : editor;
      }
    };

    if (boundedComponent instanceof BigTextPanel) {
      final ReferenceEditorWithBrowseButton editor = new ReferenceEditorWithBrowseButton(null, editorTextField, factory);
      boundedComponent.add(editor);
      editor.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          EditorTextField textArea = new EditorTextField(editorTextField.getDocument(), project, FileTypes.PLAIN_TEXT) {
            protected EditorEx createEditor() {
              final EditorEx editor = super.createEditor();
              editor.setEmbeddedIntoDialogWrapper(true);
              return makeBigEditor(editor, 5);
            }
          };
          DialogBuilder builder = new DialogBuilder(project);
          builder.setDimensionServiceKey("TextControl");
          builder.setCenterPanel(textArea);
          builder.setPreferedFocusComponent(textArea);
          builder.setTitle(UIBundle.message("big.text.control.window.title"));
          builder.addCloseButton();
          builder.show();
        }
      });
      return boundedComponent;
    }

    boundedComponent.add(editorTextField);
    return boundedComponent;
  }

  private static EditorEx makeBigEditor(final EditorEx editor, int rowCount) {
    editor.setVerticalScrollbarVisible(true);
    editor.setOneLineMode(false);
    final JTextArea area = new JTextArea(10, 50);
    area.setRows(rowCount);
    editor.getComponent().setPreferredSize(area.getPreferredSize());
    return editor;
  }


}
