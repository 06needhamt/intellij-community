// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.stash.GitChangesSaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class GitRebaseUtils {
  private final static Logger LOG = Logger.getInstance(GitRebaseUtils.class.getName());

  private GitRebaseUtils() {
  }

  public static void rebase(@NotNull final Project project,
                            @NotNull final List<? extends GitRepository> repositories,
                            @NotNull final GitRebaseParams params,
                            @NotNull final ProgressIndicator indicator) {
    if (!isRebaseAllowed(project, repositories)) return;  // TODO maybe move to the outside
    new GitRebaseProcess(project, GitRebaseSpec.forNewRebase(project, params, repositories, indicator), null).rebase();
  }

  public static void continueRebase(@NotNull Project project) {
    GitRebaseSpec spec = GitUtil.getRepositoryManager(project).getOngoingRebaseSpec();
    if (spec != null) {
      new GitRebaseProcess(project, spec, GitRebaseResumeMode.CONTINUE).rebase();
    }
    else {
      LOG.warn("Refusing to continue: no rebase spec");
      VcsNotifier.getInstance(project).notifyError("Can't Continue Rebase", "No rebase in progress");
    }
  }

  public static void continueRebase(@NotNull Project project, @NotNull GitRepository repository, @NotNull ProgressIndicator indicator) {
    GitRebaseSpec spec = GitRebaseSpec.forResumeInSingleRepository(project, repository, indicator);
    if (spec != null) {
      new GitRebaseProcess(project, spec, GitRebaseResumeMode.CONTINUE).rebase();
    }
    else {
      LOG.warn("Refusing to continue: no rebase spec");
      VcsNotifier.getInstance(project).notifyError("Can't Continue Rebase", "No rebase in progress");
    }
  }

  public static void skipRebase(@NotNull Project project) {
    GitRebaseSpec spec = GitUtil.getRepositoryManager(project).getOngoingRebaseSpec();
    if (spec != null) {
      new GitRebaseProcess(project, spec, GitRebaseResumeMode.SKIP).rebase();
    }
    else {
      LOG.warn("Refusing to skip: no rebase spec");
      VcsNotifier.getInstance(project).notifyError("Can't Continue Rebase", "No rebase in progress");
    }
  }

  public static void skipRebase(@NotNull Project project, @NotNull GitRepository repository, @NotNull ProgressIndicator indicator) {
    GitRebaseSpec spec = GitRebaseSpec.forResumeInSingleRepository(project, repository, indicator);
    if (spec != null) {
      new GitRebaseProcess(project, spec, GitRebaseResumeMode.SKIP).rebase();
    }
    else {
      LOG.warn("Refusing to skip: no rebase spec");
      VcsNotifier.getInstance(project).notifyError("Can't Continue Rebase", "No rebase in progress");
    }
  }

  /**
   * Automatically detects the ongoing rebase process in the project and abort it.
   * Optionally rollbacks repositories which were already rebased during that detected multi-root rebase process.
   * <p/>
   * Does nothing if no information about ongoing rebase is available, or if this information has become obsolete.
   */
  public static void abort(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    GitRebaseSpec spec = GitUtil.getRepositoryManager(project).getOngoingRebaseSpec();
    if (spec != null) {
      new GitAbortRebaseProcess(project, spec.getOngoingRebase(), spec.getHeadPositionsToRollback(), spec.getInitialBranchNames(),
                                indicator, spec.getSaver(), true).abortWithConfirmation();
    }
    else {
      LOG.warn("Refusing to abort: no rebase spec");
      VcsNotifier.getInstance(project).notifyError("Can't Abort Rebase", "No rebase in progress");
    }
  }

  /**
   * Abort the ongoing rebase process in the given repository.
   */
  public static void abort(@NotNull final Project project, @Nullable final GitRepository repository, @NotNull ProgressIndicator indicator) {
    new GitAbortRebaseProcess(project, repository, Collections.emptyMap(),
                              Collections.emptyMap(), indicator, null, true).abortWithConfirmation();
  }

  private static boolean isRebaseAllowed(@NotNull Project project, @NotNull Collection<? extends GitRepository> repositories) {
    // TODO links to 'rebase', 'resolve conflicts', etc.
    for (GitRepository repository : repositories) {
      Repository.State state = repository.getState();
      String in = GitUtil.mention(repository);
      String message = null;
      switch (state) {
        case NORMAL:
          if (repository.isFresh()) {
            message = "Repository" + in + " is empty.";
          }
          break;
        case MERGING:
          message = "There is an unfinished merge process" + in + ".<br/>You should complete the merge before starting a rebase";
          break;
        case REBASING:
          message = "There is an unfinished rebase process" + in + ".<br/>You should complete it before starting another rebase";
          break;
        case GRAFTING:
          message = "There is an unfinished cherry-pick process" + in + ".<br/>You should finish it before starting a rebase.";
          break;
        case REVERTING:
          message = "There is an unfinished revert process" + in + ".<br/>You should finish it before starting a rebase.";
          break;
        case DETACHED:
          message = "You are in the detached HEAD state" + in + ".<br/>Rebase is not possible.";
          break;
        default:
          LOG.error("Unknown state [" + state.name() + "]");
          message = "Rebase is not possible" + in;
      }
      if (message != null) {
        VcsNotifier.getInstance(project).notifyError("Rebase not Allowed", message);
        return false;
      }
    }
    return true;
  }

  /**
   * @deprecated Use {@link GitRepository#isRebaseInProgress()}.
   */
  @Deprecated
  public static boolean isRebaseInTheProgress(@NotNull Project project, @NotNull VirtualFile root) {
    return getRebaseDir(project, root) != null;
  }

  @Nullable
  public static File getRebaseDir(@NotNull Project project, @NotNull VirtualFile root) {
    GitRepository repository = Objects.requireNonNull(GitUtil.getRepositoryManager(project).getRepositoryForRootQuick(root));
    File f = repository.getRepositoryFiles().getRebaseApplyDir();
    if (f.exists()) {
      return f;
    }
    f = repository.getRepositoryFiles().getRebaseMergeDir();
    if (f.exists()) {
      return f;
    }
    return null;
  }

  public static boolean isInteractiveRebaseInProgress(@NotNull GitRepository repository) {
    File rebaseDir = getRebaseDir(repository.getProject(), repository.getRoot());
    return rebaseDir != null && new File(rebaseDir, "interactive").exists();
  }

  /**
   * Get rebase directory
   *
   *
   * @param project
   * @param root the vcs root
   * @return the commit information or null if no commit information could be detected
   */
  @Nullable
  public static CommitInfo getCurrentRebaseCommit(@NotNull Project project, @NotNull VirtualFile root) {
    File rebaseDir = getRebaseDir(project, root);
    if (rebaseDir == null) {
      LOG.warn("No rebase dir found for " + root.getPath());
      return null;
    }
    File nextFile = new File(rebaseDir, "next");
    int next;
    try {
      next = Integer.parseInt(FileUtil.loadFile(nextFile, StandardCharsets.UTF_8).trim());
    }
    catch (Exception e) {
      LOG.warn("Failed to load next commit number from file " + nextFile.getPath(), e);
      return null;
    }
    File commitFile = new File(rebaseDir, String.format("%04d", next));
    String hash = null;
    String subject = null;
    try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(commitFile), StandardCharsets.UTF_8))) {
      String line;
      while ((line = in.readLine()) != null) {
        if (line.startsWith("From ")) {
          hash = line.substring(5, 5 + 40);
        }
        if (line.startsWith("Subject: ")) {
          subject = line.substring("Subject: ".length());
        }
        if (hash != null && subject != null) {
          break;
        }
      }
    }
    catch (Exception e) {
      LOG.warn("Failed to load next commit number from file " + commitFile, e);
      return null;
    }
    if (subject == null || hash == null) {
      LOG.info("Unable to extract information from " + commitFile + " " + hash + ": " + subject);
      return null;
    }
    return new CommitInfo(new GitRevisionNumber(hash), subject);
  }

  @NotNull
  static String mentionLocalChangesRemainingInStash(@Nullable GitChangesSaver saver) {
    return saver != null && saver.wereChangesSaved() ?
           "<br/>Local changes were " + saver.getSaveMethod().getVerbInPast() + " before rebase." :
           "";
  }

  @NotNull
  public static Collection<GitRepository> getRebasingRepositories(@NotNull Project project) {
    return GitUtil.getRepositoriesInState(project, Repository.State.REBASING);
  }

  public static int getNumberOfCommitsToRebase(@NotNull GitRepository repository, @NotNull String upstream, @Nullable String branch)
    throws VcsException {

    String rebasingBranch = branch;
    if (rebasingBranch == null) {
      if (repository.isRebaseInProgress()) {
        rebasingBranch = getRebasingBranchHash(repository).asString();
      }
      else {
        rebasingBranch = GitUtil.HEAD;
      }
    }

    return GitHistoryUtils.collectTimedCommits(
      repository.getProject(),
      repository.getRoot(),
      upstream + ".." + rebasingBranch
    ).size();
  }

  @NotNull
  private static Hash getRebasingBranchHash(@NotNull GitRepository repository) throws VcsException {
    return readHashFromFile(repository.getProject(), repository.getRoot(), "orig-head");
  }

  @Nullable
  public static Hash getOntoHash(@NotNull Project project, @NotNull VirtualFile root) {
    try {
      return readHashFromFile(project, root, "onto");
    }
    catch (VcsException e) {
      return null;
    }
  }

  @NotNull
  private static Hash readHashFromFile(@NotNull Project project, @NotNull VirtualFile root, @NotNull String fileName) throws VcsException {
    try {
      return HashImpl.build(FileUtil.loadFile(new File(getRebaseDir(project, root), fileName)).trim());
    }
    catch (IOException e) {
      throw new VcsException("Couldn't resolve " + fileName, e);
    }
  }

  /**
   * Short commit info
   */
  public static class CommitInfo {
    /**
     * The commit hash
     */
    public final GitRevisionNumber revision;
    /**
     * The commit subject
     */
    public final String subject;

    /**
     * The constructor
     *
     * @param revision
     * @param subject the commit subject
     */
    public CommitInfo(GitRevisionNumber revision, String subject) {
      this.revision = revision;
      this.subject = subject;
    }

    @Override
    public String toString() {
      return revision.toString();
    }
  }
}
