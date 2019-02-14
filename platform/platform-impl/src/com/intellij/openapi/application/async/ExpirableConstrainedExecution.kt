// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.async.ConstrainedExecution.ContextConstraint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Runnable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.LazyThreadSafetyMode.PUBLICATION

/**
 * This class adds support for cancelling the task on disposal of [Disposable]s associated using [expireWith] and other builder methods.
 * This also ensures that if the task is a coroutine suspended at some execution point, it's resumed with a [CancellationException] giving
 * the coroutine a chance to clean up any resources it might have acquired before suspending.
 *
 * @author eldar
 */
internal abstract class ExpirableConstrainedExecution<E : ConstrainedExecution<E>>(constraints: Array<ContextConstraint>,
                                                                                   private val expirationSet: Set<Expiration>)
  : BaseConstrainedExecution<E>(constraints) {

  protected abstract fun cloneWith(constraints: Array<ContextConstraint>, expirationSet: Set<Expiration>): E

  override fun cloneWith(constraints: Array<ContextConstraint>): E = cloneWith(constraints, expirationSet)

  override fun withConstraint(constraint: ContextConstraint, parentDisposable: Disposable): E {
    val expirableHandle = DisposableExpiration(parentDisposable)
    return cloneWith(constraints + ExpirableContextConstraint(constraint, expirableHandle),
                     expirationSet + expirableHandle)
  }

  override fun expireWith(parentDisposable: Disposable): E {
    val expirableHandle = DisposableExpiration(parentDisposable)
    @Suppress("UNCHECKED_CAST")
    return if (expirableHandle in expirationSet) this as E else cloneWith(constraints, expirationSet + expirableHandle)
  }

  /** Must schedule the runnable and return immediately. */
  abstract override fun dispatchLaterUnconstrained(runnable: Runnable)

  private val compositeExpiration: Expiration? by lazy(PUBLICATION) {
    Expiration.composeExpiration(expirationSet)
  }

  override fun composeExpiration(): Expiration? = compositeExpiration

  /**
   * Wraps an expirable context constraint so that the [schedule] method guarantees to execute runnables, regardless the [expiration] state.
   *
   * This is used in combination with execution services that might refuse to run a submitted task due to disposal of an associated
   * Disposable. For example, the DumbService used in [com.intellij.openapi.application.AppUIExecutor.inSmartMode] doesn't run any task once
   * the project is closed. The [ExpirableContextConstraint] workarounds that limitation, ensuring that even if the corresponding disposable
   * is expired, the task runs eventually, which in turn is crucial for Kotlin Coroutines to work properly.
   */
  internal inner class ExpirableContextConstraint(private val constraint: ContextConstraint,
                                                  private val expiration: Expiration) : ContextConstraint {
    override val isCorrectContext: Boolean
      get() = expiration.isExpired || constraint.isCorrectContext

    override fun schedule(runnable: Runnable) {
      val runOnce = RunOnce()

      val expirationHandle = expiration.invokeOnExpiration(Runnable {
        runOnce {
          // We expect the coroutine job has already been cancelled through the expirableHandle at this point.
          // TODO[eldar] relying on the order of invocations of CompletionHandlers
          dispatchLaterUnconstrained(runnable)
        }
      })
      if (runOnce.isActive) {
        constraint.schedule(Runnable {
          runOnce {
            expirationHandle.unregisterHandler()
            runnable.run()
          }
        })
      }
    }

    override fun toString(): String = constraint.toString()
  }

  companion object {
    internal class RunOnce : (() -> Unit) -> Unit {
      val isActive get() = hasNotRunYet.get()  // inherently race-prone
      private val hasNotRunYet = AtomicBoolean(true)
      override operator fun invoke(block: () -> Unit) {
        if (hasNotRunYet.compareAndSet(true, false)) block()
      }
    }
  }
}
