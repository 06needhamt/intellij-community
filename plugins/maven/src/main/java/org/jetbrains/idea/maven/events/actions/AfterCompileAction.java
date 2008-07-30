package org.jetbrains.idea.maven.events.actions;

import org.jetbrains.idea.maven.events.MavenEventsManager;
import org.jetbrains.idea.maven.events.MavenTask;

import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public class AfterCompileAction extends IncludeExcludeTaskAction {
  protected Collection<MavenTask> getCollection(MavenEventsManager eventsHandler) {
    return eventsHandler.getState().afterCompile;
  }
}