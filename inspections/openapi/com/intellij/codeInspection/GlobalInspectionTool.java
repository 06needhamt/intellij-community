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
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefGraphAnnotator;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefVisitor;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for global inspections. Global inspections work only in batch mode
 * (when the &quot;Analyze / Inspect Code&quot; is invoked) and can access the
 * complete graph of references between classes and methods in the scope selected
 * for the analysis.<p>
 *
 * @author anna
 * @see LocalInspectionTool
 * @since 6.0
 */
public abstract class GlobalInspectionTool extends InspectionProfileEntry {
  /**
   * Returns the annotator which will receive callbacks while the reference graph
   * is being built.
   *
   * @param refManager the reference graph manager instance
   * @return the annotator instance, or null if not required.
   */
  @Nullable
  public RefGraphAnnotator getAnnotator(final RefManager refManager) {
    return null;
  }

  /**
   * Runs the global inspection. If building of the reference graph was requested by one of the
   * global inspection tools, this method is called after the graph has been built and before the
   * external usages are processed. The default implementation of the method passes each node
   * of the graph for processing to {@link #checkElement}.
   *
   * @param scope                        the scope on which the inspection was run.
   * @param manager                      the inspection manager instance for the project on which the inspection was run.
   * @param globalContext                the context for the current global inspection run.
   * @param problemDescriptionsProcessor the collector for problems reported by the inspection
   * @param filterSuppressed
   */
  public void runInspection(final AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor,
                            final boolean filterSuppressed) {
    globalContext.getRefManager().iterate(new RefVisitor() {
      public void visitElement(RefEntity refEntity) {
        if (globalContext.isSuppressed(refEntity, getShortName())) return;
        CommonProblemDescriptor[] descriptors = checkElement(refEntity, scope, manager, globalContext);
        if (descriptors != null) {
          problemDescriptionsProcessor.addProblemElement(refEntity, descriptors);
        }
      }
    });
  }

  /**
   * Processes and reports problems for a single element of the completed reference graph.
   *
   * @param refEntity     the reference graph element to check for problems.
   * @param scope         the scope on which analysis was invoked.
   * @param manager       the inspection manager instance for the project on which the inspection was run.
   * @param globalContext the context for the current global inspection run.
   * @return the problems found for the element, or null if no problems were found.
   */
  @Nullable
  public CommonProblemDescriptor[] checkElement(RefEntity refEntity, AnalysisScope scope, InspectionManager manager, GlobalInspectionContext globalContext) {
    return null;
  }

  /**
   * Checks if this inspection requires building of the reference graph. The reference graph
   * is built if at least one of the global inspection has requested that.
   *
   * @return true if the reference graph is required, false otherwise.
   */
  public boolean isGraphNeeded() {
    return false;
  }

  /**
   * Allows the inspection to process usages of analyzed classes outside the analysis scope.
   * This method is called after the reference graph has been built and after
   * the {@link #runInspection} method has collected the list of problems for the current scope.
   * In order to save time when multiple inspections need to process
   * usages of the same classes and methods, usage searches are not performed directly, but
   * instead are queued for batch processing through
   * {@link GlobalInspectionContext#enqueueClassUsagesProcessor} and similar methods. The method
   * can add new problems to <code>problemDescriptionsProcessor</code> or remove some of the problems
   * collected by {@link #runInspection} by calling {@link ProblemDescriptionsProcessor#ignoreElement}.
   *
   * @param manager                      the inspection manager instance for the project on which the inspection was run.
   * @param globalContext                the context for the current global inspection run.
   * @param problemDescriptionsProcessor the collector for problems reported by the inspection.
   * @return true if a repeated call to this method is required after the queued usage processors
   *         have completed work, false otherwise.
   */
  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemDescriptionsProcessor){
    return false;
  }

}
