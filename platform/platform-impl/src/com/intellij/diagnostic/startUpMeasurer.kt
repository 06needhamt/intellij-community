// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

inline fun Activity.runChild(name: String, task: () -> Unit) {
  val activity = startChild(name)
  task()
  activity.end()
}

inline fun ParallelActivity.run(name: String, task: () -> Unit) {
  val activity = start(name)
  task()
  activity.end()
}

inline fun <T> runActivity(name: String, task: () -> T): T {
  val activity = StartUpMeasurer.start(name)
  val result = task()
  activity.end()
  return result
}