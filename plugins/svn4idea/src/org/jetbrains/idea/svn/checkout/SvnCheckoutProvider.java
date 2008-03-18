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
package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Ref;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.CheckoutDialog;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;

public class SvnCheckoutProvider implements CheckoutProvider {

  public void doCheckout(Listener listener) {
    final Project project = ProjectManager.getInstance().getDefaultProject();
    CheckoutDialog dialog = new CheckoutDialog(project, listener);
    dialog.show();
  }

  public static void doCheckout(final Project project, final File target, final String url, final SVNRevision revision,
                                final boolean recursive, final boolean ignoreExternals, @Nullable final Listener listener) {
    final Ref<Boolean> checkoutSuccessful = new Ref<Boolean>();
    try {
      final SVNException[] exception = new SVNException[1];
      final SVNUpdateClient client = SvnVcs.getInstance(project).createUpdateClient();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          client.setEventHandler(new CheckoutEventHandler(SvnVcs.getInstance(project), false, progressIndicator));
          client.setIgnoreExternals(ignoreExternals);
          try {
            progressIndicator.setText(SvnBundle.message("progress.text.checking.out", target.getAbsolutePath()));
            client.doCheckout(SVNURL.parseURIEncoded(url), target, SVNRevision.UNDEFINED, revision, recursive);
            progressIndicator.checkCanceled();
            checkoutSuccessful.set(Boolean.TRUE);
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          finally {
            client.setIgnoreExternals(false);
            client.setEventHandler(null);
          }
        }
      }, SvnBundle.message("message.title.check.out"), true, project);
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    catch(SVNCancelException ignore) {
    }
    catch (SVNException e1) {
      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.checkout", e1.getMessage()), SvnBundle.message("message.title.check.out"));
    } finally {
      @NonNls String fileURL = "file://" + target.getAbsolutePath().replace(File.separatorChar, '/');
      VirtualFile vf = VirtualFileManager.getInstance().findFileByUrl(fileURL);
      if (vf != null) {
        vf.refresh(true, true, new Runnable() {
          public void run() {
            if (listener != null) {
              if (!checkoutSuccessful.isNull()) {
                listener.directoryCheckedOut(target);
              }
              listener.checkoutCompleted();
            }
          }
        });
      }
      else if (listener != null) {
        if (!checkoutSuccessful.isNull()) {
          listener.directoryCheckedOut(target);
        }
        listener.checkoutCompleted();
      }
    }
  }

  public static void doExport(final Project project, final File target, final String url, final boolean recursive,
                              final boolean ignoreExternals, final boolean force, final String eolStyle) {
    try {
      final SVNException[] exception = new SVNException[1];
      final SVNUpdateClient client = SvnVcs.getInstance(project).createUpdateClient();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          client.setEventHandler(new CheckoutEventHandler(SvnVcs.getInstance(project), true, progressIndicator));
          client.setIgnoreExternals(ignoreExternals);
          try {
            progressIndicator.setText(SvnBundle.message("progress.text.export", target.getAbsolutePath()));
            client.doExport(SVNURL.parseURIEncoded(url), target, SVNRevision.UNDEFINED, SVNRevision.HEAD, eolStyle, force, recursive);
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          finally {
            client.setIgnoreExternals(false);
            client.setEventHandler(null);
          }
        }
      }, SvnBundle.message("message.title.export"), true, project);
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    catch (SVNException e1) {
      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.export", e1.getMessage()), SvnBundle.message("message.title.export"));
    }
  }

  public static void doImport(final Project project, final File target, final SVNURL url, final boolean recursive,
                              final boolean includeIgnored, final String message) {
    try {
      final SVNException[] exception = new SVNException[1];
      final SVNCommitClient client = SvnVcs.getInstance(project).createCommitClient();

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
          client.setEventHandler(new CheckoutEventHandler(SvnVcs.getInstance(project), true, progressIndicator));
          try {
            progressIndicator.setText(SvnBundle.message("progress.text.import", target.getAbsolutePath()));
            client.doImport(target, url, message, !includeIgnored, recursive);
          }
          catch (SVNException e) {
            exception[0] = e;
          }
          finally {
            client.setIgnoreExternals(false);
            client.setEventHandler(null);
          }
        }
      }, SvnBundle.message("message.title.import"), true, project);
      if (exception[0] != null) {
        throw exception[0];
      }
    }
    catch (SVNException e1) {
      Messages.showErrorDialog(SvnBundle.message("message.text.cannot.import", e1.getMessage()), SvnBundle.message("message.title.import"));
    }
  }

  public String getVcsName() {
    return "_Subversion";
  }

}


