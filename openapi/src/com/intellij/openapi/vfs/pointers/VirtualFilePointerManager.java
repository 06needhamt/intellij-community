/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class VirtualFilePointerManager {
  public static VirtualFilePointerManager getInstance() {
    return ApplicationManager.getApplication().getComponent(VirtualFilePointerManager.class);
  }

  public abstract VirtualFilePointer create(String url, VirtualFilePointerListener listener);

  public abstract VirtualFilePointer create(VirtualFile file, VirtualFilePointerListener listener);

  public abstract VirtualFilePointer duplicate (VirtualFilePointer pointer, VirtualFilePointerListener listener);

  public abstract void kill(VirtualFilePointer pointer);

  public abstract VirtualFilePointerContainer createContainer();

  public abstract VirtualFilePointerContainer createContainer(VirtualFilePointerListener listener);

  public abstract VirtualFilePointerContainer createContainer(VirtualFilePointerFactory factory);
}
