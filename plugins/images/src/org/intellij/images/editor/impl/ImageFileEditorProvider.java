/*
 * Copyright 2004-2005 Alexey Efimov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.images.editor.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jdom.Element;

/**
 * Image editor provider.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileEditorProvider implements ApplicationComponent, FileEditorProvider {
    private static final String NAME = "ImageEditorProvider";
    private static final String EDITOR_TYPE_ID = "images";

    private final ImageFileTypeManager typeManager;

    ImageFileEditorProvider(ImageFileTypeManager typeManager) {
        this.typeManager = typeManager;
    }

    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    public boolean accept(Project project, VirtualFile file) {
        return typeManager.isImage(file);
    }

    public FileEditor createEditor(Project project, VirtualFile file) {
        return new ImageFileEditorImpl(project, file);
    }

    public void disposeEditor(FileEditor editor) {
        ImageFileEditorImpl fileEditor = (ImageFileEditorImpl)editor;
        fileEditor.dispose();
    }

    public FileEditorState readState(Element sourceElement, Project project, VirtualFile file) {
        return new FileEditorState() {
            public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
                return false;
            }
        };
    }

    public void writeState(FileEditorState state, Project project, Element targetElement) {
    }

    public String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    public FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
