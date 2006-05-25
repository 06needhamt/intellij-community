/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
 *
 */

package com.intellij.ide.favoritesTreeView;

import org.jetbrains.annotations.Nullable;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;

/**
 * Returns the nodes which should be added to the Favorites for the given data context.
 * Implementations of this class must be registered as application components.
 *
 * @author yole
 */
public interface FavoriteNodeProvider {
  @Nullable
  Collection<AbstractTreeNode> getFavoriteNodes(DataContext context, final ViewSettings viewSettings);

  /**
   * Checks if the specified project view node element (the value of {@link AbstractTreeNode}) contains
   * the specified virtual file as one of its children.
   *
   * @param element the value element of a project view node.
   * @param vFile   the file to check.
   * @return true if the file is contained, false if not or if <code>element</code> is not an element supported by this provider.
   */
  boolean elementContainsFile(final Object element, final VirtualFile vFile);
}
