package circlet.utils

import com.intellij.notification.*
import com.intellij.openapi.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.*
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import com.intellij.psi.*
import runtime.lifetimes.*

inline fun <reified T: Any> ComponentManager.getComponent() : T =
    this.getComponent(T::class.java) ?: throw Error("Component ${T::class.java} not found in container $this")

inline fun <reified T : Any>component() : T = application.getComponent<T>()
inline fun <reified T : Any>Project.component() : T = this.getComponent<T>()

val DataContext.Editor : Editor?
    get() = CommonDataKeys.EDITOR.getData(this)

val DataContext.PsiFile : PsiFile?
    get() = CommonDataKeys.PSI_FILE.getData(this)

val DataContext.Project : Project?
    get() = CommonDataKeys.PROJECT.getData(this)



fun createApplicationLifetime(): Lifetime {
    val result = Lifetime.create(Lifetime.Eternal)
    application.addApplicationListener(object : ApplicationAdapter() {
        override fun applicationExiting() {
            result.terminate()
        }
    })
    return result.lifetime
}

val application: Application
    get() = ApplicationManager.getApplication()

val applicationEx: ApplicationEx
    get() = ApplicationManagerEx.getApplicationEx()

// Bad inspection Disposable {} != object: Disposable {}
@Suppress("ObjectLiteralToLambda")
fun Disposable.attachLifetime(): Lifetime {
    val defComponent = Lifetime.create(Lifetime.Eternal)
    Disposer.register(this, object: Disposable {
        override fun dispose() {
            defComponent.terminate()
        }

    })
    return defComponent.lifetime
}

interface ILifetimedComponent {
    val componentLifetime: Lifetime
}

class LifetimedComponent(project: Project) : ILifetimedComponent {
    private val lifetime: Lifetime = project.attachLifetime()
    final override val componentLifetime: Lifetime
        get() = lifetime
}

interface ILifetimedApplicationComponent : Disposable {
    val componentLifetime: Lifetime
}

class LifetimedApplicationComponent() : ILifetimedApplicationComponent {
    private val lifetimeDefinition = Lifetime.create(Lifetime.Eternal)

    init {
        Disposer.register(application, this)
    }

    final override fun dispose() {
        lifetimeDefinition.terminate()
    }

    final override val componentLifetime: Lifetime
        get() = lifetimeDefinition.lifetime
}

fun Notification.notify(lifetime: Lifetime, project: Project?) {
    lifetime.add{ this.expire() }
    Notifications.Bus.notify(this, project)
}

