// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.review.details.diff

import circlet.client.codeView
import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.impl.CacheDiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.LoadingDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.space.vcs.review.details.SpaceReviewChangesVm
import com.intellij.space.vcs.review.details.getChangeFilePathInfo
import com.intellij.space.vcs.review.details.getFileContent
import com.intellij.space.vcs.review.details.getFilePath
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.LifetimeSource
import libraries.coroutines.extra.runBlocking
import runtime.reactive.SequentialLifetimes

internal class SpaceDiffRequestProcessor(project: Project,
                                         spaceDiffFile: SpaceDiffFile,
                                         lifetime: LifetimeSource) : CacheDiffRequestProcessor.Simple(project) {
  val spaceDiffVm = spaceDiffFile.diffVm
  val changesVm = spaceDiffFile.changesVm
  val codeViewService = spaceDiffVm.client.codeView
  val seqLifetimeSource = SequentialLifetimes(lifetime)

  override fun getCurrentRequestProvider(): DiffRequestProducer {

    return object : DiffRequestProducer {
      val coroutineContext = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()

      override fun getName(): String = "DiffRequestProducer"

      override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
        val nextLifetime = seqLifetimeSource.next()
        val change = changesVm.selectedChange.value ?: return LoadingDiffRequest("")
        val projectKey = spaceDiffVm.projectKey
        val selectedCommitHashes = spaceDiffVm.selectedCommits.value
          .filter { it.commitWithGraph.repositoryName == change.repository }
          .map { it.commitWithGraph.commit.id }
        if (selectedCommitHashes.isNotEmpty()) {
          return runBlocking(nextLifetime, coroutineContext) {
            val sideBySideDiff = codeViewService.getSideBySideDiff(projectKey,
                                                                   change.repository,
                                                                   change.change, false, selectedCommitHashes)

            val leftFileText = getFileContent(sideBySideDiff.left)
            val rightFileText = getFileContent(sideBySideDiff.right)

            val (oldFilePath, newFilePath) = getChangeFilePathInfo(change)
            val diffContentFactory = DiffContentFactoryImpl.getInstanceEx()
            val titles = listOf(change.change.old?.commit, change.change.new?.commit)
            val documents = listOf(
              oldFilePath?.let { diffContentFactory.create(project, leftFileText, it) } ?: diffContentFactory.createEmpty(),
              newFilePath?.let { diffContentFactory.create(project, rightFileText, it) } ?: diffContentFactory.createEmpty()
            )

            val diffRequestData = DiffRequestData(nextLifetime, spaceDiffVm, changesVm)

            return@runBlocking SimpleDiffRequest(getFilePath(change).toString(), documents, titles).apply {
              putUserData(SpaceDiffKeys.DIFF_REQUEST_DATA, diffRequestData)
            }
          }
        }
        else return LoadingDiffRequest("")
      }
    }
  }
}

data class DiffRequestData(val lifetime: Lifetime,
                           val spaceDiffVm: SpaceDiffVm,
                           val changesVm: SpaceReviewChangesVm)