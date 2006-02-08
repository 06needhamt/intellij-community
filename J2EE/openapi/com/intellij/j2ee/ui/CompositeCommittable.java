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
package com.intellij.j2ee.ui;

import com.intellij.j2ee.j2eeDom.xmlData.ReadOnlyDeploymentDescriptorModificationException;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */
public class CompositeCommittable implements Committable {
  private final List<Committable> myComponents = new ArrayList<Committable>();

  public final <T extends Committable> T addComponent(T panel) {
    myComponents.add(panel);
    return panel;
  }

  public void commit() throws ReadOnlyDeploymentDescriptorModificationException {
    for (final Committable committable : myComponents) {
      committable.commit();
    }
  }

  public void reset() {
    for (final Committable committable : myComponents) {
      committable.reset();
    }
  }

  public void dispose() {
    for (final Committable committable : myComponents) {
      committable.dispose();
    }
  }

  public List<Warning> getWarnings() {
    ArrayList<Warning> result = new ArrayList<Warning>();
    for (final Committable committable : myComponents) {
      List<Warning> warnings = committable.getWarnings();
      if (warnings != null) {
        result.addAll(warnings);
      }
    }
    return result;
  }
}
