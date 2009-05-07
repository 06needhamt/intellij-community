package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.impl.commands.FinalizableCommand;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CommandProcessor implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.CommandProcessor");
  private final Object myLock = new Object();

  private final List<CommandGroup> myCommandGroupList = new ArrayList<CommandGroup>();
  private int myCommandCount;

  public final int getCommandCount() {
    synchronized (myLock) {
      return myCommandCount;
    }
  }

  /**
   * Executes passed batch of commands. Note, that the processor surround the
   * commands with BlockFocusEventsCmd - UnbockFocusEventsCmd. It's required to
   * prevent focus handling of events which is caused by the commands to be executed.
   */
  public final void execute(final List<FinalizableCommand> commandList, Condition expired) {
    synchronized (myLock) {
      final boolean isBusy = myCommandCount > 0;

      final CommandGroup commandGroup = new CommandGroup(commandList, expired);
      myCommandGroupList.add(commandGroup);
      myCommandCount += commandList.size();

      if (!isBusy) {
        run();
      }
    }
  }

  public final void run() {
    synchronized (myLock) {
      final CommandGroup commandGroup = getNextCommandGroup();
      if (commandGroup == null) {
        return;
      }

      final Condition conditionForGroup = commandGroup.getExpireCondition();

      if (!commandGroup.isEmpty()) {
        final FinalizableCommand command = commandGroup.takeNextCommand();
        myCommandCount--;

        final Condition expire = command.getExpireCondition() != null ? command.getExpireCondition() : conditionForGroup;

        if (LOG.isDebugEnabled()) {
          LOG.debug("CommandProcessor.run " + command);
        }
        // max. I'm not actually quite sure this should have NON_MODAL modality but it should
        // definitely have some since runnables in command list may (and do) request some PSI activity
        final boolean queueNext = myCommandCount > 0;
        if (queueNext) {
        ApplicationManager.getApplication().getInvokator().invokeLater(command, ModalityState.NON_MODAL, expire == null ? Condition.FALSE : expire).doWhenDone(new Runnable() {
          public void run() {
            CommandProcessor.this.run();
          }
        });
        }
      }
    }
  }

  @Nullable
  private CommandGroup getNextCommandGroup() {
    while (!myCommandGroupList.isEmpty()) {
      final CommandGroup candidate = myCommandGroupList.get(0);
      if (!candidate.isEmpty()) {
        return candidate;
      }
      myCommandGroupList.remove(candidate);
    }

    return null;
  }

  private static class CommandGroup {
    private final List<FinalizableCommand> myList;
    private final Condition myExpireCondition;

    private CommandGroup(final List<FinalizableCommand> list, final Condition expireCondition) {
      myList = list;
      myExpireCondition = expireCondition;
    }

    public Condition getExpireCondition() {
      return myExpireCondition;
    }

    public boolean isEmpty() {
      return myList.isEmpty();
    }

    public FinalizableCommand takeNextCommand() {
      return myList.remove(0);
    }
  }
}
