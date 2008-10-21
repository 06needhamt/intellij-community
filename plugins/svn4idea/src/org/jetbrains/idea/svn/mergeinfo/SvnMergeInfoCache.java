package org.jetbrains.idea.svn.mergeinfo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Consumer;
import com.intellij.util.containers.SoftHashMap;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.WCInfoWithBranches;
import org.jetbrains.idea.svn.dialogs.WCPaths;
import org.jetbrains.idea.svn.history.FirstInBranch;
import org.jetbrains.idea.svn.history.SvnChangeList;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNMergeInfoUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.*;

public class SvnMergeInfoCache {
  private final static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.mergeinfo.SvnMergeInfoCache");

  private final Project myProject;
  private MyState myState;
  private SVNWCClient myClient;

  public static Topic<SvnMergeInfoCacheListener> SVN_MERGE_INFO_CACHE = new Topic<SvnMergeInfoCacheListener>("SVN_MERGE_INFO_CACHE",
                                                                                                 SvnMergeInfoCacheListener.class);

  private SvnMergeInfoCache(final Project project) {
    myProject = project;
    myState = new MyState();
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    myClient = vcs.createWCClient();
    myClient.setOptions(new DefaultSVNOptions() {
      @Override
      public byte[] getNativeEOL() {
        return new byte[]{'\n'};
      }
    });
  }

  public static SvnMergeInfoCache getInstance(final Project project) {
    return ServiceManager.getService(project, SvnMergeInfoCache.class);
  }

  public void clear(final WCPaths info, final WCInfoWithBranches.Branch selectedBranch) {
    final String currentUrl = info.getRootUrl();
    final String branchUrl = selectedBranch.getUrl();

    final MyCurrentUrlData rootMapping = myState.getCurrentUrlMapping().get(currentUrl);
    if (rootMapping != null) {
      final BranchInfo branchInfo = rootMapping.getBranchInfo(branchUrl);
      if (branchInfo != null) {
        branchInfo.clear();
      }
    }
  }

  public void removeList(final WCPaths info, final WCInfoWithBranches.Branch selectedBranch, final long listNumber) {
    final String currentUrl = info.getRootUrl();
    final String branchUrl = selectedBranch.getUrl();

    final MyCurrentUrlData rootMapping = myState.getCurrentUrlMapping().get(currentUrl);
    if (rootMapping != null) {
      final BranchInfo branchInfo = rootMapping.getBranchInfo(branchUrl);
      if (branchInfo != null) {
        branchInfo.halfClear(listNumber);
      }
    }
  }

  @Nullable
  public MergeinfoCached getCachedState(final WCPaths info, final WCInfoWithBranches.Branch selectedBranch) {
    final String currentUrl = info.getRootUrl();
    final String branchUrl = selectedBranch.getUrl();

    MyCurrentUrlData rootMapping = myState.getCurrentUrlMapping().get(currentUrl);
    if (rootMapping != null) {
      final BranchInfo branchInfo = rootMapping.getBranchInfo(branchUrl);
      if (branchInfo != null) {
        return branchInfo.getCached();
      }
    }
    return null;
  }

  // only refresh might have changed; for branches/roots change, another method is used
  public MergeCheckResult getState(final WCInfoWithBranches info, final SvnChangeList list, final WCInfoWithBranches.Branch selectedBranch) {
    return getState(info, list, selectedBranch, null);
  }

  // only refresh might have changed; for branches/roots change, another method is used
  public MergeCheckResult getState(final WCInfoWithBranches info, final SvnChangeList list, final WCInfoWithBranches.Branch selectedBranch,
                                   final String branchPath) {
    final String currentUrl = info.getRootUrl();
    final String branchUrl = selectedBranch.getUrl();

    MyCurrentUrlData rootMapping = myState.getCurrentUrlMapping().get(currentUrl);
    BranchInfo branchInfo = null;
    if (rootMapping == null) {
      rootMapping = new MyCurrentUrlData();
      myState.getCurrentUrlMapping().put(currentUrl, rootMapping);
    } else {
      branchInfo = rootMapping.getBranchInfo(branchUrl);
    }
    if (branchInfo == null) {
      branchInfo = new BranchInfo(SvnVcs.getInstance(myProject), info.getRepoUrl(), branchUrl, currentUrl, info.getTrunkRoot(), myClient);
      rootMapping.addBranchInfo(branchUrl, branchInfo);
    }

    return branchInfo.checkList(list, branchPath);
  }

  private static class MyState {
    private Map<String, MyCurrentUrlData> myCurrentUrlMapping;

    private MyState() {
      myCurrentUrlMapping = new HashMap<String, MyCurrentUrlData>();
    }

    public Map<String, MyCurrentUrlData> getCurrentUrlMapping() {
      return myCurrentUrlMapping;
    }

    public void setCurrentUrlMapping(final Map<String, MyCurrentUrlData> currentUrlMapping) {
      myCurrentUrlMapping = currentUrlMapping;
    }
  }

  public static enum MergeCheckResult {
    COMMON,
    MERGED,
    NOT_MERGED,
    NOT_EXISTS,
    NOT_EXISTS_PARTLY_MERGED;

    public static MergeCheckResult getInstance(final boolean merged) {
      // not exists assumed to be already checked
      if (merged) {
        return MERGED;
      }
      return NOT_MERGED;
    }
  }

  private static class CopyRevison {
    private final String myPath;
    private volatile long myRevision;

    private CopyRevison(final SvnVcs vcs, final String path, final String repositoryRoot, final String branchUrl, final String trunkUrl) {
      myPath = path;
      myRevision = -1;

      ApplicationManager.getApplication().executeOnPooledThread(new FirstInBranch(vcs, repositoryRoot, branchUrl, trunkUrl,
                                                                                  new Consumer<Long>() {
                                                                                    public void consume(final Long aLong) {
                                                                                      myRevision = aLong;
                                                                                      if (myRevision != -1) {
                                                                                        ApplicationManager.getApplication().invokeLater(new Runnable() {
                                                                                          public void run() {
                                                                                            if (vcs.getProject().isDisposed()) return;
                                                                                            vcs.getProject().getMessageBus().syncPublisher(SVN_MERGE_INFO_CACHE).copyRevisionUpdated();
                                                                                          }
                                                                                        });
                                                                                      }
                                                                                    }
                                                                                  }));
    }

    public String getPath() {
      return myPath;
    }

    public long getRevision() {
      return myRevision;
    }
  }

  //FirstInBranch
  private static class BranchInfo {
    // repo path in branch -> merged revisions
    private final Map<String, Set<Long>> myPathMergedMap;

    // to do not check again and again, what had been deleted
    private final Set<String> myNonExistingPaths;
    
    // revision in trunk -> whether merged into branch
    private final Map<Long, MergeCheckResult> myAlreadyCalculatedMap;
    private final Object myCalculatedLock = new Object();

    private final String myRepositoryRoot;
    private final String myBranchUrl;
    private final String myTrunkUrl;
    private final String myTrunkCorrected;
    private final String myRelativeTrunk;
    private final SVNWCClient myClient;
    private final SvnVcs myVcs;

    private CopyRevison myCopyRevison;

    private BranchInfo(final SvnVcs vcs, final String repositoryRoot, final String branchUrl, final String trunkUrl,
                       final String trunkCorrected, final SVNWCClient client) {
      myVcs = vcs;
      myRepositoryRoot = repositoryRoot;
      myBranchUrl = branchUrl;
      myTrunkUrl = trunkUrl;
      myTrunkCorrected = trunkCorrected;
      myClient = client;
      myRelativeTrunk = myTrunkUrl.substring(myRepositoryRoot.length());

      myPathMergedMap = new HashMap<String, Set<Long>>();
      myNonExistingPaths = new HashSet<String>();
      myAlreadyCalculatedMap = new HashMap<Long, MergeCheckResult>();
    }

    private long calculateCopyRevision(final String branchPath) {
      if (myCopyRevison != null && Comparing.equal(myCopyRevison.getPath(), branchPath)) {
        return myCopyRevison.getRevision();
      }
      myCopyRevison = new CopyRevison(myVcs, branchPath, myRepositoryRoot, myBranchUrl, myTrunkUrl);
      return -1;
    }

    public void clear() {
      myPathMergedMap.clear();
      synchronized (myCalculatedLock) {
        myAlreadyCalculatedMap.clear();
      }
      myNonExistingPaths.clear();
    }

    public void halfClear(final long listNumber) {
      myPathMergedMap.clear();
      synchronized (myCalculatedLock) {
        myAlreadyCalculatedMap.remove(listNumber);
      }
      myNonExistingPaths.clear();
    }

    public MergeinfoCached getCached() {
      synchronized (myCalculatedLock) {
        final long revision;
        if (myCopyRevison != null && myCopyRevison.getRevision() != -1) {
          revision = myCopyRevison.getRevision();
        } else {
          revision = -1;
        }
        return new MergeinfoCached(Collections.unmodifiableMap(myAlreadyCalculatedMap), revision);
      }
    }

    public MergeCheckResult checkList(final SvnChangeList list, final String branchPath) {
      synchronized (myCalculatedLock) {
        final long revision = calculateCopyRevision(branchPath);
        if (revision != -1 && revision >= list.getNumber()) {
          return MergeCheckResult.COMMON;
        }

        final MergeCheckResult calculated = myAlreadyCalculatedMap.get(list.getNumber());
        if (calculated != null) {
          return calculated;
        }

        final Ref<Boolean> mergedRef = new Ref<Boolean>();
        final Ref<Boolean> notExistsRef = new Ref<Boolean>();

        checkPaths(list.getNumber(), list.getAddedPaths(), mergedRef, notExistsRef, branchPath);
        checkPaths(list.getNumber(), list.getDeletedPaths(), mergedRef, notExistsRef, branchPath);
        checkPaths(list.getNumber(), list.getChangedPaths(), mergedRef, notExistsRef, branchPath);

        final MergeCheckResult result;
        boolean mergedDetected = Boolean.TRUE.equals(mergedRef.get());
        boolean notExistsDetected = Boolean.TRUE.equals(notExistsRef.get());
        if (notExistsDetected && (! mergedDetected)) {
          // +- (possibly ++ one case can be shown)
          result = MergeCheckResult.NOT_EXISTS;
        } else if (notExistsDetected) {
          result = MergeCheckResult.NOT_EXISTS_PARTLY_MERGED;
        } else {
          result = MergeCheckResult.getInstance(mergedDetected);
        }
        myAlreadyCalculatedMap.put(list.getNumber(), result);
        return result;
      }
    }

    private void checkPaths(final long number, final Collection<String> paths, final Ref<Boolean> mergedRef, final Ref<Boolean> notExistsRef,
                            final String branchPath) {
      for (String path : paths) {
        final String absoluteInTrunkPath = SVNPathUtil.append(myRepositoryRoot, path);
        if (! absoluteInTrunkPath.startsWith(myTrunkCorrected)) {
          notExistsRef.set(Boolean.TRUE); /// ?
          return;
        }
        final String relativeToTrunkPath = absoluteInTrunkPath.substring(myTrunkCorrected.length());

        final MergeCheckResult pathResult = checkOnePathLocally(number, branchPath, relativeToTrunkPath, path);
        if (MergeCheckResult.MERGED.equals(pathResult)) {
          mergedRef.set(Boolean.TRUE);
        } else if (MergeCheckResult.NOT_EXISTS.equals(pathResult)) {
          notExistsRef.set(Boolean.TRUE);
        }
      }
    }

    private MergeCheckResult checkOnePathLocally(final long number, final String head, final String tail, final String trunkRelativeUrl) {
      if ((myNonExistingPaths.contains(head)) || (head.length() == 0)) {
        return MergeCheckResult.NOT_EXISTS;
      }
      final Set<Long> mergeInfo = myPathMergedMap.get(head);
      if (mergeInfo != null) {
        if (mergeInfo.contains(number)) {
          return MergeCheckResult.getInstance(mergeInfo.contains(number));
        }
        return goDownLocally(number, head, tail, trunkRelativeUrl);
      }

      LOG.debug("checking " + head + " tail: " + tail);

      // go and get manually
      try {
        final SVNPropertyData mergeinfoProperty =
            myClient.doGetProperty(new File(head), SVNProperty.MERGE_INFO, SVNRevision.WORKING, SVNRevision.WORKING);
        boolean propertyFound = false;
        if (mergeinfoProperty != null) {
          final SVNPropertyValue value = mergeinfoProperty.getValue();
          if (value != null) {
            final Map<String, SVNMergeRangeList> map = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(value.getString()), null);
            for (String key : map.keySet()) {
              if ((key != null) && (trunkRelativeUrl.startsWith(key))) {
                propertyFound = true;
                final Set<Long> revisions = new HashSet<Long>();
                final SVNMergeRangeList rangesList = map.get(key);
                boolean result = false;
                for (SVNMergeRange range : rangesList.getRanges()) {
                  // SVN does not include start revision in range
                  final long startRevision = range.getStartRevision() + 1;
                  final long endRevision = range.getEndRevision();
                  if ((number >= startRevision) && (number <= endRevision)) {
                    result = true;
                  }
                  for (long i = startRevision; i <= endRevision; i++) {
                    revisions.add(i);
                  }
                }
                myPathMergedMap.put(head, revisions);
                if (result) {
                  return MergeCheckResult.getInstance(result);
                }
              }
            }
          }
        }
        if (! propertyFound) {
          myPathMergedMap.put(head, Collections.<Long>emptySet());
        }
      }
      catch (SVNException e) {
        LOG.info(e);
        if (SVNErrorCode.ENTRY_NOT_FOUND.equals(e.getErrorMessage().getErrorCode())) {
          myNonExistingPaths.add(head);
          return MergeCheckResult.NOT_EXISTS;
        }
        return MergeCheckResult.NOT_MERGED;
      }

      return goDownLocally(number, head, tail, trunkRelativeUrl);
    }

    private MergeCheckResult goDownLocally(final long number, final String head, final String tail, final String trunkRelativeUrl) {
      final String fixedTail = (tail.startsWith("/")) ? tail.substring(1) : tail;
      final String newTail = SVNPathUtil.removeHead(fixedTail);
      if (newTail.length() == 0) {
        return MergeCheckResult.NOT_MERGED;
      }
      final String headOfTail = SVNPathUtil.head(fixedTail);
      if (headOfTail.length() == 0) {
        return MergeCheckResult.NOT_MERGED;
      }
      final String newHead = SVNPathUtil.append(head, headOfTail);

      LOG.debug("goDown: newHead: " + newHead + " oldHead: " + head);
      return checkOnePathLocally(number, newHead, newTail, trunkRelativeUrl);
    }

    /*private MergeCheckResult checkOnePath(final long number, final String head, final String tail, final String branchPath) {
      if ((myNonExistingPaths.contains(head)) || (head.length() == 0)) {
        return MergeCheckResult.NOT_EXISTS;
      }
      final Set<Long> mergeInfo = myPathMergedMap.get(head);
      if (mergeInfo != null) {
        if (mergeInfo.contains(number)) {
          return MergeCheckResult.getInstance(mergeInfo.contains(number));
        }
        return goDown(number, head, tail, branchPath);
      }

      LOG.debug("checking " + head + " tail: " + tail);

      // go and get manually
      try {
        final SVNPropertyData mergeinfoProperty =
            myClient.doGetProperty(SVNURL.parseURIEncoded(head), SVNProperty.MERGE_INFO, SVNRevision.HEAD, SVNRevision.HEAD);
        boolean propertyFound = false;
        if (mergeinfoProperty != null) {
          final SVNPropertyValue value = mergeinfoProperty.getValue();
          if (value != null) {
            final Map<String, SVNMergeRangeList> map = SVNMergeInfoUtil.parseMergeInfo(new StringBuffer(value.getString()), null);
            for (String key : map.keySet()) {
              if ((key != null) && (myRelativeTrunk.startsWith(key))) {
                propertyFound = true;
                final Set<Long> revisions = new HashSet<Long>();
                final SVNMergeRangeList rangesList = map.get(key);
                boolean result = false;
                for (SVNMergeRange range : rangesList.getRanges()) {
                  // SVN does not include start revision in range
                  final long startRevision = range.getStartRevision() + 1;
                  final long endRevision = range.getEndRevision();
                  if ((number >= startRevision) && (number <= endRevision)) {
                    result = true;
                  }
                  for (long i = startRevision; i <= endRevision; i++) {
                    revisions.add(i);
                  }
                }
                myPathMergedMap.put(head, revisions);
                if (result) {
                  return MergeCheckResult.getInstance(result);
                }
              }
            }
          }
        }
        if (! propertyFound) {
          myPathMergedMap.put(head, Collections.<Long>emptySet());
        }
      }
      catch (SVNException e) {
        LOG.info(e);
        if (SVNErrorCode.ENTRY_NOT_FOUND.equals(e.getErrorMessage().getErrorCode())) {
          myNonExistingPaths.add(head);
          return MergeCheckResult.NOT_EXISTS;
        }
        return MergeCheckResult.NOT_MERGED;
      }

      return goDown(number, head, tail, branchPath);
    }

    private MergeCheckResult goDown(final long number, final String head, final String tail, final String branchPath) {
      final String fixedTail = (tail.startsWith("/")) ? tail.substring(1) : tail;
      final String newTail = SVNPathUtil.removeHead(fixedTail);
      if (newTail.length() == 0) {
        return MergeCheckResult.NOT_MERGED;
      }
      final String headOfTail = SVNPathUtil.head(fixedTail);
      if (headOfTail.length() == 0) {
        return MergeCheckResult.NOT_MERGED;
      }
      final String newHead = SVNPathUtil.append(head, headOfTail);

      LOG.debug("goDown: newHead: " + newHead + " oldHead: " + head);
      return checkOnePath(number, newHead, newTail, branchPath);
    }*/
  }

  private static class MyCurrentUrlData {
    private Map<String, BranchInfo> myBranchInfo;

    private MyCurrentUrlData() {
      myBranchInfo = new SoftHashMap<String, BranchInfo>();
    }

    public BranchInfo getBranchInfo(final String branchUrl) {
      return myBranchInfo.get(branchUrl);
    }

    public void addBranchInfo(final String branchUrl, final BranchInfo branchInfo) {
      myBranchInfo.put(branchUrl, branchInfo);
    }
  }

  public interface SvnMergeInfoCacheListener {
    void copyRevisionUpdated();
  }
}
