package circlet.utils

import com.intellij.openapi.progress.*
import com.intellij.openapi.project.*
import klogging.*
import runtime.*
import runtime.lifetimes.*

private val log = KLoggers.logger("plugin/ExternalTask.kt")

interface IExternalTask{
    val lifetime : Lifetime

    val cancel : () -> Unit

    val title : String get
    val header : String get
    val description : String get
    val isIndeterminate : Boolean get
    val progress: Double
}

fun externalTask(project : Project, cancelable : Boolean, task : IExternalTask): Task {

    val lock = Object()
    task.lifetime.add {
        Sync.exec(lock) {
            lock.notify()
        }
    }

    return object : Task.Backgroundable(project, task.title, cancelable) {
        override fun run(pi: ProgressIndicator) {
            pi.isIndeterminate = task.isIndeterminate
            try {
                var canceled = false
                while (!task.lifetime.isTerminated) {
                    pi.text = task.header
                    pi.text2 = task.description
                    if (!task.isIndeterminate)
                        pi.fraction = task.progress

                    if (pi.isCanceled && !canceled) {
                        // cancel once
                        task.cancel()
                        canceled = true
                    }

                    Sync.exec(lock) {
                        lock.wait(50)
                    }
                }
            } catch (e: Exception) {
                log.error(e)
            }
        }
    }
}
