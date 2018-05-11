package circlet.utils

import com.intellij.notification.*
import com.intellij.openapi.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import runtime.reactive.*

inline fun <reified T : Any> Project.component(): T = getComponent()

inline fun <reified T : Any> ComponentManager.getComponent(): T =
    computeSafe { getComponent(T::class.java) } ?: throw Error("Component ${T::class.java} not found in container $this")

inline fun <T : Any, C : ComponentManager> C.computeSafe(crossinline compute: C.() -> T?) : T? =
    application.runReadAction(Computable {
        if (isDisposed) null else compute()
    })

inline fun <reified T : Any> Project.getService(): T = computeSafe { service<T>() }.checkService(this)

inline fun <reified T : Any> T?.checkService(container: Any): T =
    this ?: throw Error("Service ${T::class.java} not found in container $container")

val application: Application
    get() = ApplicationManager.getApplication()

fun Disposable.attachLifetime(): Lifetime {
    val lifetime = Lifetime()

    Disposer.register(this, Disposable { lifetime.terminate() })

    return lifetime
}

class LifetimedOnDisposable(disposable: Disposable) : Lifetimed {
    override val lifetime: Lifetime = disposable.attachLifetime()
}

interface LifetimedDisposable : Lifetimed, Disposable

class SimpleLifetimedDisposable : LifetimedDisposable {
    override val lifetime: Lifetime = Lifetime()

    override fun dispose() {
        lifetime.terminate()
    }
}

fun Notification.notify(lifetime: Lifetime, project: Project?) {
    lifetime.add { expire() }
    Notifications.Bus.notify(this, project)
}
