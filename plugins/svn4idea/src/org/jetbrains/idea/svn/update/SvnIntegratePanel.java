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
package org.jetbrains.idea.svn.update;

import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.options.ConfigurationException;

import java.util.Collection;

public class SvnIntegratePanel extends AbstractSvnUpdatePanel{

  private JCheckBox myDryRunCheckbox;
  private JCheckBox myStatusBox;
  private JCheckBox myRecursiveBox;
  private JPanel myRootOptionsPanel;
  private JPanel myPanel;

  public SvnIntegratePanel(final SvnVcs vcs, Collection<FilePath> roots) {
    super(vcs);
    init(roots);
  }

  protected SvnPanel createRootPanel(final FilePath root, final SvnVcs p1) {
    return new SvnIntegrateRootOptionsPanel(myVCS, root);
  }

  protected JPanel getRootsPanel() {
    return myRootOptionsPanel;
  }

  public void reset(final SvnConfiguration configuration) {
    super.reset(configuration);
    myDryRunCheckbox.setSelected(configuration.MERGE_DRY_RUN);

  }
  public void apply(final SvnConfiguration configuration) throws ConfigurationException {
    super.apply(configuration);
    configuration.MERGE_DRY_RUN = myDryRunCheckbox.isSelected();
  }

  protected JComponent getPanel() {
    return myPanel;
  }

  protected JCheckBox getStatusBox() {
    return myStatusBox;
  }

  protected JCheckBox getRecursiveBox() {
    return myRecursiveBox;
  }
}
