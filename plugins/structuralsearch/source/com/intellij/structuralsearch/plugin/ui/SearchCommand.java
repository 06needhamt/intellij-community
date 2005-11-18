package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.MatchResultSink;
import com.intellij.structuralsearch.MatchingProcess;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.structuralsearch.plugin.ui.actions.DoSearchAction;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.Processor;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Mar 15, 2004
 * Time: 4:49:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class SearchCommand {
  protected UsageViewContext context;
  private MatchingProcess process;
  protected Project project;

  public SearchCommand(Project _project, UsageViewContext _context) {
    project = _project;
    context = _context;
  }

  public void findUsages(final Processor<Usage> processor) {
    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();

    DoSearchAction.execute(
      project,
      new MatchResultSink() {
        int count;

        public void setMatchingProcess(MatchingProcess _process) {
          process = _process;
          findStarted();
        }

            public void processFile(PsiFile element) {
              final VirtualFile virtualFile = element.getVirtualFile();
              if (virtualFile!=null)
                progress.setText( SSRBundle.message("looking.in.progress.message",virtualFile.getPresentableName()) );
            }

            public void matchingFinished() {
              findEnded();
              progress.setText( SSRBundle.message("found.progress.message", count ) );
            }

        public ProgressIndicator getProgressIndicator() {
          return progress;
        }

        public void newMatch(MatchResult result) {
          UsageInfo info;

          if (MatchResult.MULTI_LINE_MATCH.equals(result.getName())) {
            int start = -1;
            int end = -1;
            PsiElement parent = result.getMatchRef().getElement().getParent();

            for (final MatchResult matchResult : ((MatchResultImpl)result).getMatches()) {
              PsiElement el = matchResult.getMatchRef().getElement();
              final int elementStart = el.getTextRange().getStartOffset();

              if (start == -1 || start > elementStart) {
                start = elementStart;
              }
              final int newend = elementStart + el.getTextLength();

              if (newend > end) {
                end = newend;
              }
            }

            final int parentStart = parent.getTextRange().getStartOffset();
            int startOffset = start - parentStart;
            info = new UsageInfo(parent,startOffset,end - parentStart);
          } else {
            PsiElement element = result.getMatch();
            info = new UsageInfo(
              element,
              result.getStart(),
              result.getEnd()==-1?element.getTextLength() : result.getEnd()
            );
          }

          Usage usage = new UsageInfo2UsageAdapter(info);
          processor.process(usage);
          foundUsage(result, usage);
          ++count;
        }
      },
      context.getConfiguration()
    );
  }

  public void stopAsyncSearch() {
    if (process!=null) process.stop();
  }

  protected void findStarted() {
    StructuralSearchPlugin.getInstance(project).setSearchInProgress(true);
  }

  protected void findEnded() {
    StructuralSearchPlugin.getInstance(project).setSearchInProgress(false);
  }

  protected void foundUsage(MatchResult result, Usage usage) {
  }
}
