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
package com.intellij.openapi.vcs;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;

/**
 * author: lesya
 */

@State(
  name = "VcsManagerConfiguration",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )
    }
)
public final class VcsConfiguration implements PersistentStateComponent<Element>, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.VcsConfiguration");

  @NonNls private static final String VALUE_ATTR = "value";
  private Project myProject;

  public boolean OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT = true;
  public boolean CHECK_CODE_SMELLS_BEFORE_PROJECT_COMMIT = true;
  public boolean PERFORM_UPDATE_IN_BACKGROUND = false;
  public boolean PERFORM_COMMIT_IN_BACKGROUND = false;
  public boolean PERFORM_EDIT_IN_BACKGROUND = true;
  public boolean PERFORM_ADD_REMOVE_IN_BACKGROUND = true;

  public enum StandardOption {
    ADD(VcsBundle.message("vcs.command.name.add")),
    REMOVE(VcsBundle.message("vcs.command.name.remove")),
    EDIT(VcsBundle.message("vcs.command.name.edit")),
    CHECKOUT(VcsBundle.message("vcs.command.name.checkout")),
    STATUS(VcsBundle.message("vcs.command.name.status")),
    UPDATE(VcsBundle.message("vcs.command.name.update"));

    StandardOption(final String id) {
      myId = id;
    }

    private final String myId;

    public String getId() {
      return myId;
    }
  }

  public enum StandardConfirmation {
    ADD(VcsBundle.message("vcs.command.name.add")),
    REMOVE(VcsBundle.message("vcs.command.name.remove"));

    StandardConfirmation(final String id) {
      myId = id;
    }

    private final String myId;

    public String getId() {
      return myId;
    }
  }

  public boolean FORCE_NON_EMPTY_COMMENT = false;

  private ArrayList<String> myLastCommitMessages = new ArrayList<String>();
  public String LAST_COMMIT_MESSAGE = null;

  public boolean OPTIMIZE_IMPORTS_BEFORE_PROJECT_COMMIT = false;

  public boolean REFORMAT_BEFORE_PROJECT_COMMIT = false;
  public boolean REFORMAT_BEFORE_FILE_COMMIT = false;

  public float FILE_HISTORY_DIALOG_COMMENTS_SPLITTER_PROPORTION = 0.8f;
  public float FILE_HISTORY_DIALOG_SPLITTER_PROPORTION = 0.5f;

  public boolean ERROR_OCCURED = false;

  public String ACTIVE_VCS_NAME;
  public boolean UPDATE_GROUP_BY_PACKAGES = false;
  public boolean SHOW_FILE_HISTORY_AS_TREE = false;
  public float FILE_HISTORY_SPLITTER_PROPORTION = 0.6f;
  private static final int MAX_STORED_MESSAGES = 25;
  @NonNls private static final String MESSAGE_ELEMENT_NAME = "MESSAGE";

  private final PerformInBackgroundOption myUpdateOption = new UpdateInBackgroundOption();
  private final PerformInBackgroundOption myCommitOption = new CommitInBackgroundOption();
  private final PerformInBackgroundOption myEditOption = new EditInBackgroundOption();
  private final PerformInBackgroundOption myAddRemoveOption = new AddRemoveInBackgroundOption();

  public static VcsConfiguration createEmptyConfiguration() {
    return new VcsConfiguration();
  }

  private VcsConfiguration() {
  }

  public VcsConfiguration(final Project project) {
    myProject = project;
  }

  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    final List messages = element.getChildren(MESSAGE_ELEMENT_NAME);
    for (final Object message : messages) {
      saveCommitMessage(((Element)message).getAttributeValue(VALUE_ATTR));
    }
    if (ACTIVE_VCS_NAME != null && ACTIVE_VCS_NAME.length() > 0) {
      ProjectLevelVcsManager.getInstance(myProject).setDirectoryMapping("", ACTIVE_VCS_NAME);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (String message : myLastCommitMessages) {
      final Element messageElement = new Element(MESSAGE_ELEMENT_NAME);
      messageElement.setAttribute(VALUE_ATTR, message);
      element.addContent(messageElement);
    }
  }

  public static VcsConfiguration getInstance(Project project) {
    return project.getComponent(VcsConfiguration.class);
  }

  public void projectOpened() {

  }

  public void projectClosed() {

  }

  public String getComponentName() {
    return "VcsManagerConfiguration";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }

  public void saveCommitMessage(final String comment) {

    LAST_COMMIT_MESSAGE = comment;

    if (comment == null || comment.length() == 0) return;

    myLastCommitMessages.remove(comment);

    while (myLastCommitMessages.size() >= MAX_STORED_MESSAGES) {
      myLastCommitMessages.remove(0);
    }

    myLastCommitMessages.add(comment);
  }

  /**
   * @deprecated Use {@link #getLastNonEmptyCommitMessage()} instead.
   */
  public String getLastCommitMessage() {
    return getLastNonEmptyCommitMessage();
  }

  public String getLastNonEmptyCommitMessage() {
    if (myLastCommitMessages.isEmpty()) {
      return null;
    }
    else {
      return myLastCommitMessages.get(myLastCommitMessages.size() - 1);
    }
  }

  public ArrayList<String> getRecentMessages() {
    return new ArrayList<String>(myLastCommitMessages);
  }

  public void removeMessage(final String content) {
    myLastCommitMessages.remove(content);
  }


  public PerformInBackgroundOption getUpdateOption() {
    return myUpdateOption;
  }

  public PerformInBackgroundOption getCommitOption() {
    return myCommitOption;
  }

  public PerformInBackgroundOption getEditOption() {
    return myEditOption;
  }

  public PerformInBackgroundOption getAddRemoveOption() {
    return myAddRemoveOption;
  }

  private class UpdateInBackgroundOption implements PerformInBackgroundOption {
    public boolean shouldStartInBackground() {
      return PERFORM_UPDATE_IN_BACKGROUND;
    }

    public void processSentToBackground() {}
    public void processRestoredToForeground() {}
  }

  private class CommitInBackgroundOption implements PerformInBackgroundOption {
    public boolean shouldStartInBackground() {
      return PERFORM_COMMIT_IN_BACKGROUND;
    }

    public void processSentToBackground() {}
    public void processRestoredToForeground() {}
  }

  private class EditInBackgroundOption implements PerformInBackgroundOption {
    public boolean shouldStartInBackground() {
      return PERFORM_EDIT_IN_BACKGROUND;
    }

    public void processSentToBackground() {
      PERFORM_EDIT_IN_BACKGROUND = true;
    }

    public void processRestoredToForeground() {
      PERFORM_EDIT_IN_BACKGROUND = false;
    }
  }

  private class AddRemoveInBackgroundOption implements PerformInBackgroundOption {
    public boolean shouldStartInBackground() {
      return PERFORM_ADD_REMOVE_IN_BACKGROUND;
    }

    public void processSentToBackground() {
      PERFORM_ADD_REMOVE_IN_BACKGROUND = true;
    }

    public void processRestoredToForeground() {
      PERFORM_ADD_REMOVE_IN_BACKGROUND = false;
    }
  }

}
